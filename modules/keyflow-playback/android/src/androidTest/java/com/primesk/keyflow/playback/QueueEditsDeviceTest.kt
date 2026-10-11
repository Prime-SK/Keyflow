package com.primesk.keyflow.playback

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.random.Random

@UnstableApi
@RunWith(AndroidJUnit4::class)
class QueueEditsDeviceTest {
  @Test fun insertionReorderingRemovalAndShuffledClearingPreservePlayingPeriod() {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val file = File(instrumentation.targetContext.cacheDir, "queue-edits.wav")
    instrumentation.context.assets.open("audio/tone.wav").use { input -> file.outputStream().use(input::copyTo) }
    val ready = CountDownLatch(1); lateinit var player: ExoPlayer
    val order = QueueOrder(); var watching = false; var buffering = 0
    fun item(id: String) = MediaItem.Builder().setMediaId(id).setUri(Uri.fromFile(file)).build()
    instrumentation.runOnMainSync {
      player = ExoPlayer.Builder(instrumentation.targetContext).build(); player.volume = 0f
      player.addListener(object : Player.Listener {
        override fun onIsPlayingChanged(value: Boolean) { if (value) ready.countDown() }
        override fun onPlaybackStateChanged(state: Int) { if (watching && state == Player.STATE_BUFFERING) buffering++ }
      })
      order.replace((0 until 350).map { "e-$it" }); player.setMediaItems(order.base.map(::item), 344, 0)
      player.repeatMode = Player.REPEAT_MODE_ONE; player.prepare(); player.play()
    }
    try {
      assertTrue(ready.await(10, TimeUnit.SECONDS))
      instrumentation.runOnMainSync {
        watching = true; val current = player.currentMediaItem!!.mediaId; val position = player.currentPosition
        val at = order.insert(listOf("duplicate-1", "duplicate-2"), current, true)
        player.addMediaItems(at, listOf(item("duplicate-1"), item("duplicate-2")))
        QueueTimeline.apply(player, order)
        order.shuffle(true, current, Random(5)); QueueTimeline.apply(player, order)
        assertEquals(listOf(current, "duplicate-1", "duplicate-2"), order.displayed.take(3))
        val appendedAt = order.insert(listOf("appended"), current, false)
        player.addMediaItem(appendedAt, item("appended")); QueueTimeline.apply(player, order)
        assertEquals("appended", order.displayed.last())
        order.shuffle(false, current); QueueTimeline.apply(player, order)
        val old = player.currentMediaItemIndex
        order.move(current, -1); player.moveMediaItem(old, order.base.indexOf(current)); QueueTimeline.apply(player, order)
        val remove = order.base.indexOf("e-0"); order.remove(setOf("e-0")); player.removeMediaItem(remove); QueueTimeline.apply(player, order)
        assertEquals(current, player.currentMediaItem!!.mediaId); assertTrue(player.currentPosition >= position)
        assertEquals(352, player.mediaItemCount); assertEquals(Player.STATE_READY, player.playbackState); assertTrue(player.isPlaying)
        order.shuffle(true, current, Random(8)); QueueTimeline.apply(player, order)
        val physicalCurrent = player.currentMediaItemIndex
        val after = (physicalCurrent + 1 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId }.toSet()
        order.remove(after); player.removeMediaItems(physicalCurrent + 1, player.mediaItemCount); QueueTimeline.apply(player, order)
        val before = (0 until physicalCurrent).map { player.getMediaItemAt(it).mediaId }.toSet()
        order.remove(before); player.removeMediaItems(0, physicalCurrent); QueueTimeline.apply(player, order)
        assertEquals(listOf(current), order.displayed); assertEquals(1, player.mediaItemCount)
        assertEquals(current, player.currentMediaItem!!.mediaId); assertEquals(Player.STATE_READY, player.playbackState); assertTrue(player.isPlaying)
      }
      Thread.sleep(200)
      instrumentation.runOnMainSync { assertEquals(0, buffering) }
    } finally { instrumentation.runOnMainSync { player.release() }; file.delete() }
  }
}
