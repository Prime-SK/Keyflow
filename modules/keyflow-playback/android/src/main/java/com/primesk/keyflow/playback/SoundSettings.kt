package com.primesk.keyflow.playback

internal data class SoundSettings(val enabled: Boolean = false, val bands: List<Double> = List(10) { 0.0 },
  val bass: Double = 0.0, val preamp: Double = 0.0) {
  fun values() = mapOf("bands" to bands, "bass" to bass, "preamp" to preamp)
  fun map() = values() + ("enabled" to enabled)
  fun updated(values: Map<String, Any?>): SoundSettings {
    fun gain(key: String, fallback: Double, min: Double) = (values[key]?.let {
      require(it is Number) { "$key must be a number." }; it.toDouble()
    } ?: fallback).also { require(it.isFinite() && it in min..12.0) { "$key is outside its allowed range." } }
    val next = values["bands"]?.let { raw ->
      require(raw is List<*> && raw.size == 10) { "Choose ten equalizer gains." }
      raw.map { require(it is Number); it.toDouble().also { n -> require(n.isFinite() && n in -12.0..12.0) } }
    } ?: bands
    val on = values["enabled"]?.let { require(it is Boolean); it } ?: enabled
    return SoundSettings(on, next, gain("bass", bass, -12.0), gain("preamp", preamp, -24.0))
  }
  companion object { val frequencies = doubleArrayOf(31.0, 62.0, 125.0, 250.0, 500.0, 1000.0, 2000.0, 4000.0, 8000.0, 16000.0) }
}
