package com.primesk.keyflow.playback
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random
class QueueOrderTest {
  @Test fun shufflePlacesCurrentFirstAndRestoresUnderlyingOrder() {
    val queue = QueueOrder(); queue.replace(listOf("a", "b", "c", "d", "e"))
    queue.shuffle(true, "b", Random(1)); assertEquals("b", queue.displayed.first())
    assertEquals(queue.base.toSet(), queue.displayed.toSet())
    queue.shuffle(false, "d"); assertEquals(queue.base, queue.displayed)
  }
  @Test fun occurrenceIdsAreUniqueAndRecoveryValidatesOrder() {
    val queue = QueueOrder(); queue.restore(listOf("copy1", "copy2"), listOf("copy2", "copy1"), true)
    assertTrue(queue.shuffled); assertEquals("copy2", queue.displayed.first())
    assertThrows(IllegalArgumentException::class.java) { queue.replace(listOf("same", "same")) }
    assertThrows(IllegalArgumentException::class.java) { queue.restore(listOf("a"), listOf("b"), false) }
  }
  @Test fun lateSongStillShufflesTheEntireTenThousandEntryQueue() {
    val ids = (0 until 10000).map { "entry-$it" }
    val queue = QueueOrder(); queue.replace(ids)
    queue.shuffle(true, "entry-9995", Random(123))
    assertEquals("entry-9995", queue.displayed.first())
    assertEquals(ids.toSet(), queue.displayed.toSet()); assertEquals(10000, queue.displayed.distinct().size)
    assertTrue(queue.displayed.drop(1).indexOf("entry-0") >= 0)
    queue.shuffle(false, "entry-9995"); assertEquals(ids, queue.displayed)
  }  @Test fun previousUsesTenSecondBoundary() {
    assertFalse(QueueOrder.restartPrevious(9999)); assertFalse(QueueOrder.restartPrevious(10000))
    assertTrue(QueueOrder.restartPrevious(10001))
  }
  @Test fun playNextPrioritySurvivesShuffleTogglesAndIsConsumedOnlyWhenPlayed() {
    val queue = QueueOrder(); queue.replace(listOf("a", "b", "c", "d"))
    queue.insert(listOf("extra-1", "extra-2"), "c", true)
    queue.shuffle(true, "c", Random(2))
    assertEquals(listOf("c", "extra-1", "extra-2"), queue.displayed.take(3))
    queue.shuffle(false, "c"); assertEquals(listOf("a", "b", "c", "extra-1", "extra-2", "d"), queue.displayed)
    queue.consume("extra-1"); queue.shuffle(true, "extra-1", Random(3))
    assertEquals(listOf("extra-1", "extra-2"), queue.displayed.take(2))
    assertEquals(listOf("extra-2"), queue.priority)
  }
  @Test fun movingShuffledEntryDoesNotRewriteUnderlyingOrder() {
    val queue = QueueOrder(); queue.replace(listOf("a", "b", "c", "d")); queue.shuffle(true, "a", Random(4))
    val last = queue.displayed.last(); queue.move(last, -1)
    assertEquals(last, queue.displayed[2]); assertEquals(listOf("a", "b", "c", "d"), queue.base)
    queue.shuffle(false, "a"); assertEquals(queue.base, queue.displayed)
  }
  @Test fun editsPreserveOccurrenceIdentityAndRemovePendingPriority() {
    val queue = QueueOrder(); queue.replace(listOf("song-copy-1", "song-copy-2", "b"))
    queue.playNext("b", "song-copy-1"); assertEquals(listOf("song-copy-1", "b", "song-copy-2"), queue.base)
    queue.move("song-copy-1", 1); assertEquals(listOf("b", "song-copy-1", "song-copy-2"), queue.base)
    queue.remove(setOf("b")); assertTrue(queue.priority.isEmpty()); assertEquals(2, queue.base.size)
    assertThrows(IllegalArgumentException::class.java) { queue.insert(listOf("song-copy-2"), null, false) }
    assertThrows(IllegalArgumentException::class.java) { queue.restore(listOf("a"), listOf("a"), false, listOf("missing")) }
  }
  @Test fun lateCurrentAndPlayNextPreserveAdditionOrderAcrossShuffle() {
    val queue = QueueOrder(); queue.replace((0 until 350).map { "e-$it" })
    queue.insert(listOf("first"), "e-344", true); queue.insert(listOf("latest"), "e-344", true)
    queue.shuffle(true, "e-344", Random(5))
    assertEquals(listOf("e-344", "first", "latest"), queue.displayed.take(3))
    assertEquals(352, queue.displayed.distinct().size)
  }
  @Test fun consecutivePlayNextUsesFifoAcrossInsertMoveConsumeAndRecovery() {
    val queue = QueueOrder(); queue.replace(listOf("current", "rest", "existing"))
    queue.insert(listOf("a"), "current", true); queue.insert(listOf("b"), "current", true)
    queue.playNext("existing", "current"); queue.insert(listOf("c"), "current", true)
    assertEquals(listOf("current", "a", "b", "existing", "c", "rest"), queue.displayed)
    queue.consume("a"); queue.insert(listOf("d"), "a", true)
    assertEquals(listOf("current", "a", "b", "existing", "c", "d", "rest"), queue.base)
    queue.shuffle(true, "a", Random(1)); assertEquals(listOf("a", "b", "existing", "c", "d"), queue.displayed.take(5))
    val restored = QueueOrder().apply { restore(queue.base, queue.displayed, true, queue.priority) }
    restored.shuffle(false, "a"); assertEquals(queue.base, restored.displayed)
    restored.move("d", -4); assertEquals("d", restored.displayed[1]); assertFalse("d" in restored.priority)
  }
}
