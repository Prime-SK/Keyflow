package com.primesk.keyflow.playback

import kotlin.math.pow

/** Musical inputs stay separate from playback rate; changing one never changes the other. */
internal object PlaybackMath {
  fun pitchFactor(semitones: Double, cents: Double): Float {
    require(semitones.isFinite() && semitones in -12.0..12.0) { "Pitch must be between -12 and 12 semitones." }
    require(cents.isFinite() && cents in -100.0..100.0) { "Fine tuning must be between -100 and 100 cents." }
    return 2.0.pow((semitones + cents / 100.0) / 12.0).toFloat()
  }

  fun speedFactor(speed: Double): Float {
    require(speed.isFinite() && speed in 0.5..2.0) { "Tempo must be between 50% and 200%." }
    return speed.toFloat()
  }

  fun seekPosition(positionMs: Double, durationMs: Long): Long {
    require(positionMs.isFinite()) { "Seek position must be finite." }
    val nonNegative = positionMs.toLong().coerceAtLeast(0)
    return if (durationMs > 0) nonNegative.coerceAtMost(durationMs) else nonNegative
  }
}
