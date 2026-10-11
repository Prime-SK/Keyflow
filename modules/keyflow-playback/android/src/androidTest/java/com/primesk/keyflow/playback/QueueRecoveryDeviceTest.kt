package com.primesk.keyflow.playback

import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@UnstableApi
@RunWith(AndroidJUnit4::class)
class QueueRecoveryDeviceTest {
  @Test fun tenThousandEntriesPersistAcrossDatabaseReopenWithBoundedRows() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val name = "queue-stress-${java.util.UUID.randomUUID()}.db"
    fun database() = androidx.room.Room.databaseBuilder(context, com.primesk.keyflow.library.LibraryDatabase::class.java, name).build()
    var db = database()
    try {
      val unicode = "a".repeat(128 * 1024 - 1) + "\uD83C\uDFB5" + "尾"
      db.dao().saveRecoveryChunks(QueueStorage.recoveryChunks(unicode))
      assertEquals(unicode, db.dao().recoveryChunks().joinToString("") { it.snapshot })
      val started = System.nanoTime()
      val items = (0 until 10_000).map { n -> QueueStorage.item(mapOf(
        "uri" to "content://media/external/audio/media/$n", "title" to "Song $n with a longer musical title",
        "artist" to "Artist ${n % 200}", "fileName" to "track-$n.mp3", "mimeType" to "audio/mpeg"), trackId = "track-$n") }
      val order = QueueOrder().apply { replace(items.map { it.mediaId }); shuffle(true, items[9990].mediaId) }
      val snapshot = QueueStorage.encode(items, order, items[9990].mediaId, 54321, 2)
      assertTrue("Fixture must exercise cursor-sized snapshots", snapshot.toByteArray().size > 2 * 1024 * 1024)
      val chunks = QueueStorage.recoveryChunks(snapshot)
      db.dao().saveRecoveryChunks(chunks); db.close(); db = database()
      val rows = db.dao().recoveryChunks()
      assertTrue(rows.all { it.snapshot.toByteArray().size < 512 * 1024 })
      val recovered = QueueStorage.decode(rows.joinToString("") { it.snapshot })
      assertEquals(10_000, recovered.items.size); assertEquals(order.displayed, recovered.displayed)
      assertEquals(items[9990].mediaId, recovered.current); assertEquals(54321L, recovered.position)
      assertTrue(recovered.shuffle); assertEquals(2, recovered.repeat)
      // Replacing with a short legacy-shaped snapshot must leave no obsolete tail chunks.
      db.dao().saveRecoveryChunks(listOf(com.primesk.keyflow.library.PlaybackRecovery(snapshot = "short")))
      assertEquals(1, db.dao().recoveryChunks().size)
      assertTrue("Queue persistence took too long", (System.nanoTime() - started) / 1_000_000 < 20_000)
    } finally { db.close(); context.deleteDatabase(name) }
  }
  @Test fun duplicateSongEntriesAndShuffleRoundTripWithoutChangingFiles() {
    val audio = mapOf("uri" to Uri.fromFile(java.io.File("/local/music.wav")).toString(),
      "title" to "Practice", "fileName" to "music.wav", "artist" to "Artist", "persistedPermission" to true)
    val first = QueueStorage.item(audio, "occurrence-a", "track")
    val second = QueueStorage.item(audio, "occurrence-b", "track")
    val order = QueueOrder().apply { restore(listOf(first.mediaId, second.mediaId), listOf(second.mediaId, first.mediaId), true) }
    val recovered = QueueStorage.decode(QueueStorage.encode(listOf(first, second), order, second.mediaId, 12345, 2))
    assertEquals(2, recovered.items.size); assertTrue(recovered.shuffle)
    assertEquals(listOf("occurrence-b", "occurrence-a"), recovered.displayed)
    assertEquals("occurrence-b", recovered.current); assertEquals(12345L, recovered.position); assertEquals(2, recovered.repeat)
    assertEquals(first.localConfiguration?.uri, recovered.items.first().localConfiguration?.uri)
    assertEquals("track", recovered.items.first().mediaMetadata.extras?.getString("trackId"))
  }
  @Test fun malformedRecoveryFailsExplicitly() {
    assertThrows(Exception::class.java) { QueueStorage.decode("invalid") }
    assertThrows(Exception::class.java) { QueueStorage.decode("{\"version\":99}") }
  }
  @Test fun pendingPlayNextPersistsAndLegacySnapshotsStillRestore() {
    val audio = mapOf("uri" to "file:///local/song.wav", "title" to "Song")
    val items = listOf("a", "b", "c").map { QueueStorage.item(audio, it) }
    val order = QueueOrder().apply { replace(listOf("a", "b", "c")); playNext("c", "a"); shuffle(true, "a") }
    val encoded = QueueStorage.encode(items, order, "a", 999, 0)
    val saved = QueueStorage.decode(encoded)
    assertEquals(listOf("c"), saved.priority)
    val restored = QueueOrder().apply { restore(saved.base, saved.displayed, saved.shuffle, saved.priority) }
    restored.shuffle(false, "a"); restored.shuffle(true, "a")
    assertEquals(listOf("a", "c"), restored.displayed.take(2))
    val legacy = org.json.JSONObject(encoded).apply { remove("priority") }.toString()
    assertTrue(QueueStorage.decode(legacy).priority.isEmpty())
  }
}
