package com.primesk.keyflow.library

import android.net.Uri
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class MetadataDeviceTest {
  private val instrumentation = InstrumentationRegistry.getInstrumentation()
  private val context = instrumentation.targetContext
  @Test fun playbackArtworkLoadsUncachedCoverAndClearsStaleFallback() = runBlocking {
    val name = "playback-artwork-test.db"; context.deleteDatabase(name)
    val db = Room.databaseBuilder(context, LibraryDatabase::class.java, name).build()
    val covered = File(context.cacheDir, "covered-fixture.mp3")
    val plain = File(context.cacheDir, "plain-fixture.mp3")
    try {
      val original = instrumentation.context.assets.open("audio/tone.mp3").use { it.readBytes() }
      plain.writeBytes(original)
      val bitmap = android.graphics.Bitmap.createBitmap(2, 2, android.graphics.Bitmap.Config.ARGB_8888)
      bitmap.eraseColor(android.graphics.Color.RED)
      val png = java.io.ByteArrayOutputStream().apply { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, this) }.toByteArray()
      bitmap.recycle()
      val picture = byteArrayOf(0) + "image/png".toByteArray() + byteArrayOf(0, 3, 0) + png
      val frame = "APIC".toByteArray() + java.nio.ByteBuffer.allocate(4).putInt(picture.size).array() + byteArrayOf(0, 0) + picture
      fun syncSafe(size: Int) = byteArrayOf(((size shr 21) and 127).toByte(), ((size shr 14) and 127).toByte(), ((size shr 7) and 127).toByte(), (size and 127).toByte())
      val oldSize = if (String(original.copyOfRange(0, 3)) == "ID3")
        10 + ((original[6].toInt() and 127) shl 21) + ((original[7].toInt() and 127) shl 14) + ((original[8].toInt() and 127) shl 7) + (original[9].toInt() and 127) else 0
      covered.writeBytes("ID3".toByteArray() + byteArrayOf(3, 0, 0) + syncSafe(frame.size) + frame + original.copyOfRange(oldSize, original.size))
      val first = Track(identity = "fixture:covered:${System.nanoTime()}", fileName = covered.name, title = "Covered", folderKey = "fixture:/", folderName = "Fixtures")
      val second = first.copy(id = "plain", identity = "fixture:plain:${System.nanoTime()}", fileName = plain.name, title = "Plain")
      db.dao().putTrack(first); db.dao().putTrack(second)
      val repository = LibraryRepository(context, db)
      val artwork = repository.artworkForPlayback(first.id, Uri.fromFile(covered))
      assertNotNull(artwork); assertTrue(File(Uri.parse(artwork).path!!).exists())
      assertEquals(artwork, db.dao().track(first.id)?.artworkUri)
      db.dao().putTrack(second.copy(artworkUri = artwork))
      assertNull(repository.artworkForPlayback(second.id, Uri.fromFile(plain)))
      assertNull(db.dao().track(second.id)?.artworkUri)
    } finally { db.close(); covered.delete(); plain.delete(); context.deleteDatabase(name) }
  }
  @Test fun untaggedFormatsAndCorruptFilesHaveSafeMetadataAndArtworkFallbacks() = runBlocking {
    for (name in listOf("tone.mp3", "tone.m4a", "tone.flac", "tone.wav", "tone.ogg", "corrupt.mp3")) {
      val file = File(context.cacheDir, "metadata-$name")
      instrumentation.context.assets.open("audio/$name").use { input -> file.outputStream().use { input.copyTo(it) } }
      try {
        val original = Track(identity = "fixture:$name", fileName = name, title = name.substringBeforeLast('.'), folderKey = "fixture:/", folderName = "Fixtures")
        val result = LibraryMetadata.fill(context, Uri.fromFile(file), original)
        assertTrue(result.metadataComplete)
        assertTrue(result.title.isNotBlank()); assertTrue(result.artist.isNotBlank()); assertTrue(result.album.isNotBlank())
        if (name != "corrupt.mp3") assertTrue("$name duration", result.durationMs > 0)
        assertNull(LibraryMetadata.artwork(context, Uri.fromFile(file), original.identity, file.lastModified()))
        assertEquals(original.id, result.id)
      } finally { file.delete() }
    }
  }
}
