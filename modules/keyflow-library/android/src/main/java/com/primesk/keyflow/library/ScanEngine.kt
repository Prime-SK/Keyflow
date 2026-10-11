package com.primesk.keyflow.library

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import kotlinx.coroutines.*
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

internal class ScanEngine(val repository: LibraryRepository, resume: Boolean = true) {
  private val context = repository.context
  private val dao = repository.dao
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private var job: Job? = null
  private var refreshJob: Job? = null
  private val settings = context.getSharedPreferences("keyflow-library-settings", Context.MODE_PRIVATE)
  fun automaticScanning(): Boolean = settings.getBoolean("automatic-scanning", false)
  @Synchronized fun setAutomaticScanning(enabled: Boolean) {
    check(settings.edit().putBoolean("automatic-scanning", enabled).commit()) { "Could not save scanning preference." }
    if (!enabled) { refreshJob?.cancel(); refreshJob = null }
  }
  private val listeners = CopyOnWriteArrayList<(Map<String, Any?>) -> Unit>()
  @Volatile private var snapshot = SavedScan()
  @Volatile private var revision = System.currentTimeMillis()
  private var lastPublished = 0L
  private val pending = mutableListOf<Pair<Track, Location>>()

  init { if (resume) scope.launch {
    val saved = dao.scan() ?: SavedScan()
    synchronized(this@ScanEngine) {
      if (job?.isCompleted != false) {
        publish(saved, true)
        if (saved.status == "running") {
          publish(saved.copy(status = "interrupted", error = "Resuming an interrupted scan."), true)
          start()
        }
      }
    }
  } }
  fun listen(listener: (Map<String, Any?>) -> Unit): () -> Unit { listeners.add(listener); return { listeners.remove(listener) } }
  fun state(): Map<String, Any?> = mapOf("revision" to revision.toDouble(), "status" to snapshot.status,
    "discovered" to snapshot.discovered.toDouble(), "processed" to snapshot.processed.toDouble(),
    "sourceName" to snapshot.sourceName, "error" to snapshot.error)
  @Synchronized private fun publish(next: SavedScan, force: Boolean = false) {
    snapshot = next
    val now = System.currentTimeMillis()
    if (!force && now - lastPublished < 500) return
    lastPublished = now; revision++
    dao.putScan(next)
    val event = state(); listeners.forEach { it(event) }
  }
  fun requireIdle() { check(job?.isCompleted != false) { "Cancel the current scan before changing sources or folders." } }
  @Synchronized fun start(): Map<String, Any?> {
    if (job?.isCompleted == false || (job != null && snapshot.status == "running")) return state()
    publish(SavedScan(status = "running"), true)
    val launched = scope.launch(start = CoroutineStart.LAZY) { runScan() }
    job = launched
    // Cancellation before the coroutine enters runScan must also terminate its saved state.
    launched.invokeOnCompletion { failure ->
      if (failure is CancellationException) scope.launch {
        synchronized(this@ScanEngine) {
          if (job === launched && snapshot.status == "running") publish(snapshot.copy(status = "cancelled", sourceName = null), true)
        }
      }
    }
    launched.start()
    return state()
  }
  @Synchronized fun requestRefresh() {
    if (!automaticScanning()) return
    refreshJob?.cancel()
    refreshJob = scope.launch {
      delay(1500)
      job?.join()
      if (automaticScanning() && dao.sources().any { it.enabled && it.kind == "device" } && hasPermission()) start()
    }
  }
  @Synchronized fun cancel(): Map<String, Any?> { refreshJob?.cancel(); refreshJob = null; job?.cancel(); return state() }
  internal fun shutdown() { scope.cancel(); listeners.clear() }
  fun permission(): String = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
  fun hasPermission(): Boolean = context.checkSelfPermission(permission()) == PackageManager.PERMISSION_GRANTED
  private fun volumes(): Set<String> = if (Build.VERSION.SDK_INT >= 29) MediaStore.getExternalVolumeNames(context) else setOf("external")
  fun addDeviceSources() {
    requireIdle(); check(hasPermission()) { "Allow audio access before scanning the device." }
    for (volume in volumes()) {
      val id = "device:$volume"
      val existing = dao.source(id)
      dao.putSource(existing?.copy(enabled = true, access = "available") ?: Source(id, "device", volume,
        if (volume == "external_primary" || volume == "external") "Device audio" else "Storage $volume"))
    }
  }
  fun refreshAvailability() {
    val mounted = volumes()
    val grants = context.contentResolver.persistedUriPermissions.filter { it.isReadPermission }.map { it.uri.toString() }.toSet()
    for (source in dao.sources()) {
      val access = if (source.kind == "device") {
        if (!hasPermission()) "permission" else if (source.locator !in mounted) "disconnected" else "available"
      } else {
        val tree = Uri.parse(source.locator)
        val doc = runCatching { DocumentsContract.getTreeDocumentId(tree) }.getOrNull()
        val volume = doc?.substringBefore(':')
        if (tree.authority == "com.android.externalstorage.documents" && Build.VERSION.SDK_INT >= 29 && volume != null &&
          (if (volume == "primary") "external_primary" else volume.lowercase()) !in mounted) "disconnected"
        else if (source.locator !in grants) "permission" else "available"
      }
      if (source.access != access) dao.putSource(source.copy(access = access))
    }
  }
  private suspend fun runScan() {
    var failures = 0
    try {
      refreshAvailability()
      for (source in dao.sources().filter { it.enabled }) {
        currentCoroutineContext().ensureActive()
        if (source.access != "available") { failures++; continue }
        val token = UUID.randomUUID().toString()
        dao.putSource(source.copy(checkpoint = token, error = null))
        publish(snapshot.copy(sourceName = source.name), true)
        try {
          if (source.kind == "device") scanMedia(source, token) else scanTree(source, token)
          flush()
          currentCoroutineContext().ensureActive()
          // A disappearing volume must not turn a completed cursor into deletion evidence.
          refreshAvailability()
          check(dao.source(source.id)?.access == "available") { "Storage became unavailable during scanning." }
          repository.db.runInTransaction {
            dao.reconcile(source.id, token)
            dao.putSource(source.copy(checkpoint = null, lastCompleted = System.currentTimeMillis(), error = null))
          }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) {
          flush(); failures++
          val current = dao.source(source.id) ?: continue
          dao.putSource(current.copy(access = when (error) {
            is SecurityException -> "permission"
            is java.io.FileNotFoundException -> "missing"
            else -> current.access
          },
            error = error.message ?: "This source could not be scanned."))
        }
      }
      publish(snapshot.copy(status = if (failures == 0) "completed" else "error", sourceName = null,
        error = if (failures > 0) "$failures source(s) unavailable or incomplete. Check library sources." else null), true)
    } catch (_: CancellationException) {
      withContext(NonCancellable) { flush(); publish(snapshot.copy(status = "cancelled", sourceName = null), true) }
    } catch (error: Exception) {
      flush(); publish(snapshot.copy(status = "error", sourceName = null, error = error.message ?: "Scan failed."), true)
    }
  }
  private fun flush() {
    if (pending.isEmpty()) return
    repository.db.runInTransaction { for ((track, location) in pending) { dao.putTrack(track); dao.putLocation(location) } }
    pending.clear()
    publish(snapshot, true)
  }
  private suspend fun discovered(source: Source, token: String, uri: Uri, candidate: Track, rules: List<FolderRule>) {
    currentCoroutineContext().ensureActive()
    val old = dao.byIdentity(candidate.identity) ?: pending.firstOrNull { it.first.identity == candidate.identity }?.first
    val included = LibraryIdentity.included(candidate.folderKey, rules) && LibraryIdentity.libraryIncluded(candidate.folderKey, dao.libraryRules())
    var track = candidate.copy(id = old?.id ?: candidate.id, dateAdded = old?.dateAdded ?: candidate.dateAdded)
    if (old != null && old.modified == candidate.modified && old.size == candidate.size && old.metadataComplete) track = old
    else if (included) track = LibraryMetadata.fill(context, uri, track)
    pending += track to Location(source.id, uri.toString(), track.id, token, "available")
    publish(snapshot.copy(discovered = snapshot.discovered + 1, processed = snapshot.processed + 1))
    if (pending.size >= 25) flush()
    val musicActive = (context.getSystemService(Context.AUDIO_SERVICE) as AudioManager).isMusicActive
    delay(if (musicActive) 15 else 1)
  }
  private suspend fun scanMedia(source: Source, token: String) {
    val collection = if (Build.VERSION.SDK_INT >= 29) MediaStore.Audio.Media.getContentUri(source.locator) else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
    val columns = mutableListOf("_id", "_display_name", "title", "artist", "album", "duration", "mime_type", "_size", "date_modified", "date_added")
    if (Build.VERSION.SDK_INT >= 30) columns += "album_artist"
    columns += if (Build.VERSION.SDK_INT >= 29) "relative_path" else "_data"
    val selection = when { Build.VERSION.SDK_INT >= 30 -> "is_pending=0 AND is_trashed=0"; Build.VERSION.SDK_INT >= 29 -> "is_pending=0"; else -> null }
    val rules = dao.rules(source.id)
    val cursor = context.contentResolver.query(collection, columns.toTypedArray(), selection, null, "_id ASC") ?: error("Device audio index is unavailable.")
    cursor.use { c ->
      fun text(name: String): String? = c.getColumnIndex(name).takeIf { it >= 0 }?.let { c.getString(it) }?.takeIf { it.isNotBlank() && it != "<unknown>" }
      fun number(name: String): Long = c.getColumnIndex(name).takeIf { it >= 0 }?.let { c.getLong(it) } ?: 0
      while (c.moveToNext()) {
        currentCoroutineContext().ensureActive()
        val uri = ContentUris.withAppendedId(collection, number("_id"))
        val file = text("_display_name") ?: "Audio ${number("_id")}"
        val identity = if (Build.VERSION.SDK_INT >= 29) text("relative_path")?.let { LibraryIdentity.external(source.locator, it + file) } ?: "media:$uri"
          else text("_data")?.let(LibraryIdentity::legacyPath) ?: "media:$uri"
        val folder = LibraryIdentity.folder(identity)
        val artist = text("artist") ?: "Unknown artist"
        discovered(source, token, uri, Track(identity = identity, fileName = file,
          title = text("title") ?: file.substringBeforeLast('.', file), artist = artist,
          album = text("album") ?: "Unknown album", albumArtist = text("album_artist") ?: artist,
          durationMs = number("duration"), mimeType = text("mime_type"), size = number("_size"), modified = number("date_modified") * 1000,
          dateAdded = number("date_added") * 1000, folderKey = folder, folderName = folder.trimEnd('/').substringAfterLast('/').substringAfter(':').ifBlank { "Storage root" }), rules)
      }
    }
  }
  private suspend fun scanTree(source: Source, token: String) {
    val tree = Uri.parse(source.locator)
    val root = DocumentsContract.getTreeDocumentId(tree)
    val directories = ArrayDeque<String>(); directories.add(root)
    val visited = mutableSetOf<String>()
    val rules = dao.rules(source.id)
    while (directories.isNotEmpty()) {
      currentCoroutineContext().ensureActive()
      val parent = directories.removeLast()
      if (!visited.add(parent)) continue
      val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parent)
      val projection = arrayOf("document_id", "_display_name", "mime_type", "_size", "last_modified")
      val cursor = context.contentResolver.query(children, projection, null, null, null) ?: error("This folder is unavailable.")
      cursor.use { c -> while (c.moveToNext()) {
        currentCoroutineContext().ensureActive()
        val id = c.getString(0); val file = c.getString(1) ?: "Local audio"; val mime = c.getString(2)
        if (mime == DocumentsContract.Document.MIME_TYPE_DIR) { directories.add(id); continue }
        if (!LibraryIdentity.isAudio(file, mime)) continue
        val uri = DocumentsContract.buildDocumentUriUsingTree(tree, id)
        val identity = LibraryIdentity.document(tree.authority, id)
        val folder = if (tree.authority == "com.android.externalstorage.documents") LibraryIdentity.folder(identity)
          else "document:${tree.authority}:$parent/"
        discovered(source, token, uri, Track(identity = identity, fileName = file, title = file.substringBeforeLast('.', file),
          mimeType = mime, size = c.getLong(3), modified = c.getLong(4), folderKey = folder,
          folderName = parent.substringAfterLast('/').substringAfter(':').ifBlank { source.name }), rules)
      } }
    }
  }
  companion object {
    @Volatile private var instance: ScanEngine? = null
    fun get(context: Context): ScanEngine = instance ?: synchronized(this) {
      instance ?: ScanEngine(LibraryRepository(context.applicationContext)).also { engine ->
        // Watch the audio index only. Artwork/database writes do not trigger a rescan.
        context.applicationContext.contentResolver.registerContentObserver(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
          true, object : android.database.ContentObserver(android.os.Handler(android.os.Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { engine.requestRefresh() }
          })
        instance = engine
      }
    }
  }
}
