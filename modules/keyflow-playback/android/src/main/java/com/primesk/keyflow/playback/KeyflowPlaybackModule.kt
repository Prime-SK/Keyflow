package com.primesk.keyflow.playback

import android.content.ComponentName
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import expo.modules.kotlin.activityresult.AppContextActivityResultLauncher
import expo.modules.kotlin.exception.CodedException
import expo.modules.kotlin.functions.Coroutine
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@UnstableApi
class KeyflowPlaybackModule : Module() {
  private lateinit var picker: AppContextActivityResultLauncher<String, AudioSelection?>
  private var picking = false
  private var controller: MediaController? = null
  private var connection: ListenableFuture<MediaController>? = null
  private var observing = false
  private var foreground = true
  private var destroyed = false
  private var revision = 0L
  private val handler = Handler(Looper.getMainLooper())
  private val mainExecutor = Executor { command -> handler.post(command) }
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

  private val listener = object : Player.Listener {
    override fun onEvents(player: Player, events: Player.Events) { publishState() }
    override fun onPlayerError(error: PlaybackException) {
      if (!destroyed && observing) sendEvent("onPlaybackError", playbackError(error))
    }
  }
  private val ticker = object : Runnable {
    override fun run() {
      if (!observing || !foreground || destroyed) return
      publishState()
      handler.postDelayed(this, 250)
    }
  }

  override fun definition() = ModuleDefinition {
    Name("KeyflowPlayback")
    Events("onPlaybackState", "onPlaybackError")

    AsyncFunction("getThemePreference") {
      context().getSharedPreferences("keyflow-settings", 0).getString("theme", "system") ?: "system"
    }
    AsyncFunction("setThemePreference") { preference: String ->
      require(preference in listOf("system", "light", "dark")) { "Choose system, light, or dark appearance." }
      context().getSharedPreferences("keyflow-settings", 0).edit().putString("theme", preference).apply()
    }

    RegisterActivityContracts {
      picker = registerForActivityResult(AudioFileContract()) { _, _ ->
        // A killed activity has no awaiting JS call. Do not unexpectedly start music.
        picking = false
      }
    }

    AsyncFunction("pickAudio") Coroutine { ->
      withContext(Dispatchers.Main) {
        if (picking) throw CodedException("ERR_PICKER_BUSY", "A file picker is already open.", null)
        if (!::picker.isInitialized) throw CodedException("ERR_PICKER_UNAVAILABLE", "The file picker is not ready. Try again.", null)
        picking = true
        try {
          val selection = picker.launch("audio")
          if (selection == null) null else withContext(Dispatchers.IO) { AudioMetadata.read(context(), selection) }
        } finally { picking = false }
      }
    }

    AsyncFunction("getState") Coroutine { -> withContext(Dispatchers.Main) { state(getController()) } }

    AsyncFunction("openAudio") Coroutine { audio: Map<String, Any?> ->
      withContext(Dispatchers.Main) {
        val uri = Uri.parse(audio["uri"] as? String ?: throw IllegalArgumentException("Choose a local audio file first."))
        require(uri.scheme == "content" || uri.scheme == "file") { "Only local audio files are supported." }
        val extras = Bundle().apply {
          putString("fileName", audio["fileName"] as? String)
          putString("mimeType", audio["mimeType"] as? String)
          putBoolean("persistedPermission", audio["persistedPermission"] as? Boolean ?: false)
        }
        val metadata = MediaMetadata.Builder().setTitle(audio["title"] as? String ?: "Local audio")
          .setArtist(audio["artist"] as? String).setExtras(extras)
          .setArtworkUri((audio["artworkUri"] as? String)?.let(Uri::parse)).build()
        val item = MediaItem.Builder().setMediaId(uri.toString()).setUri(uri)
          .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(uri).build())
          .setMediaMetadata(metadata).build()
        val player = getController()
        player.playbackParameters = PlaybackParameters.DEFAULT
        player.setMediaItem(item)
        player.prepare()
        player.play()
        state(player)
      }
    }

    AsyncFunction("play") Coroutine { ->
      withContext(Dispatchers.Main) {
        val player = getController()
        require(player.mediaItemCount > 0) { "Choose a song first." }
        if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
        if (player.playbackState == Player.STATE_IDLE) player.prepare()
        player.play()
        state(player)
      }
    }
    AsyncFunction("pause") Coroutine { -> withContext(Dispatchers.Main) {
      val player = getController(); player.pause(); state(player)
    } }
    AsyncFunction("seekTo") Coroutine { positionMs: Double -> withContext(Dispatchers.Main) {
      val player = getController()
      require(player.isCurrentMediaItemSeekable) { "This file does not support seeking." }
      player.seekTo(PlaybackMath.seekPosition(positionMs, player.duration))
      state(player)
    } }
    // Development checks only in Phase 1; the musician console arrives in Phase 6.
    AsyncFunction("setPlaybackParameters") Coroutine { speed: Double, semitones: Double, cents: Double ->
      val rate = PlaybackMath.speedFactor(speed)
      val pitch = PlaybackMath.pitchFactor(semitones, cents)
      withContext(Dispatchers.Main) {
        val player = getController()
        player.playbackParameters = PlaybackParameters(rate, pitch)
        state(player)
      }
    }

    OnStartObserving {
      handler.post {
        observing = true
        scope.launch {
          try { getController(); restartTicker() }
          catch (error: Exception) {
            if (!destroyed && observing) sendEvent("onPlaybackError",
              mapOf("code" to "ERR_CONNECTION", "message" to "Couldn't connect to the music player. Try again."))
          }
        }
      }
    }
    OnStopObserving { handler.post { observing = false; handler.removeCallbacks(ticker) } }
    OnActivityEntersForeground { handler.post { foreground = true; restartTicker() } }
    OnActivityEntersBackground { handler.post { foreground = false; handler.removeCallbacks(ticker) } }
    OnDestroy {
      handler.post {
        destroyed = true
        observing = false
        handler.removeCallbacks(ticker)
        scope.cancel()
        controller?.removeListener(listener)
        connection?.let(MediaController::releaseFuture)
        controller = null
        connection = null
        // Disconnecting JS must not stop the service's player.
      }
    }
  }

  private fun context() = appContext.reactContext?.applicationContext
    ?: throw CodedException("ERR_CONTEXT", "The app is not ready. Try again.", null)

  private suspend fun getController(): MediaController {
    check(!destroyed) { "The playback connection was closed." }
    controller?.let { if (it.isConnected) return it }
    val future = connection ?: MediaController.Builder(context(),
      SessionToken(context(), ComponentName(context(), KeyflowPlaybackService::class.java)))
      .setApplicationLooper(Looper.getMainLooper())
      .setListener(object : MediaController.Listener {
        override fun onDisconnected(disconnected: MediaController) {
          disconnected.removeListener(listener)
          controller = null
          connection = null
          if (!destroyed && observing) sendEvent("onPlaybackError",
            mapOf("code" to "ERR_DISCONNECTED", "message" to "The music player stopped. Choose a song to reconnect."))
        }
      }).buildAsync().also { connection = it }
    try {
      val connected = withTimeout(10000) {
        suspendCancellableCoroutine<MediaController> { continuation ->
          future.addListener({
            if (continuation.isActive) {
              try { continuation.resume(future.get()) }
              catch (error: Exception) { continuation.resumeWithException(error) }
            }
          }, mainExecutor)
        }
      }
      if (controller !== connected) {
        controller = connected
        connected.addListener(listener)
      }
      return connected
    } catch (error: Exception) {
      if (connection === future) {
        connection = null
        MediaController.releaseFuture(future)
      }
      throw CodedException("ERR_CONNECTION", "Couldn't connect to the music player. Try again.", error)
    }
  }

  private fun restartTicker() {
    handler.removeCallbacks(ticker)
    if (observing && foreground && !destroyed) handler.post(ticker)
  }

  private fun publishState() {
    if (!destroyed && observing && foreground) controller?.let { sendEvent("onPlaybackState", state(it)) }
  }

  private fun state(player: MediaController): Map<String, Any?> {
    val duration = player.duration.takeIf { it != C.TIME_UNSET && it >= 0 } ?: 0L
    val item = player.currentMediaItem
    val metadata = player.mediaMetadata
    val track = item?.let {
      mapOf("uri" to it.mediaId, "title" to (metadata.title?.toString() ?: "Local audio"),
        "artist" to metadata.artist?.toString(), "fileName" to (metadata.extras?.getString("fileName") ?: "Local audio"),
        "artworkUri" to metadata.artworkUri?.toString(), "durationMs" to duration.toDouble(),
        "mimeType" to metadata.extras?.getString("mimeType"),
        "persistedPermission" to (metadata.extras?.getBoolean("persistedPermission") ?: false))
    }
    val status = when {
      player.playerError != null -> "error"
      item == null -> "empty"
      player.playbackState == Player.STATE_BUFFERING -> "loading"
      player.playbackState == Player.STATE_ENDED -> "ended"
      player.isPlaying -> "playing"
      else -> "paused"
    }
    return mapOf("revision" to (++revision).toDouble(), "track" to track, "status" to status,
      "isPlaying" to player.isPlaying, "playWhenReady" to player.playWhenReady,
      "positionMs" to player.currentPosition.coerceAtLeast(0).toDouble(), "durationMs" to duration.toDouble(),
      "bufferedPositionMs" to player.bufferedPosition.coerceAtLeast(0).toDouble(),
      "canSeek" to (item != null && player.isCurrentMediaItemSeekable && duration > 0),
      "speed" to player.playbackParameters.speed.toDouble(), "pitch" to player.playbackParameters.pitch.toDouble(),
      "error" to player.playerError?.let(::playbackError))
  }

  private fun playbackError(error: PlaybackException): Map<String, String> {
    val message = when (error.errorCode) {
      PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> "This file is no longer available. Choose it again."
      PlaybackException.ERROR_CODE_IO_NO_PERMISSION -> "Access to this file was lost. Choose it again to grant access."
      PlaybackException.ERROR_CODE_DECODING_FAILED,
      PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
      PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED -> "This audio file is damaged or uses an unsupported format."
      else -> "Couldn't play this file. Choose it again or try another song."
    }
    return mapOf("code" to error.errorCodeName, "message" to message)
  }
}
