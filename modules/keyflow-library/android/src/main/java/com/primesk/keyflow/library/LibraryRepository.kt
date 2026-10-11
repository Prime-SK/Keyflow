package com.primesk.keyflow.library

import android.content.Context
import android.net.Uri

internal class LibraryRepository(val context: Context, val db: LibraryDatabase = LibraryDatabase.get(context)) {
  val dao = db.dao()
  fun availability(trackId: String): String {
    val track = dao.track(trackId) ?: return "missing"
    if (!LibraryIdentity.libraryIncluded(track.folderKey, dao.libraryRules())) return "excluded"
    val locations = dao.locations(trackId)
    val reasons = locations.mapNotNull { location -> dao.source(location.sourceId)?.takeIf { it.enabled }?.let { source ->
      if (!LibraryIdentity.included(track.folderKey, dao.rules(source.id))) null
      else if (source.access != "available") source.access
      else if (location.availability == "excluded") "available" else location.availability
    } }
    return if ("available" in reasons) "available" else reasons.firstOrNull() ?: "missing"
  }
  fun trackMap(t: Track): Map<String, Any?> = mapOf("id" to t.id, "title" to t.title,
    "artist" to t.artist, "album" to t.album, "albumArtist" to t.albumArtist,
    "fileName" to t.fileName, "durationMs" to t.durationMs.toDouble(), "folderKey" to t.folderKey,
    "folderName" to t.folderName, "artworkUri" to t.artworkUri, "availability" to availability(t.id),
    "dateAdded" to t.dateAdded.toDouble(), "artworkVersion" to "${t.modified}:${t.size}")
  fun tracks(options: Map<String, Any?>): Map<String, Any> {
    val items = dao.tracks(LibraryQueries.tracks(options)).map(::trackMap)
    val total = dao.count(LibraryQueries.count(options))
    val (_, offset) = LibraryQueries.page(options)
    return mapOf("items" to items, "total" to total.toDouble(), "offset" to offset, "hasMore" to (offset + items.size < total))
  }
  fun groups(kind: String, options: Map<String, Any?>): Map<String, Any> {
    val items = dao.groups(LibraryQueries.groups(kind, options)).map { mapOf("key" to it.key,
      "title" to it.title, "subtitle" to it.subtitle, "count" to it.count.toDouble()) }
    val total = dao.count(LibraryQueries.groups(kind, options, true))
    val (_, offset) = LibraryQueries.page(options)
    return mapOf("items" to items, "total" to total.toDouble(), "offset" to offset, "hasMore" to (offset + items.size < total))
  }
  fun resolve(id: String): Map<String, Any?> {
    val track = dao.track(id) ?: error("This song is no longer in the library.")
    check(LibraryIdentity.libraryIncluded(track.folderKey, dao.libraryRules())) { "This folder is excluded from the library." }
    val candidates = dao.locations(id).filter { location ->
      val source = dao.source(location.sourceId)
      location.availability in listOf("available", "excluded") && source?.enabled == true && source.access == "available" &&
        LibraryIdentity.included(track.folderKey, dao.rules(source.id))
    }
    for (location in candidates) {
      try {
        context.contentResolver.openAssetFileDescriptor(Uri.parse(location.uri), "r")?.use { }
          ?: continue
        return mapOf("uri" to location.uri, "fileName" to track.fileName, "title" to track.title,
          "artist" to track.artist, "durationMs" to track.durationMs.toDouble(), "artworkUri" to track.artworkUri,
          "mimeType" to track.mimeType, "persistedPermission" to (dao.source(location.sourceId)?.kind == "folder"))
      } catch (_: SecurityException) {
        dao.source(location.sourceId)?.let { dao.putSource(it.copy(access = "permission")) }
      } catch (_: Exception) { dao.putLocation(location.copy(availability = "missing")) }
    }
    error("This song is unavailable. Reconnect its storage or grant folder access again.")
  }
}
