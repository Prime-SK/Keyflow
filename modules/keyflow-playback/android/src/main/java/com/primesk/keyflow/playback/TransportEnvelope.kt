package com.primesk.keyflow.playback

/** A reversible master envelope; starting a new command preserves the current gain. */
internal class TransportEnvelope {
  var gain = 1f; private set
  var target = 1f; private set
  var active = false; private set
  private var start = 1f
  private var elapsed = 0L
  private var duration = 0L
  private var curve = "linear"
  fun begin(playing: Boolean, durationMs: Long, selectedCurve: String) {
    start = gain; target = if (playing) 1f else 0f; elapsed = 0
    duration = (durationMs * kotlin.math.abs(target - start)).toLong().coerceAtLeast(1)
    curve = selectedCurve; active = start != target
  }
  fun step(delta: Long): Boolean {
    if (!active) return false
    elapsed = (elapsed + delta.coerceAtLeast(0)).coerceAtMost(duration)
    val (outgoing, incoming) = TransitionMath.gains(elapsed.toDouble() / duration, curve)
    gain = if (target > start) start + (target - start) * incoming else target + (start - target) * outgoing
    if (elapsed == duration) { gain = target; active = false; return true }
    return false
  }
  fun reset(value: Float = 1f) { gain = value; target = value; active = false }
}
