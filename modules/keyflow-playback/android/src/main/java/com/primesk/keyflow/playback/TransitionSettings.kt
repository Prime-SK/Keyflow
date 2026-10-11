package com.primesk.keyflow.playback

import android.content.Context
import kotlin.math.cos
import kotlin.math.sin

internal data class TransitionSettings(
  val automatic: Boolean = false, val automaticMs: Long = 5000,
  val manual: Boolean = false, val manualMs: Long = 300,
  val automaticCurve: String = "equal-power", val manualCurve: String = "linear",
  val gapless: Boolean = false,
) {
  fun map(): Map<String, Any> = mapOf("automatic" to automatic, "automaticMs" to automaticMs.toDouble(),
    "manual" to manual, "manualMs" to manualMs.toDouble(), "automaticCurve" to automaticCurve,
    "manualCurve" to manualCurve, "gapless" to gapless)
  fun updated(values: Map<String, Any?>): TransitionSettings {
    require(values.keys.all { it in map().keys }) { "Unknown transition setting." }
    fun flag(key: String, fallback: Boolean) = values[key]?.let { require(it is Boolean); it } as Boolean? ?: fallback
    fun duration(key: String, fallback: Long, maximum: Long) = values[key]?.let {
      require(it is Number && it.toDouble().isFinite() && it.toDouble() in 0.0..maximum.toDouble()) { "Invalid fade duration." }; it.toLong()
    } ?: fallback
    fun curve(key: String, fallback: String) = values[key]?.let { require(it in listOf("linear", "equal-power")); it as String } ?: fallback
    val auto = flag("automatic", automatic); val gap = flag("gapless", gapless)
    return TransitionSettings(auto && !(values["gapless"] == true || (gap && values["automatic"] != true)),
      duration("automaticMs", automaticMs, 12000), flag("manual", manual), duration("manualMs", manualMs, 2000),
      curve("automaticCurve", automaticCurve), curve("manualCurve", manualCurve), gap && values["automatic"] != true)
  }
  fun save(context: Context) {
    context.getSharedPreferences("keyflow-transitions", 0).edit().putInt("version", 1)
      .putBoolean("automatic", automatic).putLong("automaticMs", automaticMs).putBoolean("manual", manual)
      .putLong("manualMs", manualMs).putString("automaticCurve", automaticCurve).putString("manualCurve", manualCurve)
      .putBoolean("gapless", gapless).apply()
  }
  companion object {
    fun load(context: Context): TransitionSettings {
      val prefs = context.getSharedPreferences("keyflow-transitions", 0)
      return try { TransitionSettings().updated(prefs.all.filterKeys { it != "version" }) } catch (_: Exception) { TransitionSettings() }
    }
  }
}

internal object TransitionMath {
  fun overlap(requested: Long, outgoing: Long, incoming: Long): Long =
    if (outgoing <= 0 || incoming <= 0) 0 else minOf(requested.coerceAtLeast(0), outgoing / 2, incoming / 2)
  fun gains(progress: Double, curve: String): Pair<Float, Float> {
    val p = progress.coerceIn(0.0, 1.0)
    return if (curve == "equal-power") cos(p * Math.PI / 2).toFloat() to sin(p * Math.PI / 2).toFloat()
      else (1 - p).toFloat() to p.toFloat()
  }
}
