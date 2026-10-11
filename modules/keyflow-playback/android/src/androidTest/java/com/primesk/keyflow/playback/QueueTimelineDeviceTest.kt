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
class QueueTimelineDeviceTest {
  @Test fun repeatedFullQueueShuffleKeepsLatePlayingEntryAndPhysicalTimeline() {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val file = File(instrumentation.targetContext.cacheDir, "shuffle-tone.wav")
    instrumentation.context.assets.open("audio/tone.wav").use { input -> file.outputStream().use(input::copyTo) }
    val ready = CountDownLatch(1)
    lateinit var player: ExoPlayer
    var buffering = 0; var watching = false
    instrumentation.runOnMainSync {
      player = ExoPlayer.Builder(instrumentation.targetContext).build(); player.volume = 0f
      player.addListener(object : Player.Listener {
        override fun onIsPlayingChanged(value: Boolean) { if(value) ready.countDown() }
        override fun onPlaybackStateChanged(state: Int) { if(watching && state == Player.STATE_BUFFERING) buffering++ }
      })
      player.setMediaItems((0 until 350).map { MediaItem.Builder().setMediaId("entry-$it").setUri(Uri.fromFile(file)).build() }, 344, 0)
      player.repeatMode = Player.REPEAT_MODE_ONE; player.prepare(); player.play()
    }
    try {
      assertTrue(ready.await(10, TimeUnit.SECONDS))
      instrumentation.runOnMainSync {
        watching = true
        val current = player.currentMediaItem!!.mediaId
        val physical = (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId }
        val order = QueueOrder().apply { replace(physical) }
        val position = player.currentPosition
        val started = System.nanoTime()
        repeat(100) { toggle ->
          order.shuffle(true, current, Random(toggle)); QueueTimeline.apply(player, order)
          assertEquals(current, order.displayed.first())
          assertEquals(350, order.displayed.distinct().size)
          assertEquals(344, player.shuffleOrder.getFirstIndex())
          val traversed = mutableListOf<Int>(); var index = player.shuffleOrder.getFirstIndex()
          while(index != -1) { traversed += index; index = player.shuffleOrder.getNextIndex(index) }
          assertEquals(order.displayed, traversed.map { physical[it] })
          order.shuffle(false, current); QueueTimeline.apply(player, order)
          assertEquals(current, player.currentMediaItem!!.mediaId); assertEquals(344, player.currentMediaItemIndex)
          assertEquals(Player.STATE_READY, player.playbackState); assertTrue(player.isPlaying)
          assertEquals(physical, (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId })
        }
        val elapsed = (System.nanoTime() - started) / 1_000_000
        assertTrue("Repeated toggles took ${elapsed}ms", elapsed < 2000)
        assertTrue(player.currentPosition >= position)
      }
      Thread.sleep(200)
      instrumentation.runOnMainSync { assertEquals(0, buffering) }
    } finally { instrumentation.runOnMainSync { player.release() }; file.delete() }
  }
}
