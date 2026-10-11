package com.primesk.keyflow.library

import android.content.Context
import android.net.Uri
import org.json.JSONObject

class CollectionsRepository(context: Context, val db: LibraryDatabase = LibraryDatabase.get(context)) {
  val dao = db.collections()
  private val library = LibraryRepository(context, db)
  private fun name(value: String) = value.trim().also { require(it.isNotBlank() && it.length <= 120) { "Enter a playlist name of 1–120 characters." } }
  fun audioJson(audio: Map<String, Any?>): String {
    val uri = audio["uri"] as? String ?: error("No local song supplied.")
    require(Uri.parse(uri).scheme in listOf("file", "content")) { "Choose a local song." }
    val fields = listOf("uri", "title", "artist", "fileName", "mimeType", "durationMs", "artworkUri", "persistedPermission")
    return JSONObject(fields.associateWith { audio[it] }).toString()
  }
  fun audioMap(json: String): Map<String, Any?> = JSONObject(json).let { value ->
    value.keys().asSequence().associateWith { key -> value.opt(key).takeUnless { it == JSONObject.NULL } }
  }
  fun key(audio: Map<String, Any?>, trackId: String?) = trackId?.let { "track:$it" } ?: "uri:${audio["uri"]}"
  fun create(value: String): Playlist = Playlist(name = name(value)).also(dao::putPlaylist)
  fun rename(id: String, value: String) {
    val existing = dao.playlist(id) ?: error("This playlist no longer exists.")
    dao.putPlaylist(existing.copy(name = name(value), updatedAt = System.currentTimeMillis()))
  }
  fun add(id: String, audio: Map<String, Any?>, trackId: String?): PlaylistEntry {
    val json = audioJson(audio); lateinit var entry: PlaylistEntry
    db.runInTransaction {
      val playlist = dao.playlist(id) ?: error("This playlist no longer exists.")
      entry = PlaylistEntry(playlistId = id, position = dao.entryCount(id), trackId = trackId, audio = json)
      dao.putEntries(listOf(entry)); dao.putPlaylist(playlist.copy(updatedAt = System.currentTimeMillis()))
    }
    return entry
  }
  fun saveQueue(value: String, entries: List<Pair<Map<String, Any?>, String?>>): Playlist {
    require(entries.isNotEmpty()) { "The queue is empty." }
    val playlist = Playlist(name = name(value))
    val rows = entries.mapIndexed { index, entry -> PlaylistEntry(playlistId = playlist.id, position = index, trackId = entry.second, audio = audioJson(entry.first)) }
    db.runInTransaction { dao.putPlaylist(playlist); rows.chunked(100).forEach(dao::putEntries) }
    return playlist
  }
  fun move(id: String, delta: Int) {
    require(delta == -1 || delta == 1)
    db.runInTransaction {
      val entry = dao.entry(id) ?: error("This playlist entry no longer exists.")
      val adjacent = dao.atPosition(entry.playlistId, entry.position + delta) ?: return@runInTransaction
      dao.updateEntry(entry.copy(position = adjacent.position)); dao.updateEntry(adjacent.copy(position = entry.position))
      touch(entry.playlistId)
    }
  }
  fun remove(id: String) {
    db.runInTransaction {
      val entry = dao.entry(id) ?: error("This playlist entry no longer exists.")
      dao.deleteEntry(id); dao.closeGap(entry.playlistId, entry.position); touch(entry.playlistId)
    }
  }
  private fun touch(id: String) { dao.playlist(id)?.let { dao.putPlaylist(it.copy(updatedAt = System.currentTimeMillis())) } }
  fun favorite(audio: Map<String, Any?>, trackId: String?, enabled: Boolean) {
    val json = audioJson(audio); val key = key(audio, trackId)
    if (enabled) dao.putFavorite(Favorite(key, trackId, json)) else dao.deleteFavorite(key)
  }
  fun record(audio: Map<String, Any?>, trackId: String?) {
    val json = audioJson(audio)
    db.runInTransaction { dao.putRecent(RecentPlay(key(audio, trackId), trackId, json)); dao.pruneRecent() }
  }
  fun playlists(options: Map<String, Any?>): Map<String, Any> {
    val (limit, offset) = LibraryQueries.page(options); val total = dao.playlistCount()
    val rows = dao.playlists(offset, limit).map { mapOf("id" to it.playlist.id, "name" to it.playlist.name, "count" to it.entryCount.toDouble()) }
    return mapOf("items" to rows, "total" to total.toDouble(), "offset" to offset, "hasMore" to (offset + rows.size < total))
  }
  fun entries(kind: String, id: String?, options: Map<String, Any?>): Map<String, Any?> {
    val (limit, offset) = LibraryQueries.page(options)
    val rows: List<Map<String, Any?>>; val total: Long; val title: String
    fun row(key: String, trackId: String?, json: String, index: Int): Map<String, Any?> {
      val audio = audioMap(json).toMutableMap()
      trackId?.let { library.dao.track(it) }?.let { track ->
        audio["title"] = track.title; audio["artist"] = track.artist; audio["fileName"] = track.fileName
        audio["artworkUri"] = track.artworkUri; audio["durationMs"] = track.durationMs.toDouble()
      }
      return mapOf("id" to key, "trackId" to trackId, "index" to index, "audio" to audio,
        "availability" to (trackId?.let(library::availability) ?: "available"))
    }
    when(kind) {
      "playlist" -> {
        val playlist = dao.playlist(id ?: error("Choose a playlist.")) ?: error("This playlist no longer exists.")
        total = dao.entryCount(playlist.id).toLong(); title = playlist.name
        rows = dao.entries(playlist.id, offset, limit).mapIndexed { index, e -> row(e.id, e.trackId, e.audio, offset + index) }
      }
      "favorites" -> {
        val search = (options["search"] as? String).orEmpty().trim()
        val pattern = "%" + search.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"
        total = if (search.isEmpty()) dao.favoriteCount() else dao.searchFavoriteCount(pattern)
        title = "Favorites"
        val favorites = if (search.isEmpty()) dao.favorites(offset, limit) else dao.searchFavorites(pattern, offset, limit)
        rows = favorites.mapIndexed { index, e -> row(e.key, e.trackId, e.audio, offset + index) }
      }
      "recent" -> { total = dao.recentCount(); title = "Recently played"; rows = dao.recent(offset, limit).mapIndexed { index, e -> row(e.key, e.trackId, e.audio, offset + index) } }
      else -> error("Choose playlists, favorites or recently played.")
    }
    return mapOf("items" to rows, "total" to total.toDouble(), "offset" to offset, "hasMore" to (offset + rows.size < total), "title" to title)
  }
}
