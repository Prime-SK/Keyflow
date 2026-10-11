package com.primesk.keyflow.playback

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer

/** Service-owned, one-shot timer. Never restored after process termination. */
@UnstableApi
internal class SleepTimer(private val player: Player, private val pauseAtEnd: (Boolean) -> Unit, private val pauseNow: () -> Unit = { player.pause() }, private val expired: () -> Unit) : Player.Listener {
  private val handler = Handler(Looper.getMainLooper())
  private var deadline = 0L
  private var entry: String? = null
  private val tick = object : Runnable {
    override fun run() {
      if (deadline == 0L) return
      val remaining = deadline - SystemClock.elapsedRealtime()
      if (remaining <= 0) finish() else handler.postDelayed(this, remaining.coerceAtMost(1000))
    }
  }
  init { player.addListener(this) }
  fun set(mode: String, durationMs: Long) {
    require(mode in listOf("off", "timed", "end")) { "Unknown sleep timer mode." }
    if (mode != "off") require(player.currentMediaItem != null) { "Choose a song first." }
    if (mode == "timed") require(durationMs in 1000..86_400_000) { "Choose a timer between one second and 24 hours." }
    if (mode == "end") require(player.playbackState != Player.STATE_ENDED) { "Replay the song before setting an end-of-song timer." }
    cancel()
    if (mode == "timed") {
      deadline = SystemClock.elapsedRealtime() + durationMs
      handler.post(tick)
    } else if (mode == "end") {
      entry = player.currentMediaItem!!.mediaId
      pauseAtEnd(true)
    }
  }
  fun state(): Map<String, Any> = mapOf(
    "mode" to if (deadline != 0L) "timed" else if (entry != null) "end" else "off",
    "remainingMs" to (if (deadline == 0L) 0L else (deadline - SystemClock.elapsedRealtime()).coerceAtLeast(0)).toDouble())
  override fun onPlayWhenReadyChanged(value: Boolean, reason: Int) {
    if (!value && entry != null && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM) finish()
  }
  override fun onPlaybackStateChanged(state: Int) {
    if (state == Player.STATE_ENDED && entry != null) finish()
  }
  override fun onMediaItemTransition(item: androidx.media3.common.MediaItem?, reason: Int) {
    // Skipping/replacing the selected song cancels its end timer. Timed timers continue.
    if (entry != null && item?.mediaId != entry) cancel()
  }
  override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
    if (entry != null) finish()
  }
  private fun finish() { cancel(); pauseNow(); expired() }
  fun cancel() {
    deadline = 0; entry = null; handler.removeCallbacks(tick)
    pauseAtEnd(false)
  }
  fun release() { cancel(); player.removeListener(this) }
}
