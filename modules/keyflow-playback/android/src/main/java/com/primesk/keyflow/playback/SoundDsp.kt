package com.primesk.keyflow.playback

import kotlin.math.*

/** RBJ peaking filters (Q=1.4) and a low shelf (S=1), using double precision. */
internal data class Biquad(val b0: Double = 1.0, val b1: Double = 0.0, val b2: Double = 0.0,
  val a1: Double = 0.0, val a2: Double = 0.0) {
  fun magnitude(w: Double): Double {
    fun norm(x: Double, y: Double, z: Double) = hypot(x + y * cos(w) + z * cos(2*w), -y * sin(w) - z * sin(2*w))
    return norm(b0,b1,b2) / norm(1.0,a1,a2)
  }
  companion object {
    fun make(hz: Double, db: Double, rate: Int, shelf: Boolean = false): Biquad {
      // Frequencies beyond Nyquist are bypassed, never folded into audible bands.
      if (db == 0.0 || hz >= rate * .49) return Biquad()
      val a = 10.0.pow(db / 40); val w = 2*PI*hz/rate; val c = cos(w)
      val alpha = sin(w) / (if (shelf) sqrt(2.0) else 2*1.4)
      val beta = 2*sqrt(a)*alpha
      val n: DoubleArray; val d: DoubleArray
      if (shelf) {
        n = doubleArrayOf(a*((a+1)-(a-1)*c+beta), 2*a*((a-1)-(a+1)*c), a*((a+1)-(a-1)*c-beta))
        d = doubleArrayOf((a+1)+(a-1)*c+beta, -2*((a-1)+(a+1)*c), (a+1)+(a-1)*c-beta)
      } else {
        n = doubleArrayOf(1+alpha*a, -2*c, 1-alpha*a)
        d = doubleArrayOf(1+alpha/a, -2*c, 1-alpha/a)
      }
      return Biquad(n[0]/d[0], n[1]/d[0], n[2]/d[0], d[1]/d[0], d[2]/d[0])
    }
  }
}

internal object SoundDsp {
  private val cache = object : LinkedHashMap<Pair<SoundSettings,Int>,Double>(64,.75f,true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Pair<SoundSettings,Int>,Double>?) = size > 64
  }
  fun filters(s: SoundSettings, rate: Int) = SoundSettings.frequencies.mapIndexed { i, hz -> Biquad.make(hz, s.bands[i], rate) } + Biquad.make(100.0, s.bass, rate, true)
  fun reduction(s: SoundSettings, rate: Int = 48000): Double {
    if (!s.enabled) return 0.0
    val key = s to rate
    synchronized(cache) { cache[key]?.let { return it } }
    val filters = filters(s, rate)
    var peak = 0.0
    // Dense logarithmic response grid, including DC and each band's exact center.
    val points = (0..1024).map { 10.0 * (rate*.499/10).pow(it/1024.0) } + SoundSettings.frequencies.toList() + listOf(0.0, 100.0)
    for (hz in points) if (hz < rate/2.0) {
      val response = filters.fold(1.0) { value, f -> value*f.magnitude(2*PI*hz/rate) }
      peak = max(peak, 20*log10(response.coerceAtLeast(1e-12)))
    }
    val boost = max(0.0, peak + s.preamp)
    val reduction = if (boost > .001) boost + 1.0 else 0.0
    synchronized(cache) { cache[key] = reduction }
    return reduction
  }
}

internal class SoundEngine(val settings: SoundSettings, rate: Int, channels: Int) {
  private val filters = if (settings.enabled) SoundDsp.filters(settings, rate).filter { it != Biquad() } else emptyList()
  private val z1 = Array(channels) { DoubleArray(filters.size) }
  private val z2 = Array(channels) { DoubleArray(filters.size) }
  // One shared reserve for both decks, so the displayed gain reduction is exact.
  private val gain = if (settings.enabled) 10.0.pow((settings.preamp - SoundDsp.reduction(settings))/20) else 1.0
  fun sample(input: Double, channel: Int): Double {
    var value = input
    filters.forEachIndexed { i, f ->
      val out = f.b0*value + z1[channel][i]
      z1[channel][i] = f.b1*value - f.a1*out + z2[channel][i]
      z2[channel][i] = f.b2*value - f.a2*out
      value = out
    }
    return value * gain
  }
}
