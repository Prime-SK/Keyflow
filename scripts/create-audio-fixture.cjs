const fs = require('node:fs');
const path = require('node:path');

// Controlled device-test audio: quiet A4 tone with a short click at 120 BPM.
// This is a fixture, never a substitute for a user's library or listening tests.
const sampleRate = 44100;
const seconds = 120;
const samples = sampleRate * seconds;
const data = Buffer.alloc(samples * 2);
for (let i = 0; i < samples; i++) {
  const t = i / sampleRate;
  const beatTime = t % 0.5;
  const edgeFade = Math.min(1, t / 0.02, (seconds - t) / 0.02);
  const tone = 0.06 * Math.sin(2 * Math.PI * 440 * t);
  const click = beatTime < 0.025
    ? 0.12 * Math.exp(-beatTime * 180) * Math.sin(2 * Math.PI * 1800 * t) : 0;
  data.writeInt16LE(Math.round((tone + click) * edgeFade * 32767), i * 2);
}
const header = Buffer.alloc(44);
header.write('RIFF', 0);
header.writeUInt32LE(data.length + 36, 4);
header.write('WAVEfmt ', 8);
header.writeUInt32LE(16, 16);
header.writeUInt16LE(1, 20);
header.writeUInt16LE(1, 22);
header.writeUInt32LE(sampleRate, 24);
header.writeUInt32LE(sampleRate * 2, 28);
header.writeUInt16LE(2, 32);
header.writeUInt16LE(16, 34);
header.write('data', 36);
header.writeUInt32LE(data.length, 40);
const outputDirectory = path.join(__dirname, '..', 'artifacts');
fs.mkdirSync(outputDirectory, { recursive: true });
const output = path.join(outputDirectory, 'Keyflow-test-440Hz-120BPM.wav');
fs.writeFileSync(output, Buffer.concat([header, data]));
console.log(output);
