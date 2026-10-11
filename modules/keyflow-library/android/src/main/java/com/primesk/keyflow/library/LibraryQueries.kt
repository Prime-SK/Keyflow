package com.primesk.keyflow.library

import androidx.sqlite.db.SimpleSQLiteQuery

object LibraryQueries {
  const val VISIBLE = "COALESCE((SELECT r.included FROM library_folder_rules r WHERE substr(t.folderKey,1,length(r.path))=r.path ORDER BY length(r.path) DESC LIMIT 1),1)=1 AND EXISTS (SELECT 1 FROM locations l JOIN sources s ON s.id=l.sourceId WHERE l.trackId=t.id AND s.enabled=1 AND COALESCE((SELECT r.included FROM folder_rules r WHERE r.sourceId=l.sourceId AND substr(t.folderKey,1,length(r.path))=r.path ORDER BY length(r.path) DESC LIMIT 1),1)=1)"
  fun searchExpression(input: String): String = Regex("[\\p{L}\\p{N}]+").findAll(input).take(20)
    // Adjacent quoted terms imply AND in both Android's legacy FTS4 parser and enhanced parsers.
    .joinToString(" ") { "\"${it.value}*\"" }

  fun where(options: Map<String, Any?>): Pair<String, List<Any>> {
    val clauses = mutableListOf(if (options["includeExcluded"] == true)
      "EXISTS (SELECT 1 FROM locations l JOIN sources s ON s.id=l.sourceId WHERE l.trackId=t.id)" else VISIBLE)
    val args = mutableListOf<Any>()
    (options["sourceId"] as? String)?.let { source ->
      clauses += "EXISTS (SELECT 1 FROM locations sl WHERE sl.trackId=t.id AND sl.sourceId=?)"; args += source
    }
    (options["folderSearch"] as? String)?.takeIf { it.isNotBlank() }?.let { search ->
      clauses += "INSTR(LOWER(t.folderName || ' ' || t.folderKey), LOWER(?)) > 0"; args += search
    }
    val search = searchExpression(options["search"] as? String ?: "")
    if (search.isNotEmpty()) {
      clauses += "t.rowid IN (SELECT docid FROM track_search WHERE track_search MATCH ?)"; args += search
    }
    val groupKind = options["groupKind"] as? String
    val groupKey = options["groupKey"] as? String
    if (groupKey != null) {
      clauses += when (groupKind) {
        "albums" -> "t.album || char(31) || t.albumArtist = ?"
        "artists" -> "t.artist = ?"
        "folders" -> "t.folderKey = ?"
        else -> error("Unknown library group.")
      }
      args += groupKey
    }
    return clauses.joinToString(" AND ") to args
  }

  fun page(options: Map<String, Any?>): Pair<Int, Int> {
    val limit = ((options["limit"] as? Number)?.toInt() ?: 50).coerceIn(1, 100)
    val offset = ((options["offset"] as? Number)?.toInt() ?: 0).coerceAtLeast(0)
    return limit to offset
  }

  fun tracks(options: Map<String, Any?>, queue: Boolean = false): SimpleSQLiteQuery {
    val (where, args) = where(options)
    val column = when (options["sort"]) {
      "artist" -> "t.artist COLLATE NOCASE"
      "album" -> "t.album COLLATE NOCASE"
      "duration" -> "t.durationMs"
      "fileName" -> "t.fileName COLLATE NOCASE"
      "dateAdded" -> "t.dateAdded"
      else -> "t.title COLLATE NOCASE"
    }
    val direction = if (options["descending"] == true) "DESC" else "ASC"
    val (limit, offset) = page(options)
    val candidate = "FROM locations l JOIN sources s ON s.id=l.sourceId WHERE l.trackId=t.id AND s.enabled=1 AND COALESCE((SELECT r.included FROM folder_rules r WHERE r.sourceId=l.sourceId AND substr(t.folderKey,1,length(r.path))=r.path ORDER BY length(r.path) DESC LIMIT 1),1)=1 ORDER BY (s.access='available' AND l.availability='available') DESC, l.sourceId, l.uri LIMIT 1"
    val projection = if (queue) "t.*, (SELECT l.uri $candidate) AS queueUri, COALESCE((SELECT s.kind='folder' $candidate),0) AS persistedPermission" else "t.*"
    return SimpleSQLiteQuery("SELECT $projection FROM tracks t WHERE $where ORDER BY $column $direction, t.id ASC LIMIT ? OFFSET ?",
      (args + listOf(limit, offset)).toTypedArray())
  }

  fun count(options: Map<String, Any?>): SimpleSQLiteQuery {
    val (where, args) = where(options)
    return SimpleSQLiteQuery("SELECT COUNT(*) FROM tracks t WHERE $where", args.toTypedArray())
  }

  fun groups(kind: String, options: Map<String, Any?>, countOnly: Boolean = false): SimpleSQLiteQuery {
    val (where, args) = where(options.filterKeys { it != "groupKey" && it != "groupKind" })
    val (key, title, subtitle) = when (kind) {
      "albums" -> Triple("t.album || char(31) || t.albumArtist", "t.album", "t.albumArtist")
      "artists" -> Triple("t.artist", "t.artist", "''")
      "folders" -> Triple("t.folderKey", "t.folderName", "t.folderKey")
      else -> error("Unknown library section.")
    }
    val grouped = "SELECT $key AS `key`, $title AS title, $subtitle AS subtitle, COUNT(*) AS count FROM tracks t WHERE $where GROUP BY $key"
    if (countOnly) return SimpleSQLiteQuery("SELECT COUNT(*) FROM ($grouped)", args.toTypedArray())
    val (limit, offset) = page(options)
    return SimpleSQLiteQuery("$grouped ORDER BY title COLLATE NOCASE, `key` LIMIT ? OFFSET ?", (args + listOf(limit, offset)).toTypedArray())
  }
}
