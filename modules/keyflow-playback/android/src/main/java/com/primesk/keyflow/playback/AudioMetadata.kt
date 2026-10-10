package com.primesk.keyflow.playback

import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.security.MessageDigest

internal object AudioMetadata {
  fun read(context: Context, selection: AudioSelection): Map<String, Any?> {
    val uri = selection.uri
    require(uri.scheme == "content" || uri.scheme == "file") { "Choose an audio file stored on your device." }
    var persisted = false
    if (selection.flags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION != 0) {
      try {
        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        persisted = true
      } catch (_: SecurityException) {
        // Some local providers offer only a session grant; playback can still use it now.
      }
    }
    // Verify access before replacing a playing song. Never copy or modify its contents.
    context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { }
      ?: error("This file is no longer available. Choose it again.")
    val fileName = displayName(context, uri)
    var title = fileName.substringBeforeLast('.', fileName)
    var artist: String? = null
    var durationMs = 0L
    var artworkUri: String? = null
    val retriever = MediaMetadataRetriever()
    try {
      retriever.setDataSource(context, uri)
      title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() } ?: title
      artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.takeIf { it.isNotBlank() }
      durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
      retriever.embeddedPicture?.takeIf { it.size <= 4 * 1024 * 1024 }?.let { artwork ->
        val cache = File(context.cacheDir, "keyflow-artwork").apply { mkdirs() }
        val digest = MessageDigest.getInstance("SHA-256").digest(artwork).joinToString("") { "%02x".format(it) }
        val file = File(cache, "$digest.img")
        if (!file.exists()) file.writeBytes(artwork)
        artworkUri = Uri.fromFile(file).toString()
      }
    } catch (_: Exception) {
      // ExoPlayer remains the decoder authority when platform tag extraction is unsupported.
    } finally {
      retriever.release()
    }
    return mapOf("uri" to uri.toString(), "fileName" to fileName, "title" to title,
      "artist" to artist, "durationMs" to durationMs.toDouble(), "artworkUri" to artworkUri,
      "mimeType" to context.contentResolver.getType(uri), "persistedPermission" to persisted)
  }

  private fun displayName(context: Context, uri: Uri): String {
    try {
      context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) return cursor.getString(0) ?: "Local audio"
      }
    } catch (_: Exception) { }
    return uri.lastPathSegment?.substringAfterLast('/') ?: "Local audio"
  }
}
