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

@UnstableApi
@RunWith(AndroidJUnit4::class)
class SleepTimerDeviceTest {
  private fun withPlayer(test: (ExoPlayer, SleepTimer, CountDownLatch) -> Unit) {
    val i = InstrumentationRegistry.getInstrumentation()
    val file = File(i.targetContext.cacheDir, "sleep-tone.wav")
    i.context.assets.open("audio/tone.wav").use { input -> file.outputStream().use(input::copyTo) }
    val ready = CountDownLatch(1); val expired = CountDownLatch(1)
    lateinit var player: ExoPlayer; lateinit var timer: SleepTimer
    i.runOnMainSync {
      player = ExoPlayer.Builder(i.targetContext).build(); player.volume = 0f
      timer = SleepTimer(player, player::setPauseAtEndOfMediaItems) { expired.countDown() }
      player.addListener(object : Player.Listener { override fun onIsPlayingChanged(value: Boolean) { if(value) ready.countDown() } })
      player.setMediaItems((0..1).map { MediaItem.Builder().setMediaId("sleep-$it").setUri(Uri.fromFile(file)).build() })
      player.repeatMode = Player.REPEAT_MODE_ONE; player.prepare(); player.play()
    }
    try { assertTrue(ready.await(10, TimeUnit.SECONDS)); test(player, timer, expired) }
    finally { i.runOnMainSync { timer.release(); player.release() }; file.delete() }
  }
  @Test fun timedExpiryPausesAndCanResumeWithoutASecondExpiry() = withPlayer { player, timer, expired ->
    val i = InstrumentationRegistry.getInstrumentation()
    i.runOnMainSync { timer.set("timed", 1000) }
    assertTrue(expired.await(5, TimeUnit.SECONDS))
    i.runOnMainSync { assertFalse(player.playWhenReady); assertEquals("off", timer.state()["mode"]); player.play() }
    Thread.sleep(1200)
    i.runOnMainSync { assertTrue(player.playWhenReady) }
  }
  @Test fun replacementCancellationAndInvalidInputsAreSafe() = withPlayer { player, timer, expired ->
    val i = InstrumentationRegistry.getInstrumentation()
    i.runOnMainSync {
      timer.set("timed", 1000); timer.set("timed", 5000)
      assertThrows(IllegalArgumentException::class.java) { timer.set("timed", -1) }
      assertEquals("timed", timer.state()["mode"])
      timer.cancel(); timer.set("end", 0); player.seekTo(1, 0)
    }
    Thread.sleep(1400)
    i.runOnMainSync { assertEquals("off", timer.state()["mode"]); assertTrue(player.playWhenReady) }
    assertEquals(1L, expired.count)
  }
  @Test fun endTimerOverridesRepeatOneAndRepeatAllWithoutPlayingTheNextSong() {
    for (repeat in listOf(Player.REPEAT_MODE_ONE, Player.REPEAT_MODE_ALL, Player.REPEAT_MODE_OFF)) withPlayer { player, timer, expired ->
      val i = InstrumentationRegistry.getInstrumentation()
      i.runOnMainSync {
        player.repeatMode = repeat; timer.set("end", 0)
        player.seekTo((player.duration - 180).coerceAtLeast(0))
      }
      assertTrue("End timer repeat=$repeat", expired.await(5, TimeUnit.SECONDS))
      i.runOnMainSync {
        assertFalse(player.playWhenReady); assertEquals("sleep-0", player.currentMediaItem!!.mediaId)
        assertEquals(repeat, player.repeatMode); assertEquals("off", timer.state()["mode"])
      }
    }
  }
}
