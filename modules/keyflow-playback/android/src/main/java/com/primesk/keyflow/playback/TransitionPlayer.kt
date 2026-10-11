package com.primesk.keyflow.playback

import android.content.*
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.media3.common.*
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/** Stable session player. Only the displayed deck forwards events and recovery state. */
@UnstableApi
internal class TransitionPlayer(
  private val context: Context,
  first: ExoPlayer,
  private val order: QueueOrder,
  private val seekCommand: (Int, Long, Int) -> Unit,
  private val shuffled: (Boolean) -> Unit,
  private val activated: (ExoPlayer, Boolean) -> Unit,
  sound: () -> SoundSettings = { SoundSettings() },
) : ForwardingSimpleBasePlayer(first) {
  var current: ExoPlayer = first; private set
  private val second = newDeck(context, sound)
  private val decks = listOf(first, second)
  private var tail: ExoPlayer? = null
  private var tailId: String? = null
  private var pending: Pending? = null
  val requestedId: String? get() = pending?.id
  private data class Pending(val deck: ExoPlayer, val id: String, val duration: Long, val curve: String,
    val playing: Boolean, val automatic: Boolean, val commit: () -> Unit)
  private var refreshRequest: Pair<String, Boolean>? = null
  private var preloaded: String? = null
  private var preloadRevision = -1L
  private var failedPreload: String? = null
  private var elapsed = 0L
  private var duration = 0L
  private var curve = "linear"
  private var lastTick = SystemClock.elapsedRealtime()
  private var changing = false
  private var released = false
  private var endTimer = false
  private var resumeOnFocus = false
  private var duck = 1f
  private val transport = TransportEnvelope()
  private var requestedPlaying: Boolean? = null
  private val handler = Handler(Looper.getMainLooper())
  var settings = TransitionSettings.load(context); private set
  private val focus = PlaybackFocus(context) { change ->
    when (change) {
      AudioManager.AUDIOFOCUS_GAIN -> { duck = 1f; if (resumeOnFocus) { resumeOnFocus = false; setBothPlaying(true) }; applyGains() }
      AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> { duck = .2f; applyGains() }
      AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> { resumeOnFocus = requestedPlaying ?: current.playWhenReady; pauseImmediately(false) }
      AudioManager.AUDIOFOCUS_LOSS -> { resumeOnFocus = false; pauseImmediately() }
    }
  }
  private val noisy = object : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) { if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) pauseImmediately() }
  }
  private val tick = object : Runnable {
    override fun run() {
      if (released) return
      val now = SystemClock.elapsedRealtime(); val delta = (now - lastTick).coerceIn(0, 100); lastTick = now
      val fadingTransport = transport.active
      if (fadingTransport && requestedPlaying == false && !current.playWhenReady) finishPauseFade()
      if (fadingTransport && current.isPlaying && transport.step(delta)) {
        if (transport.target == 0f) finishPauseFade()
        requestedPlaying = null; invalidateState()
      }
      if (fadingTransport) applyGains()
      val outgoing = tail
      if (outgoing != null) {
        if (!fadingTransport && current.isPlaying && outgoing.isPlaying) elapsed = (elapsed + delta).coerceAtMost(duration)
        if (elapsed >= duration || outgoing.playbackState == Player.STATE_ENDED || !outgoing.playWhenReady && current.playWhenReady) finishTail()
        else applyGains()
      } else if (pending == null && requestedPlaying != false && !fadingTransport) {
        preload()
        val spare = spare()
        if (settings.automatic && !endTimer && current.isPlaying && preloaded != null && spare.playbackState == Player.STATE_READY) {
          val overlap = TransitionMath.overlap(settings.automaticMs, validDuration(current), validDuration(spare))
          if (overlap > 0 && validDuration(current) - current.currentPosition in 1..overlap)
            begin(spare, preloaded!!, overlap, settings.automaticCurve, true, true) {}
        }
      }
      handler.postDelayed(this, if (transport.active) 16 else if (!current.playWhenReady && pending == null) 1000 else if (tail != null || pending != null) 16 else 50)
    }
  }
  init {
    decks.forEach { deck ->
      deck.addListener(object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) { if (isPlaying && pending?.deck === deck) commitPending() }
        override fun onPlaybackStateChanged(state: Int) {
          if (state == Player.STATE_READY && pending?.deck === deck) {
            if (pending!!.playing) deck.play() else commitPending()
          }
        }
        override fun onPlayerError(error: PlaybackException) {
          when {
            tail === deck -> finishTail()
            pending?.deck === deck -> {
              val request = pending!!; pending = null; finishTail(); current.pause()
              request.commit(); current = deck; deck.playWhenReady = request.playing; setPlayer(deck); activated(deck, request.automatic)
            }
            current !== deck -> { failedPreload = preloaded; preloaded = null; deck.stop() }
          }
        }
        override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
          if (deck === current && !changing) { cancelPending(); finishTail(); invalidatePreload() }
        }
      })
    }
    androidx.core.content.ContextCompat.registerReceiver(context, noisy, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY), androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED)
    applyGains(); handler.post(tick)
  }
  private fun spare() = if (current === decks[0]) decks[1] else decks[0]
  private fun validDuration(deck: ExoPlayer) = deck.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0
  private fun items() = (0 until current.mediaItemCount).map(current::getMediaItemAt)
  private fun nextId(): String? {
    val id = current.currentMediaItem?.mediaId ?: return null
    if (current.repeatMode == Player.REPEAT_MODE_ONE) return id
    val index = order.displayed.indexOf(id)
    return order.displayed.getOrNull(index + 1) ?: if (current.repeatMode == Player.REPEAT_MODE_ALL) order.displayed.firstOrNull() else null
  }
  private fun preload() {
    if (!settings.automatic || endTimer || !current.playWhenReady || current.playbackState != Player.STATE_READY) return
    val id = nextId() ?: return
    if (id == failedPreload || id == preloaded && preloadRevision == order.revision) return
    val queue = items(); val index = queue.indexOfFirst { it.mediaId == id }; if (index < 0) return
    val deck = spare(); changing = true
    try {
      deck.pause(); deck.volume = 0f; deck.setMediaItems(queue, index, 0)
      deck.repeatMode = Player.REPEAT_MODE_OFF; deck.setPauseAtEndOfMediaItems(true); deck.prepare()
      preloaded = id; preloadRevision = order.revision
    } finally { changing = false }
  }
  fun beforeQueueEdit() {
    pending?.let { if (!it.automatic && it.id in order.base) refreshRequest = it.id to it.playing }
    cancelPending()
  }
  fun invalidatePreload() {
    failedPreload = null; preloaded = null; preloadRevision = -1
    if (tail == null && pending == null) { spare().stop(); spare().clearMediaItems() }
    refreshRequest?.let { request ->
      refreshRequest = null
      val index = (0 until current.mediaItemCount).firstOrNull { current.getMediaItemAt(it).mediaId == request.first }
      if (index != null) change(index, request.second)
    }
  }
  fun update(values: Map<String, Any?>): Map<String, Any> {
    val previous = settings; settings = settings.updated(values); settings.save(context)
    if (previous.automatic && !settings.automatic || !previous.gapless && settings.gapless) { if (pending?.automatic == true) cancelPending(); finishTail() }
    invalidatePreload(); applyGains(); return settings.map()
  }
  fun setEndTimer(enabled: Boolean) {
    endTimer = enabled
    decks.forEach { it.setPauseAtEndOfMediaItems(enabled || it !== current) }
    if (enabled) { if (pending?.automatic == true) cancelPending(); finishTail(); invalidatePreload() }
  }
  fun transitionState(): Map<String, Any?> = mapOf("incomingEntryId" to if (tail != null) current.currentMediaItem?.mediaId else null,
    "outgoingEntryId" to tailId, "progress" to if (tail != null && duration > 0) elapsed.toDouble() / duration else 0.0,
    "kind" to if (tail == null) null else if (automaticOverlap) "automatic" else "manual")
  private var automaticOverlap = false
  fun tailRemoved(ids: Set<String>) { if (tailId in ids) finishTail() }
  fun change(index: Int, playing: Boolean = requestedPlaying ?: current.playWhenReady, fade: Boolean = true) {
    val queue = items(); require(index in queue.indices)
    changeQueue(queue, index, playing, fade) {}
  }
  fun changeQueue(queue: List<MediaItem>, index: Int, playing: Boolean, fade: Boolean = true, commit: () -> Unit) {
    transport.reset(); requestedPlaying = null
    cancelPending(); finishTail(); preloaded = null
    if (!fade || !settings.manual || settings.manualMs == 0L || !current.isPlaying || !playing || endTimer) {
      changing = true
      try {
        commit(); current.setMediaItems(queue, index, 0); QueueTimeline.apply(current, order)
        current.playbackParameters = PlaybackParameters.DEFAULT; current.prepare()
        if (playing && focus.acquire()) current.play() else current.pause()
      } finally { changing = false }
      applyGains(); return
    }
    val deck = spare(); changing = true
    try {
      deck.pause(); deck.volume = 0f; deck.setMediaItems(queue, index, 0); deck.repeatMode = Player.REPEAT_MODE_OFF
      deck.setPauseAtEndOfMediaItems(true)
      begin(deck, queue[index].mediaId, settings.manualMs, settings.manualCurve, playing, false, commit)
      deck.prepare()
    } finally { changing = false }
  }
  private fun begin(deck: ExoPlayer, id: String, ms: Long, selectedCurve: String, playing: Boolean, automatic: Boolean, commit: () -> Unit) {
    pending = Pending(deck, id, ms, selectedCurve, playing, automatic, commit)
    // If decoding takes longer than the remaining tail, wait at its end rather
    // than allowing its native playlist to advance and cancel the requested song.
    current.setPauseAtEndOfMediaItems(true)
    deck.volume = 0f
    if (deck.playbackState == Player.STATE_READY) { if (playing) deck.play() else commitPending() }
  }
  private fun commitPending() {
    val request = pending ?: return
    if (request.deck.currentMediaItem?.mediaId != request.id) return
    pending = null; val old = current; val repeat = old.repeatMode
    changing = true
    try {
      request.commit(); current = request.deck
      current.repeatMode = repeat; current.setPauseAtEndOfMediaItems(endTimer); QueueTimeline.apply(current, order)
      old.repeatMode = Player.REPEAT_MODE_OFF; old.setPauseAtEndOfMediaItems(true)
      tail = old; tailId = old.currentMediaItem?.mediaId
      duration = request.duration; curve = request.curve; elapsed = 0; automaticOverlap = request.automatic
      preloaded = null; lastTick = SystemClock.elapsedRealtime(); applyGains()
      setPlayer(current); activated(current, request.automatic)
    } finally { changing = false }
  }
  private fun cancelPending() { pending?.deck?.let { it.pause(); it.stop() }; pending = null; current.setPauseAtEndOfMediaItems(endTimer) }
  fun finishTail() { tail?.let { it.pause(); it.stop(); it.clearMediaItems() }; tail = null; tailId = null; applyGains() }
  private fun headroom() = if (tail != null && curve == "equal-power" || settings.automatic && settings.automaticCurve == "equal-power" || settings.manual && settings.manualCurve == "equal-power") .70710677f else 1f
  fun overlapHeadroomDb() = if (headroom() < 1f) 3.0103 else 0.0
  private fun applyGains() {
    val gain = headroom() * duck * transport.gain
    if (tail == null) current.volume = gain else {
      val (outgoing, incoming) = TransitionMath.gains(if (duration > 0) elapsed.toDouble() / duration else 1.0, curve)
      tail?.volume = outgoing * gain; current.volume = incoming * gain
    }
  }
  private fun setBothPlaying(playing: Boolean, wake: Boolean = true) {
    current.playWhenReady = playing; tail?.playWhenReady = playing
    pending?.let { pending = it.copy(playing = playing); it.deck.playWhenReady = playing && it.deck.playbackState == Player.STATE_READY }
    lastTick = SystemClock.elapsedRealtime()
    if (wake) { handler.removeCallbacks(tick); handler.post(tick) }
  }
  private fun focusRelease() { focus.release() }
  private fun finishPauseFade() {
    transport.reset(0f); setBothPlaying(false, false)
    current.setPauseAtEndOfMediaItems(endTimer); focus.release(); requestedPlaying = null
  }
  fun pauseImmediately(releaseFocus: Boolean = true) {
    transport.reset(); requestedPlaying = null; setBothPlaying(false)
    if (releaseFocus) { resumeOnFocus = false; focus.release() }
    applyGains(); invalidateState()
  }
  fun seekCurrent(position: Long) { cancelPending(); finishTail(); invalidatePreload(); current.seekTo(position) }
  override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
    resumeOnFocus = false
    if (!settings.manual || settings.manualMs == 0L || current.currentMediaItem == null) {
      transport.reset(); requestedPlaying = null
      if (playWhenReady) { if (focus.acquire()) setBothPlaying(true) } else pauseImmediately()
      applyGains(); return Futures.immediateVoidFuture()
    }
    if (playWhenReady) {
      if (!focus.acquire()) return Futures.immediateVoidFuture()
      if (!current.playWhenReady && !transport.active) transport.reset(0f)
      requestedPlaying = true
      current.setPauseAtEndOfMediaItems(endTimer || pending != null)
      transport.begin(true, settings.manualMs, settings.manualCurve); applyGains(); setBothPlaying(true)
    } else if (current.isPlaying) {
      requestedPlaying = false
      transport.begin(false, settings.manualMs, settings.manualCurve)
      // A waiting song must not start audibly after a pause request.
      cancelPending()
      current.setPauseAtEndOfMediaItems(true)
      handler.removeCallbacks(tick); lastTick = SystemClock.elapsedRealtime(); handler.post(tick)
    } else pauseImmediately()
    return Futures.immediateVoidFuture()
  }
  override fun handleSeek(index: Int, position: Long, command: Int): ListenableFuture<*> {
    seekCommand(index, position, command); return Futures.immediateVoidFuture()
  }
  override fun handleSetShuffleModeEnabled(enabled: Boolean): ListenableFuture<*> { shuffled(enabled); return Futures.immediateVoidFuture() }
  override fun getState(): State {
    val state = super.getState()
    val builder = state.buildUpon().setAvailableCommands(state.availableCommands.buildUpon().remove(Player.COMMAND_SEEK_BACK).remove(Player.COMMAND_SEEK_FORWARD).build()).setVolume(1f)
    requestedPlaying?.let { builder.setPlayWhenReady(it, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST) }
    return builder.build()
  }
  override fun handleStop(): ListenableFuture<*> { transport.reset(); requestedPlaying = null; cancelPending(); finishTail(); decks.forEach { it.stop() }; focus.release(); return Futures.immediateVoidFuture() }
  override fun handleRelease(): ListenableFuture<*> {
    released = true; handler.removeCallbacksAndMessages(null); context.unregisterReceiver(noisy); focus.release()
    decks.forEach { it.release() }; return Futures.immediateVoidFuture()
  }
  companion object {
    fun newDeck(context: Context, sound: () -> SoundSettings = { SoundSettings() }): ExoPlayer {
      val processor = SoundAudioProcessor(sound)
      val renderers = object : DefaultRenderersFactory(context) {
        override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean): AudioSink =
          DefaultAudioSink.Builder(context).setEnableFloatOutput(false).setEnableAudioTrackPlaybackParams(false)
            .setAudioProcessors(arrayOf(processor)).build()
      }
      return ExoPlayer.Builder(context, renderers).build().apply {
      trackSelectionParameters = trackSelectionParameters.buildUpon().setAudioOffloadPreferences(
        TrackSelectionParameters.AudioOffloadPreferences.Builder().setAudioOffloadMode(TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED).build()).build()
      setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), false)
      setHandleAudioBecomingNoisy(false); setWakeMode(C.WAKE_MODE_LOCAL)
      }
    }
  }
}
