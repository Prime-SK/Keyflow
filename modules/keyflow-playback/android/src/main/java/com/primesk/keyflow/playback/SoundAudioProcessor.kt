package com.primesk.keyflow.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.AudioProcessor.UnhandledAudioFormatException
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import kotlin.math.roundToInt

/** Remains in the PCM chain even in bypass, so live edits never rebuild a decoder. */
@UnstableApi
internal class SoundAudioProcessor(private val source: () -> SoundSettings) : BaseAudioProcessor() {
  private var current: SoundEngine? = null
  private var incoming: SoundEngine? = null
  private var frame = 0
  private var smoothingFrames = 1
  override fun onConfigure(format: AudioFormat): AudioFormat {
    if (format.encoding != C.ENCODING_PCM_16BIT) throw UnhandledAudioFormatException(format)
    return format
  }
  override fun onFlush() {
    current = SoundEngine(source(), inputAudioFormat.sampleRate, inputAudioFormat.channelCount)
    incoming = null; frame = 0; smoothingFrames = (inputAudioFormat.sampleRate / 50).coerceAtLeast(1)
  }
  override fun onReset() { current = null; incoming = null; frame = 0 }
  override fun queueInput(buffer: ByteBuffer) {
    val output = replaceOutputBuffer(buffer.remaining())
    val channels = inputAudioFormat.channelCount
    val wanted = source()
    if (incoming == null && current?.settings != wanted) {
      incoming = SoundEngine(wanted, inputAudioFormat.sampleRate, channels); frame = 0
    }
    val old = current ?: return
    while (buffer.remaining() >= channels * 2) {
      val next = incoming
      val mix = if (next == null) 0.0 else (frame.toDouble() / smoothingFrames).coerceAtMost(1.0)
      for (ch in 0 until channels) {
        val raw = ((buffer.get().toInt() and 255) or (buffer.get().toInt() shl 8)).toShort().toInt()
        val before = old.sample(raw.toDouble(), ch)
        val value = if (next == null) before else before*(1-mix) + next.sample(raw.toDouble(), ch)*mix
        output.putShort(value.roundToInt().coerceIn(-32768,32767).toShort())
      }
      if (next != null) frame++
    }
    // Promote only after the buffer ends; intermediate edits coalesce into the next ramp.
    if (incoming != null && frame >= smoothingFrames) { current = incoming; incoming = null }
    output.flip()
  }
}
