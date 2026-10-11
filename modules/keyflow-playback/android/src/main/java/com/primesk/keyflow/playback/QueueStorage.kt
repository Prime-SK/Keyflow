package com.primesk.keyflow.playback

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import com.primesk.keyflow.library.LibraryDatabase
import com.primesk.keyflow.library.LibraryQueries
import com.primesk.keyflow.library.LibraryRepository
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

@UnstableApi
internal object QueueStorage {
  /** Keep each SQLite row bounded, without splitting an emoji's UTF-16 pair. */
  fun recoveryChunks(value: String): List<com.primesk.keyflow.library.PlaybackRecovery> {
    val rows = mutableListOf<com.primesk.keyflow.library.PlaybackRecovery>()
    var start = 0
    while (start < value.length) {
      var end = (start + 128 * 1024).coerceAtMost(value.length)
      if (end < value.length && Character.isHighSurrogate(value[end - 1])) end--
      rows += com.primesk.keyflow.library.PlaybackRecovery(rows.size + 1, value.substring(start, end))
      start = end
    }
    return rows
  }
  fun item(audio: Map<String, Any?>, entryId: String = UUID.randomUUID().toString(), trackId: String? = null): MediaItem {
    val uri = Uri.parse(audio["uri"] as? String ?: error("No local file was supplied."))
    require(uri.scheme in listOf("file", "content"))
    val extras = Bundle().apply {
      putString("fileName", audio["fileName"] as? String); putString("mimeType", audio["mimeType"] as? String)
      putString("trackId", trackId); putBoolean("persistedPermission", audio["persistedPermission"] as? Boolean ?: false)
    }
    return MediaItem.Builder().setMediaId(entryId).setUri(uri)
      .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(uri).build())
      .setMediaMetadata(MediaMetadata.Builder().setTitle(audio["title"] as? String ?: "Local audio")
        .setArtist(audio["artist"] as? String).setArtworkUri((audio["artworkUri"] as? String)?.let(Uri::parse))
        .setExtras(extras).build()).build()
  }
  fun libraryQueue(context: Context, query: Map<String, Any?>, selected: String): Pair<List<MediaItem>, Int> {
    val db = LibraryDatabase.get(context); val repository = LibraryRepository(context, db)
    // Validate the selected song before replacing currently playing audio.
    val selectedAudio = repository.resolve(selected)
    val result = mutableListOf<MediaItem>(); var selectedIndex = -1
    db.runInTransaction {
      var offset = 0
      while (true) {
        val tracks = db.dao().queueTracks(LibraryQueries.tracks(query + mapOf("offset" to offset, "limit" to 100), queue = true))
        if (tracks.isEmpty()) break
        tracks.forEach { row ->
          val track = row.track
          if (row.queueUri != null) {
            if (track.id == selected) selectedIndex = result.size
            val audio = if (track.id == selected) selectedAudio else mapOf("uri" to row.queueUri,
              "title" to track.title, "artist" to track.artist, "fileName" to track.fileName,
              "mimeType" to track.mimeType, "artworkUri" to track.artworkUri,
              "persistedPermission" to row.persistedPermission)
            result += item(audio, trackId = track.id)
          }
        }
        offset += tracks.size
        if (tracks.size < 100) break
      }
    }
    check(selectedIndex >= 0) { "The song list changed. Refresh and try again." }
    return result to selectedIndex
  }
  fun audio(item: MediaItem): Map<String, Any?> = mapOf("uri" to (item.localConfiguration?.uri ?: item.requestMetadata.mediaUri).toString(),
    "title" to item.mediaMetadata.title?.toString(), "artist" to item.mediaMetadata.artist?.toString(),
    "fileName" to item.mediaMetadata.extras?.getString("fileName"), "mimeType" to item.mediaMetadata.extras?.getString("mimeType"),
    "artworkUri" to item.mediaMetadata.artworkUri?.toString(),
    "persistedPermission" to (item.mediaMetadata.extras?.getBoolean("persistedPermission") ?: false))
  fun collectionQueue(context: Context, kind: String, id: String?, selected: String): Pair<List<MediaItem>, Int> {
    val repository = com.primesk.keyflow.library.CollectionsRepository(context)
    val result = mutableListOf<MediaItem>(); var selectedIndex = -1
    repository.db.runInTransaction {
      var offset = 0
      while (true) {
        val page = repository.entries(kind, id, mapOf("offset" to offset, "limit" to 100))
        @Suppress("UNCHECKED_CAST") val rows = page["items"] as List<Map<String, Any?>>
        rows.forEach { row ->
          val trackId = row["trackId"] as? String
          @Suppress("UNCHECKED_CAST") var audio = row["audio"] as Map<String, Any?>
          if (row["id"] == selected) {
            selectedIndex = result.size
            if (trackId != null) audio = LibraryRepository(context, repository.db).resolve(trackId)
            else check(context.contentResolver.openAssetFileDescriptor(Uri.parse(audio["uri"] as String), "r")?.use { true } == true) { "This song is unavailable. Choose it again to restore access." }
          } else if (trackId != null) {
            audio = LibraryRepository(context, repository.db).queueInput(trackId) ?: audio
          }
          result += item(audio, trackId = trackId)
        }
        if (page["hasMore"] != true) break
        offset += rows.size
      }
    }
    check(selectedIndex >= 0) { "The saved list changed. Refresh and try again." }
    return result to selectedIndex
  }
  fun encode(items: List<MediaItem>, order: QueueOrder, current: String?, position: Long, repeat: Int, ended: Boolean = false): String = JSONObject().apply {
    put("version", 1); put("items", JSONArray(items.map { item -> JSONObject(audio(item)).apply {
      put("entryId", item.mediaId); put("trackId", item.mediaMetadata.extras?.getString("trackId"))
    } })); put("base", JSONArray(order.base)); put("displayed", JSONArray(order.displayed))
    put("shuffle", order.shuffled); put("priority", JSONArray(order.priority)); put("current", current); put("position", position); put("repeat", repeat); put("ended", ended)
  }.toString()
  data class Recovery(val items: List<MediaItem>, val base: List<String>, val displayed: List<String>, val shuffle: Boolean,
    val current: String?, val position: Long, val repeat: Int, val ended: Boolean, val priority: List<String>)
  fun decode(value: String): Recovery {
    val json = JSONObject(value); require(json.getInt("version") == 1)
    fun strings(name: String): List<String> = json.getJSONArray(name).let { a -> (0 until a.length()).map(a::getString) }
    val array = json.getJSONArray("items")
    val items = (0 until array.length()).map { index -> val row = array.getJSONObject(index)
      item(row.keys().asSequence().associateWith { key -> row.opt(key).takeUnless { it == JSONObject.NULL } },
        row.getString("entryId"), row.optString("trackId").takeIf { it.isNotEmpty() && it != "null" }) }
    return Recovery(items, strings("base"), strings("displayed"), json.getBoolean("shuffle"),
      json.optString("current"), json.getLong("position").coerceAtLeast(0), json.getInt("repeat").coerceIn(0, 2), json.optBoolean("ended", false),
      json.optJSONArray("priority")?.let { a -> (0 until a.length()).map(a::getString) } ?: emptyList())
  }
}
