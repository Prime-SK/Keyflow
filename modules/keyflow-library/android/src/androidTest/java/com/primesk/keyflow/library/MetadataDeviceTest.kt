package com.primesk.keyflow.library

import android.net.Uri
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
