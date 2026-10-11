package com.primesk.keyflow.library

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal object LibraryMetadata {
  private val artworkSlots = Semaphore(2)
  fun fill(context: Context, uri: Uri, track: Track): Track {
    val reader = MediaMetadataRetriever()
    return try {
      reader.setDataSource(context, uri)
      fun tag(key: Int) = reader.extractMetadata(key)?.takeIf { it.isNotBlank() && it != "<unknown>" }
      val artist = tag(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: track.artist
      track.copy(title = tag(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: track.title,
        artist = artist, album = tag(MediaMetadataRetriever.METADATA_KEY_ALBUM) ?: track.album,
        albumArtist = tag(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
          ?: track.albumArtist.takeIf { it != "Unknown artist" } ?: artist,
        durationMs = tag(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: track.durationMs,
        metadataComplete = true)
    } catch (_: Exception) { track.copy(metadataComplete = true) }
    finally { reader.release() }
  }
  suspend fun artwork(context: Context, uri: Uri, identity: String, modified: Long, size: Long = 0): String? = artworkSlots.withPermit {
    withContext(Dispatchers.IO) {
      val cache = File(context.cacheDir, "keyflow-library-artwork").apply { mkdirs() }
      val key = MessageDigest.getInstance("SHA-256").digest("$identity:$modified:$size".toByteArray()).joinToString("") { "%02x".format(it) }
      val file = File(cache, "$key.img")
      val absent = File(cache, "$key.none")
      if (file.exists()) { file.setLastModified(System.currentTimeMillis()); return@withContext Uri.fromFile(file).toString() }
      if (absent.exists()) return@withContext null
      val reader = MediaMetadataRetriever()
      try {
        reader.setDataSource(context, uri)
        val bytes = reader.embeddedPicture
        if (bytes == null || bytes.size > 4 * 1024 * 1024) { absent.writeText(""); prune(cache, absent); return@withContext null }
        val temporary = File(cache, "$key.tmp")
        temporary.writeBytes(bytes)
        if (!temporary.renameTo(file)) { temporary.delete(); return@withContext null }
        // Bound disk use to 64 MB and absence markers to 10,000 entries.
        prune(cache, file)
        Uri.fromFile(file).toString()
      } catch (_: Exception) { null } finally { reader.release() }
    }
  }
  private fun prune(cache: File, keep: File) {
        val files = cache.listFiles()?.sortedBy { it.lastModified() } ?: emptyList()
        var total = files.sumOf { it.length() }
        var markers = files.count { it.extension == "none" }
        for (old in files) {
          if (old == keep) continue
          if (total > 64L * 1024 * 1024 || (old.extension == "none" && markers > 10_000)) {
            val length = old.length()
            if (old.delete()) { total -= length; if (old.extension == "none") markers-- }
          }
        }
  }
}
