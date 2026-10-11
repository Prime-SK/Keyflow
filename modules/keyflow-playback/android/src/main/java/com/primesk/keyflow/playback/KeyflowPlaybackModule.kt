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
import com.primesk.keyflow.library.LibraryRepository
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
    AsyncFunction("getArtworkTint") Coroutine { uri: String ->
      withContext(Dispatchers.IO) { ArtworkTint.read(context(), uri) }
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

    AsyncFunction("openAudio") Coroutine { audio: Map<String, Any?> -> withContext(Dispatchers.Main) {
      val player = getPlayer(); val service = service(); service.ready.await()
      service.replace(listOf(QueueStorage.item(audio)), 0); state(player)
    } }
    AsyncFunction("openLibraryQueue") Coroutine { query: Map<String, Any?>, selected: String ->
      val queue = withContext(Dispatchers.IO) { QueueStorage.libraryQueue(context(), query, selected) }
      withContext(Dispatchers.Main) { val player = getPlayer(); val service = service(); service.ready.await()
        service.replace(queue.first, queue.second); state(player) }
    }
    AsyncFunction("next") Coroutine { -> withContext(Dispatchers.Main) { val player = getPlayer(); service().next(); state(player) } }
    AsyncFunction("openCollection") Coroutine { kind: String, id: String?, selected: String ->
      val queue = withContext(Dispatchers.IO) { QueueStorage.collectionQueue(context(), kind, id, selected) }
      withContext(Dispatchers.Main) { val player = getPlayer(); service().replace(queue.first, queue.second); state(player) }
    }
    AsyncFunction("saveQueueAsPlaylist") Coroutine { name: String ->
      val entries = withContext(Dispatchers.Main) { getPlayer(); service().displayedAudio() }
      withContext(Dispatchers.IO) { com.primesk.keyflow.library.CollectionsRepository(context()).saveQueue(name, entries).id }
    }
    AsyncFunction("previous") Coroutine { -> withContext(Dispatchers.Main) { val player = getPlayer(); service().previous(); state(player) } }
    AsyncFunction("setShuffle") Coroutine { enabled: Boolean -> withContext(Dispatchers.Main) { val player = getPlayer(); service().shuffle(enabled); state(player) } }
    AsyncFunction("setSleepTimer") Coroutine { mode: String, durationMs: Double -> withContext(Dispatchers.Main) {
      require(durationMs.isFinite())
      val player = getPlayer(); service().setSleepTimer(mode, durationMs.toLong()); state(player)
    } }
    AsyncFunction("setRepeat") Coroutine { mode: String -> withContext(Dispatchers.Main) { val player = getPlayer(); service().repeat(mode); state(player) } }
    AsyncFunction("queryQueue") Coroutine { offset: Int, limit: Int -> withContext(Dispatchers.Main) { getController(); service().queuePage(offset, limit) } }
    AsyncFunction("jumpQueueEntry") Coroutine { id: String -> withContext(Dispatchers.Main) { val player = getPlayer(); service().jump(id); state(player) } }
    AsyncFunction("moveQueueEntry") Coroutine { id: String, delta: Int -> withContext(Dispatchers.Main) { val player = getPlayer(); service().move(id, delta); state(player) } }
    AsyncFunction("playNextQueueEntry") Coroutine { id: String -> withContext(Dispatchers.Main) { val player = getPlayer(); service().playNext(id); state(player) } }
    AsyncFunction("removeQueueEntry") Coroutine { id: String -> withContext(Dispatchers.Main) { val player = getPlayer(); service().remove(id); state(player) } }
    AsyncFunction("clearUpcoming") Coroutine { -> withContext(Dispatchers.Main) { val player = getPlayer(); service().clearUpcoming(); state(player) } }
    AsyncFunction("insertLibraryTrack") Coroutine { id: String, playNext: Boolean ->
      val item = withContext(Dispatchers.IO) { QueueStorage.item(LibraryRepository(context()).resolve(id), trackId = id) }
      withContext(Dispatchers.Main) { val player = getPlayer(); service().insert(listOf(item), playNext); state(player) }
    }
    AsyncFunction("play") Coroutine { ->
      withContext(Dispatchers.Main) {
        val player = getPlayer()
        require(player.mediaItemCount > 0) { "Choose a song first." }
        if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
        if (player.playbackState == Player.STATE_IDLE) player.prepare()
        player.play()
        state(player)
      }
    }
    AsyncFunction("pause") Coroutine { -> withContext(Dispatchers.Main) {
      val player = getPlayer(); player.pause(); state(player)
    } }
    AsyncFunction("seekTo") Coroutine { positionMs: Double -> withContext(Dispatchers.Main) {
      val player = getPlayer()
      require(player.isCurrentMediaItemSeekable) { "This file does not support seeking." }
      player.seekTo(PlaybackMath.seekPosition(positionMs, player.duration))
      state(player)
    } }
    // Development checks only in Phase 1; the musician console arrives in Phase 6.
    AsyncFunction("setPlaybackParameters") Coroutine { speed: Double, semitones: Double, cents: Double ->
      val rate = PlaybackMath.speedFactor(speed)
      val pitch = PlaybackMath.pitchFactor(semitones, cents)
      withContext(Dispatchers.Main) {
        val player = getPlayer()
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

  private suspend fun getPlayer(): Player { getController(); return service().audioPlayer }

  private fun service() = KeyflowPlaybackService.active ?: error("The music service is unavailable.")

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
      service().ready.await()
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

  private fun state(controller: Player): Map<String, Any?> {
    val player = KeyflowPlaybackService.active?.audioPlayer ?: controller
    val duration = player.duration.takeIf { it != C.TIME_UNSET && it >= 0 } ?: 0L
    val item = player.currentMediaItem
    val metadata = item?.mediaMetadata ?: player.mediaMetadata
    val track = item?.let {
      mapOf("uri" to (it.requestMetadata.mediaUri ?: it.localConfiguration?.uri).toString(), "title" to (metadata.title?.toString() ?: "Local audio"),
        "artist" to metadata.artist?.toString(), "fileName" to (metadata.extras?.getString("fileName") ?: "Local audio"),
        "artworkUri" to metadata.artworkUri?.toString(), "durationMs" to duration.toDouble(),
        "mimeType" to metadata.extras?.getString("mimeType"), "trackId" to metadata.extras?.getString("trackId"),
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
      "sleepTimer" to (KeyflowPlaybackService.active?.sleepTimer?.state() ?: mapOf("mode" to "off", "remainingMs" to 0)),
      "error" to player.playerError?.let(::playbackError)) + (KeyflowPlaybackService.active?.queueState() ?: emptyMap())
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
