package com.primesk.keyflow.playback

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
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

/** Real device decoder checks; fixtures are test-only, output is muted. */
@UnstableApi
@RunWith(AndroidJUnit4::class)
class DecoderDeviceTest {
  private val instrumentation = InstrumentationRegistry.getInstrumentation()

  @Test fun supportedFormatsReachEndAndCanReplay() {
    for (extension in listOf("mp3", "m4a", "flac", "wav", "ogg")) {
      val file = fixture("tone.$extension")
      lateinit var player: ExoPlayer
      var ended = CountDownLatch(1)
      var failure: PlaybackException? = null
      instrumentation.runOnMainSync {
        player = ExoPlayer.Builder(instrumentation.targetContext).build()
        player.volume = 0f
        player.addListener(object : Player.Listener {
          override fun onPlaybackStateChanged(state: Int) {
            if (state == Player.STATE_ENDED) ended.countDown()
          }
          override fun onPlayerError(error: PlaybackException) { failure = error; ended.countDown() }
        })
        player.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
        player.prepare()
        player.play()
      }
      try {
        assertTrue("$extension did not finish", ended.await(15, TimeUnit.SECONDS))
        assertNull("$extension failed: $failure", failure)
        ended = CountDownLatch(1)
        instrumentation.runOnMainSync {
          assertEquals(Player.STATE_ENDED, player.playbackState)
          assertTrue(player.duration > 0)
          player.seekTo(0)
          player.play()
        }
        assertTrue("$extension did not replay", ended.await(15, TimeUnit.SECONDS))
        assertNull(failure)
      } finally { instrumentation.runOnMainSync { player.release() }; file.delete() }
    }
  }

  @Test fun corruptAudioReportsAnError() {
    val file = fixture("corrupt.mp3")
    val errored = CountDownLatch(1)
    lateinit var player: ExoPlayer
    instrumentation.runOnMainSync {
      player = ExoPlayer.Builder(instrumentation.targetContext).build()
      player.volume = 0f
      player.addListener(object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) { errored.countDown() }
      })
      player.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
      player.prepare()
      player.play()
    }
    try { assertTrue("Expected decoder error", errored.await(15, TimeUnit.SECONDS)) }
    finally { instrumentation.runOnMainSync { player.release() }; file.delete() }
  }

  private fun fixture(name: String): File = File(instrumentation.targetContext.cacheDir, name).also { file ->
    instrumentation.context.assets.open("audio/$name").use { input -> file.outputStream().use(input::copyTo) }
  }
}
