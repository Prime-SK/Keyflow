package com.primesk.keyflow.library

import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import expo.modules.kotlin.functions.Coroutine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.*
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import expo.modules.kotlin.activityresult.AppContextActivityResultLauncher
import expo.modules.interfaces.permissions.PermissionsResponseListener
import kotlin.coroutines.resume

class KeyflowLibraryModule : Module() {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private lateinit var folderPicker: AppContextActivityResultLauncher<String, FolderSelection?>
  private var picking = false
  private var unlisten: (() -> Unit)? = null
  private fun context() = requireNotNull(appContext.reactContext).applicationContext
  private fun engine() = ScanEngine.get(context())
  private fun repository() = LibraryRepository(requireNotNull(appContext.reactContext).applicationContext)
  private fun changed(invalidatePages: Boolean = false) { sendEvent("onLibraryChanged", mapOf("revision" to System.currentTimeMillis().toDouble(), "invalidatePages" to invalidatePages)) }
  override fun definition() = ModuleDefinition {
    Name("KeyflowLibrary")
    Events("onLibraryChanged", "onScanState")
    RegisterActivityContracts { folderPicker = registerForActivityResult(FolderContract()) { _, _ -> picking = false } }
    OnStartObserving {
      unlisten = engine().listen { event -> sendEvent("onScanState", event); changed() }
    }
    OnStopObserving { unlisten?.invoke(); unlisten = null }
    OnDestroy { unlisten?.invoke(); scope.cancel() }
    OnActivityEntersForeground { scope.launch {
      val engine = engine()
      engine.refreshAvailability(); changed()
      if (engine.automaticScanning() && !picking && repository().dao.sources().any { it.enabled && it.access == "available" }) engine.start()
    } }
    AsyncFunction("getPermission") { mapOf("granted" to engine().hasPermission()) }
    AsyncFunction("getAutomaticScanning") { engine().automaticScanning() }
    AsyncFunction("setAutomaticScanning") Coroutine { enabled: Boolean -> withContext(Dispatchers.IO) {
      engine().setAutomaticScanning(enabled)
    } }
    AsyncFunction("requestPermission") Coroutine { -> withContext(Dispatchers.Main) {
      val permission = engine().permission()
      suspendCancellableCoroutine<Map<String, Any>> { continuation ->
        val manager = requireNotNull(appContext.permissions) { "Audio permission requests are unavailable." }
        manager.askForPermissions(PermissionsResponseListener { response ->
          if (continuation.isActive) continuation.resume(mapOf("granted" to engine().hasPermission(),
            "canAskAgain" to (response[permission]?.canAskAgain ?: true)))
        }, permission)
      }
    } }
    AsyncFunction("enableDeviceScan") Coroutine { -> withContext(Dispatchers.IO) { engine().addDeviceSources(); changed(); engine().start() } }
    AsyncFunction("startScan") Coroutine { -> withContext(Dispatchers.IO) { engine().start() } }
    AsyncFunction("cancelScan") { engine().cancel() }
    AsyncFunction("getScanState") { engine().state() }
    AsyncFunction("addFolder") Coroutine { -> withContext(Dispatchers.Main) {
      check(!picking) { "A folder picker is already open." }
      engine().requireIdle(); picking = true
      try {
        val selection = folderPicker.launch("folder") ?: return@withContext null
        withContext(Dispatchers.IO) {
          check(selection.flags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION != 0) { "Choose a folder that supports saved local access." }
          context().contentResolver.takePersistableUriPermission(selection.uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
          val documentId = DocumentsContract.getTreeDocumentId(selection.uri)
          val id = "folder:${selection.uri}"
          val dao = repository().dao
          dao.putSource(dao.source(id)?.copy(access = "available", enabled = true) ?: Source(id, "folder", selection.uri.toString(),
            documentId.substringAfterLast('/').substringAfter(':').ifBlank { "Selected folder" }))
          changed(); engine().start(); id
        }
      } finally { picking = false }
    } }
    AsyncFunction("removeSource") Coroutine { id: String -> withContext(Dispatchers.IO) {
      engine().requireIdle()
      val r = repository(); val source = r.dao.source(id)
      r.dao.removeSource(id)
      if (source?.kind == "folder") runCatching { context().contentResolver.releasePersistableUriPermission(Uri.parse(source.locator), Intent.FLAG_GRANT_READ_URI_PERMISSION) }
      changed()
    } }
    AsyncFunction("setSourceEnabled") Coroutine { id: String, enabled: Boolean -> withContext(Dispatchers.IO) {
      engine().requireIdle(); val dao = repository().dao; val source = dao.source(id) ?: error("Source not found.")
      dao.putSource(source.copy(enabled = enabled)); changed()
    } }
    AsyncFunction("getFolderRules") Coroutine { id: String -> withContext(Dispatchers.IO) {
      repository().dao.rules(id).map { mapOf("path" to it.path, "included" to it.included) }
    } }
    AsyncFunction("setFolderRule") Coroutine { id: String, path: String, included: Boolean -> withContext(Dispatchers.IO) {
      engine().requireIdle(); require(path.isNotBlank() && !path.split('/').contains("..")) { "Choose a valid folder path." }
      repository().dao.putRule(FolderRule(id, path.trimEnd('/') + "/", included)); changed(true)
    } }
    AsyncFunction("removeFolderRule") Coroutine { id: String, path: String -> withContext(Dispatchers.IO) {
      engine().requireIdle(); repository().dao.removeRule(id, path); changed(true)
    } }
    AsyncFunction("getLibraryFolderRules") Coroutine { -> withContext(Dispatchers.IO) {
      repository().dao.libraryRules().map { mapOf("path" to it.path, "included" to it.included) }
    } }
    AsyncFunction("setLibraryFolderRule") Coroutine { path: String, included: Boolean -> withContext(Dispatchers.IO) {
      require(path.isNotBlank() && !path.split('/').contains("..")) { "Choose a valid folder path." }
      repository().dao.putLibraryRule(LibraryFolderRule(path.trimEnd('/') + "/", included)); changed(true)
    } }
    AsyncFunction("removeLibraryFolderRule") Coroutine { path: String -> withContext(Dispatchers.IO) {
      repository().dao.removeLibraryRule(path); changed(true)
    } }
    AsyncFunction("pickExcludedFolder") Coroutine { -> withContext(Dispatchers.Main) {
      check(!picking) { "A folder picker is already open." }; picking = true
      try {
        val selection = folderPicker.launch("exclusion") ?: return@withContext null
        withContext(Dispatchers.IO) {
          require(selection.uri.authority == "com.android.externalstorage.documents") { "Choose a device or SD-card folder, or use the folders already listed." }
          val documentId = DocumentsContract.getTreeDocumentId(selection.uri)
          val path = LibraryIdentity.document(selection.uri.authority, documentId).trimEnd('/') + "/"
          repository().dao.putLibraryRule(LibraryFolderRule(path, false)); changed(true); path
        }
      } finally { picking = false }
    } }
    AsyncFunction("getArtwork") Coroutine { id: String -> withContext(Dispatchers.IO) {
      val r = repository(); val track = r.dao.track(id) ?: return@withContext null
      val audio = runCatching { r.resolve(id) }.getOrNull() ?: return@withContext null
      val artwork = LibraryMetadata.artwork(context(), Uri.parse(audio["uri"] as String), track.identity, track.modified, track.size)
      val current = r.dao.track(id)
      if (current == null || current.modified != track.modified || current.size != track.size) null
      else {
        if (current.artworkUri != artwork) r.dao.putTrack(current.copy(artworkUri = artwork))
        artwork
      }
    } }
    AsyncFunction("queryTracks") Coroutine { options: Map<String, Any?> -> withContext(Dispatchers.IO) { repository().tracks(options) } }
    AsyncFunction("queryGroups") Coroutine { kind: String, options: Map<String, Any?> -> withContext(Dispatchers.IO) { repository().groups(kind, options) } }
    AsyncFunction("getTrack") Coroutine { id: String -> withContext(Dispatchers.IO) { repository().let { r -> r.dao.track(id)?.let(r::trackMap) } } }
    AsyncFunction("resolveTrack") Coroutine { id: String -> withContext(Dispatchers.IO) { repository().resolve(id) } }
    AsyncFunction("getSources") Coroutine { -> withContext(Dispatchers.IO) { engine().refreshAvailability(); repository().dao.sources().map { source ->
      mapOf("id" to source.id, "kind" to source.kind, "name" to source.name, "locator" to source.locator,
        "enabled" to source.enabled, "access" to source.access, "lastCompleted" to source.lastCompleted.toDouble(), "error" to source.error)
    } } }
  }
}
