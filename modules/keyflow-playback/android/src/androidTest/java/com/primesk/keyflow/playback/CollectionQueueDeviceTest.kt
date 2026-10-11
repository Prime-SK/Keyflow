package com.primesk.keyflow.playback

import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.primesk.keyflow.library.CollectionsRepository
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@UnstableApi
@RunWith(AndroidJUnit4::class)
class CollectionQueueDeviceTest {
  @Test fun savedDisplayedSequenceKeepsDuplicatesAndLoadsSelectedOccurrence() {
    val instrumentation = InstrumentationRegistry.getInstrumentation(); val context = instrumentation.targetContext
    val file = File(context.cacheDir, "collection-queue.wav")
    instrumentation.context.assets.open("audio/tone.wav").use { input -> file.outputStream().use(input::copyTo) }
    val repo = CollectionsRepository(context)
    val audio = mapOf("uri" to Uri.fromFile(file).toString(), "title" to "Song", "fileName" to file.name)
    val physical = listOf("a", "b", "c").map { QueueStorage.item(audio + ("title" to it), it) }
    val byId = physical.associateBy { it.mediaId }
    val order = QueueOrder().apply { restore(listOf("a", "b", "c"), listOf("c", "a", "b"), true) }
    val saved = repo.saveQueue("Displayed shuffle", order.displayed.map { QueueStorage.audio(byId.getValue(it)) to null })
    try {
      val rows = repo.dao.entries(saved.id, 0, 50)
      assertEquals(listOf("c", "a", "b"), rows.map { repo.audioMap(it.audio)["title"] })
      val queue = QueueStorage.collectionQueue(context, "playlist", saved.id, rows[1].id)
      assertEquals(1, queue.second); assertEquals(listOf("c", "a", "b"), queue.first.map { it.mediaMetadata.title.toString() })
      assertEquals(3, queue.first.map { it.mediaId }.distinct().size)
      assertEquals(1, queue.first.map { it.localConfiguration!!.uri }.distinct().size)
      file.delete()
      assertThrows(Exception::class.java) { QueueStorage.collectionQueue(context, "playlist", saved.id, rows[1].id) }
    } finally { repo.dao.deletePlaylist(saved.id); file.delete() }
  }
}
