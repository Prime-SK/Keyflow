const assert = require('node:assert/strict');
const fs = require('node:fs');
const Module = require('node:module');
const path = require('node:path');
const test = require('node:test');
const ts = require('typescript');

// Compile the pure helper module without loading React Native or requiring an Android bridge.
const filename = path.resolve(__dirname, '../lib/playback.ts');
const compiled = ts.transpileModule(fs.readFileSync(filename, 'utf8'), {
  compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
}).outputText;
const helpers = new Module(filename, module);
helpers._compile(compiled, filename);
const { acceptSnapshot, formatTime, errorMessage } = helpers.exports;

test('a delayed command response cannot overwrite newer native playback state', () => {
  const playing = { revision: 12, status: 'playing', positionMs: 15000 };
  const stale = { revision: 11, status: 'paused', positionMs: 5000 };
  assert.equal(acceptSnapshot(playing, stale), playing);
  const fresh = { revision: 13, status: 'paused', positionMs: 16000 };
  assert.equal(acceptSnapshot(playing, fresh), fresh);
});

test('time labels support long songs and unknown/invalid durations', () => {
  assert.equal(formatTime(0), '0:00');
  assert.equal(formatTime(125999), '2:05');
  assert.equal(formatTime(3660000), '61:00');
  for (const value of [-1, NaN, Infinity]) assert.equal(formatTime(value), '0:00');
});

test('command failures expose a usable message rather than crashing the interface', () => {
  assert.equal(errorMessage(new Error('Access to this file was lost.')), 'Access to this file was lost.');
  assert.equal(errorMessage(null), 'Something went wrong. Please try again.');
});
