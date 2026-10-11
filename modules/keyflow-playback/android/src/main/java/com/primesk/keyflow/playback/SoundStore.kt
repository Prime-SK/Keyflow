package com.primesk.keyflow.playback

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

internal data class SoundPreset(val id: String, val name: String, val sound: SoundSettings, val builtIn: Boolean = false) {
  fun map() = mapOf("id" to id, "name" to name, "builtIn" to builtIn, "values" to sound.values())
}

/** Current edits and explicit saved presets have separate, versioned records. */
internal class SoundStore(context: Context) {
  private val prefs = context.getSharedPreferences("keyflow-sound", Context.MODE_PRIVATE)
  @Volatile var current = readSound(prefs.getString("current", null)); private set
  private val custom = mutableListOf<SoundPreset>()
  init {
    try {
      val data = JSONArray(prefs.getString("presets", "[]"))
      for (i in 0 until data.length()) {
        val p = data.getJSONObject(i)
        custom += SoundPreset(p.getString("id"), p.getString("name"), readSound(p.getJSONObject("values").toString()))
      }
    } catch (_: Exception) { /* Preserve the stored record; never erase it on a read failure. */ }
  }
  fun state(overlapDb: Double) = mapOf("settings" to current.map(), "presets" to (builtIns + custom).map { it.map() },
    "headroomDb" to SoundDsp.reduction(current), "overlapHeadroomDb" to overlapDb)
  fun update(values: Map<String, Any?>) { current = current.updated(values); prefs.edit().putInt("version",1).putString("current", JSONObject(current.map()).toString()).apply() }
  fun select(id: String) { val preset = (builtIns + custom).firstOrNull { it.id == id } ?: error("Preset no longer exists."); update(preset.sound.values()) }
  fun save(name: String): String {
    val id = UUID.randomUUID().toString(); custom += SoundPreset(id, validName(name), current.copy(enabled = false)); persist(); return id
  }
  fun edit(id: String, action: String, name: String?) {
    val index = custom.indexOfFirst { it.id == id }; require(index >= 0) { "Built-in presets cannot be edited." }
    when (action) {
      "update" -> custom[index] = custom[index].copy(sound = current.copy(enabled = false))
      "rename" -> custom[index] = custom[index].copy(name = validName(name ?: ""))
      "delete" -> custom.removeAt(index)
      else -> error("Unknown preset action.")
    }
    persist()
  }
  private fun persist() {
    prefs.edit().putInt("version",1).putString("presets", JSONArray(custom.map { JSONObject(mapOf("id" to it.id, "name" to it.name, "values" to it.sound.values())) }).toString()).apply()
  }
  private fun validName(name: String) = name.trim().also { require(it.isNotEmpty() && it.length <= 80) { "Use a name between 1 and 80 characters." } }
  companion object {
    val builtIns = listOf(
      SoundPreset("flat", "Flat", SoundSettings(), true),
      SoundPreset("bass", "Bass boost", SoundSettings(bands = listOf(4.0,3.0,1.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0), bass = 2.0), true),
      SoundPreset("vocal", "Vocal", SoundSettings(bands = listOf(0.0,0.0,-2.0,-1.0,1.0,2.0,3.0,1.0,0.0,0.0)), true),
      SoundPreset("treble", "Treble boost", SoundSettings(bands = listOf(0.0,0.0,0.0,0.0,0.0,0.0,0.0,2.0,3.0,4.0)), true))
    private fun readSound(raw: String?): SoundSettings = try {
      val json = JSONObject(raw ?: "{}")
      val values = mutableMapOf<String,Any?>("enabled" to json.optBoolean("enabled", false), "bass" to json.optDouble("bass",0.0), "preamp" to json.optDouble("preamp",0.0))
      json.optJSONArray("bands")?.let { bands -> values["bands"] = (0 until bands.length()).map { bands.getDouble(it) } }
      SoundSettings().updated(values)
    } catch (_: Exception) { SoundSettings() }
  }
}
