package com.primesk.keyflow.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.media3.common.*
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.primesk.keyflow.library.LibraryDatabase
import com.primesk.keyflow.library.PlaybackRecovery
import com.primesk.keyflow.library.LibraryRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel

@UnstableApi
class KeyflowPlaybackService : MediaSessionService() {
  companion object { internal var active: KeyflowPlaybackService? = null; private set }
  private var session: MediaSession? = null
  private lateinit var player: ExoPlayer
  internal lateinit var sleepTimer: SleepTimer
  internal val audioPlayer: Player get() = player
  internal val order = QueueOrder()
  private var physicalIndices: Map<String, Int> = emptyMap()
  internal val ready = CompletableDeferred<Unit>()
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private data class Save(val items: List<MediaItem>, val order: QueueOrder, val current: String?, val position: Long, val repeat: Int, val ended: Boolean)
  private val saves = Channel<Save>(Channel.CONFLATED)
  private var cachedRevision = -1L
  private var cachedItems: List<MediaItem> = emptyList()
  private var cachedOrder = QueueOrder()
  private val handler = Handler(Looper.getMainLooper())
  private var restoring = true
  private var stopping = false
  private var artworkJob: Job? = null
  private var artworkEntry: String? = null
  private var recordedEntry: String? = null
  private val history = Channel<Pair<Map<String, Any?>, String?>>(Channel.UNLIMITED)
  private val failed = mutableSetOf<String>()
  internal var notice: String? = null; private set
  private val checkpoint = object : Runnable {
    override fun run() { persist(); handler.postDelayed(this, 5000) }
  }
  override fun onCreate() {
    super.onCreate(); active = this
    player = ExoPlayer.Builder(this).build().apply {
      setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
      setHandleAudioBecomingNoisy(true); setWakeMode(C.WAKE_MODE_LOCAL)
    }
    sleepTimer = SleepTimer(player) { notice = "Sleep timer finished."; persist() }
    player.addListener(object : Player.Listener {
      override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) recordedEntry = null
        order.consume(mediaItem?.mediaId)
        player.playbackParameters = PlaybackParameters.DEFAULT
        loadCurrentArtwork(mediaItem)
        // Keep skip explanations visible after advancing to a playable entry.
        persist()
      }
      override fun onEvents(p: Player, events: Player.Events) {
        if (player.isPlaying && player.currentMediaItem?.mediaId != recordedEntry) {
          player.currentMediaItem?.let { item ->
            recordedEntry = item.mediaId
            history.trySend(QueueStorage.audio(item) to item.mediaMetadata.extras?.getString("trackId"))
          }
        }
        if (events.contains(Player.EVENT_IS_PLAYING_CHANGED) || events.contains(Player.EVENT_POSITION_DISCONTINUITY) ||
          events.contains(Player.EVENT_REPEAT_MODE_CHANGED) || events.contains(Player.EVENT_PLAY_WHEN_READY_CHANGED) || events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED)) persist()
      }
      override fun onPlayerError(error: PlaybackException) {
        val id = player.currentMediaItem?.mediaId ?: return
        val resumePlayback = player.playWhenReady
        failed += id; notice = "Skipped an unavailable or unreadable song."
        handler.post {
          if (stopping || player.currentMediaItem?.mediaId != id) return@post
          val current = displayedIndex()
          val next = order.displayed.drop(current + 1).firstOrNull { it !in failed }
            ?: if (player.repeatMode == Player.REPEAT_MODE_ALL) order.displayed.take(current.coerceAtLeast(0)).firstOrNull { it !in failed } else null
          if (next != null) { seekEntry(next); player.prepare(); player.playWhenReady = resumePlayback }
          else { player.pause(); notice = "No more playable songs in this queue."; persist() }
        }
      }
    })
    val transport = object : ForwardingPlayer(player) {
      override fun getMediaMetadata(): MediaMetadata = player.currentMediaItem?.mediaMetadata ?: MediaMetadata.EMPTY
      override fun getAvailableCommands(): Player.Commands = super.getAvailableCommands().buildUpon()
        .remove(Player.COMMAND_SEEK_BACK).remove(Player.COMMAND_SEEK_FORWARD).build()
      override fun setShuffleModeEnabled(enabled: Boolean) { shuffle(enabled) }
      override fun getShuffleModeEnabled(): Boolean = order.shuffled
      override fun seekToNext() { next() }
      override fun seekToNextMediaItem() { next() }
      override fun seekToPrevious() { previous() }
      override fun seekToPreviousMediaItem() { previous() }
    }
    val builder = MediaSession.Builder(this, transport).setCallback(object : MediaSession.Callback {
      override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult =
        if (controller.uid == applicationInfo.uid || controller.isTrusted) MediaSession.ConnectionResult.AcceptedResultBuilder(session).build()
        else MediaSession.ConnectionResult.reject()
      override fun onAddMediaItems(session: MediaSession, controller: MediaSession.ControllerInfo,
        mediaItems: List<MediaItem>): ListenableFuture<List<MediaItem>> = try {
        Futures.immediateFuture(mediaItems.map { item ->
          val uri = item.requestMetadata.mediaUri ?: item.localConfiguration?.uri ?: error("No local file supplied.")
          require(uri.scheme in listOf("file", "content")); item.buildUpon().setUri(uri).build()
        })
      } catch (error: Exception) { Futures.immediateFailedFuture(error) }
    })
    packageManager.getLaunchIntentForPackage(packageName)?.let { intent ->
      intent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
      builder.setSessionActivity(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
    }
    session = builder.build()
    scope.launch(Dispatchers.IO) {
      val repository = com.primesk.keyflow.library.CollectionsRepository(applicationContext)
      for ((audio, trackId) in history) {
        try { repository.record(audio, trackId) }
        catch (_: Exception) { withContext(Dispatchers.Main) { notice = "Recently played could not be saved." } }
      }
    }
    scope.launch(Dispatchers.IO) {
      val dao = LibraryDatabase.get(applicationContext).dao()
      for (save in saves) {
        try { dao.saveRecoveryChunks(QueueStorage.recoveryChunks(QueueStorage.encode(save.items, save.order, save.current, save.position, save.repeat, save.ended))) }
        catch (_: Exception) { withContext(Dispatchers.Main) { notice = "Playback recovery could not be saved." } }
      }
    }
    scope.launch {
      try {
        val saved = withContext(Dispatchers.IO) { LibraryDatabase.get(applicationContext).dao().recoveryChunks().takeIf { it.isNotEmpty() }?.joinToString("") { it.snapshot }?.let(QueueStorage::decode) }
        if (!stopping && saved != null && saved.items.isNotEmpty()) {
          order.restore(saved.base, saved.displayed, saved.shuffle, saved.priority)
          val byId = saved.items.associateBy { it.mediaId }
          val items = saved.base.map { byId.getValue(it) }
          physicalIndices = items.mapIndexed { index, item -> item.mediaId to index }.toMap()
          player.setMediaItems(items, saved.base.indexOf(saved.current).coerceAtLeast(0), if (saved.ended) 0 else saved.position)
          QueueTimeline.apply(player, order)
          player.repeatMode = saved.repeat; player.playWhenReady = false
          // Prepare only on explicit Play: disconnected storage must not start error-skipping on launch.
        }
      } catch (_: Exception) { notice = "Saved playback could not be restored. Choose a song to continue." }
      finally { restoring = false; ready.complete(Unit); if (!stopping) handler.postDelayed(checkpoint, 5000) }
    }
  }
  private fun loadCurrentArtwork(item: MediaItem?) {
    val entryId = item?.mediaId
    if (entryId == artworkEntry) return
    artworkEntry = entryId
    artworkJob?.cancel()
    val trackId = item?.mediaMetadata?.extras?.getString("trackId") ?: return
    val uri = item.localConfiguration?.uri ?: item.requestMetadata.mediaUri ?: return
    artworkJob = scope.launch {
      val artwork = try { withContext(Dispatchers.IO) { LibraryRepository(applicationContext).artworkForPlayback(trackId, uri) } }
        catch (_: Exception) { null }
      ensureActive()
      if (stopping || player.currentMediaItem?.mediaId != entryId) return@launch
      val index = player.currentMediaItemIndex
      val current = player.getMediaItemAt(index)
      val newUri = artwork?.let(android.net.Uri::parse)
      if (current.mediaMetadata.artworkUri != newUri) {
        player.replaceMediaItem(index, current.buildUpon().setMediaMetadata(
          current.mediaMetadata.buildUpon().setArtworkUri(newUri).build()).build())
        cachedRevision = -1 // Metadata changed without changing queue order.
        persist()
      }
    }
  }
  internal fun replace(items: List<MediaItem>, start: Int) {
    failed.clear(); notice = null
    if (sleepTimer.state()["mode"] == "end") sleepTimer.cancel()
    val wasShuffled = order.shuffled
    order.replace(items.map { it.mediaId }); order.shuffle(wasShuffled, items[start].mediaId)
    physicalIndices = items.mapIndexed { index, item -> item.mediaId to index }.toMap()
    player.playbackParameters = PlaybackParameters.DEFAULT
    player.setMediaItems(items, start, 0); QueueTimeline.apply(player, order)
    player.prepare(); player.play(); persist()
  }
  private fun displayedIndex() = order.displayed.indexOf(player.currentMediaItem?.mediaId)
  private fun seekEntry(id: String) { player.seekTo(physicalIndices.getValue(id), 0) }
  private fun indexPhysicalEntries() {
    physicalIndices = (0 until player.mediaItemCount).associateBy { player.getMediaItemAt(it).mediaId }
  }
  internal fun jump(id: String) {
    require(id in physicalIndices) { "This queue entry is no longer available." }
    failed.remove(id); notice = null; seekEntry(id)
    if (player.playbackState == Player.STATE_IDLE) player.prepare()
    player.play(); persist()
  }
  internal fun insert(items: List<MediaItem>, playNext: Boolean) {
    val index = order.insert(items.map { it.mediaId }, player.currentMediaItem?.mediaId, playNext)
    if (player.mediaItemCount == 0) player.pause()
    player.addMediaItems(index, items); indexPhysicalEntries(); QueueTimeline.apply(player, order); persist()
  }
  internal fun move(id: String, delta: Int) {
    val old = physicalIndices[id] ?: error("This queue entry is no longer available.")
    require(delta in -player.mediaItemCount..player.mediaItemCount)
    order.move(id, delta)
    val destination = order.base.indexOf(id)
    if (old != destination) player.moveMediaItem(old, destination)
    indexPhysicalEntries(); QueueTimeline.apply(player, order); persist()
  }
  internal fun playNext(id: String) {
    val old = physicalIndices[id] ?: error("This queue entry is no longer available.")
    order.playNext(id, player.currentMediaItem?.mediaId)
    val destination = order.base.indexOf(id)
    if (old != destination) player.moveMediaItem(old, destination)
    indexPhysicalEntries(); QueueTimeline.apply(player, order); persist()
  }
  internal fun remove(id: String) {
    val index = physicalIndices[id] ?: error("This queue entry is no longer available.")
    removeRange(index, index + 1)
  }
  private fun removeRange(start: Int, end: Int) {
    val removed = (start until end).map { player.getMediaItemAt(it).mediaId }.toSet()
    val current = player.currentMediaItem?.mediaId
    if (current in removed && removed.size < order.displayed.size) {
      val index = displayedIndex()
      val replacement = order.displayed.drop(index + 1).firstOrNull { it !in removed }
        ?: order.displayed.take(index).last { it !in removed }
      seekEntry(replacement)
    }
    order.remove(removed); failed.removeAll(removed)
    player.removeMediaItems(start, end); indexPhysicalEntries(); QueueTimeline.apply(player, order); persist()
  }
  internal suspend fun clearUpcoming() {
    val upcoming = order.displayed.drop(displayedIndex() + 1).toSet()
    val indices = upcoming.mapNotNull(physicalIndices::get).sorted()
    val ranges = mutableListOf<Pair<Int, Int>>()
    for (index in indices) {
      val last = ranges.lastOrNull()
      if (last != null && last.second == index) ranges[ranges.lastIndex] = last.first to index + 1
      else ranges += index to index + 1
    }
    // Sparse shuffled removals are grouped, processed backwards and yield to main.
    ranges.asReversed().forEachIndexed { index, range ->
      removeRange(range.first, range.second)
      if (index % 8 == 7) yield()
    }
  }
  internal fun previous() {
    val index = displayedIndex()
    if (QueueOrder.restartPrevious(player.currentPosition)) player.seekTo(0)
    else if (index > 0) seekEntry(order.displayed[index - 1])
    else if (player.repeatMode == Player.REPEAT_MODE_ALL && order.displayed.isNotEmpty()) seekEntry(order.displayed.last())
    else player.seekTo(0)
  }
  internal fun next() {
    val index = displayedIndex()
    if (index + 1 < order.displayed.size) seekEntry(order.displayed[index + 1])
    else if (player.repeatMode == Player.REPEAT_MODE_ALL && order.displayed.isNotEmpty()) seekEntry(order.displayed.first())
  }
  internal fun shuffle(enabled: Boolean) {
    if (enabled == order.shuffled) return
    order.shuffle(enabled, player.currentMediaItem?.mediaId)
    QueueTimeline.apply(player, order)
    persist()
  }
  internal fun setSleepTimer(mode: String, durationMs: Long) {
    sleepTimer.set(mode, durationMs)
    if (notice == "Sleep timer finished.") notice = null
  }
  internal fun repeat(mode: String) { player.repeatMode = when(mode) { "off" -> 0; "one" -> 1; "all" -> 2; else -> error("Unknown repeat mode.") }; persist() }
  internal fun queuePage(offset: Int, limit: Int): Map<String, Any> {
    val start = offset.coerceIn(0, player.mediaItemCount); val end = (start + limit.coerceIn(1, 100)).coerceAtMost(player.mediaItemCount)
    return mapOf("revision" to order.revision.toDouble(), "total" to player.mediaItemCount, "hasMore" to (end < player.mediaItemCount),
      "items" to (start until end).map { index -> val item = player.getMediaItemAt(physicalIndices.getValue(order.displayed[index]))
        mapOf("id" to item.mediaId, "trackId" to item.mediaMetadata.extras?.getString("trackId"),
          "title" to (item.mediaMetadata.title?.toString() ?: "Local audio"), "artist" to item.mediaMetadata.artist?.toString(), "index" to index,
          "priority" to (item.mediaId in order.priority)) })
  }
  internal fun displayedAudio(): List<Pair<Map<String, Any?>, String?>> = order.displayed.map { id ->
    val item = player.getMediaItemAt(physicalIndices.getValue(id))
    QueueStorage.audio(item) to item.mediaMetadata.extras?.getString("trackId")
  }
  internal fun queueState(): Map<String, Any?> = mapOf("currentEntryId" to player.currentMediaItem?.mediaId,
    "queueCount" to player.mediaItemCount, "queueIndex" to displayedIndex().coerceAtLeast(0),
    "queueRevision" to order.revision.toDouble(), "shuffle" to order.shuffled,
    "repeat" to when(player.repeatMode) { 1 -> "one"; 2 -> "all"; else -> "off" },
    "canNext" to (displayedIndex() + 1 < order.displayed.size || (order.displayed.isNotEmpty() && player.repeatMode == 2)),
    "canPrevious" to (player.mediaItemCount > 0), "notice" to notice)
  private fun persist() {
    if (restoring || stopping || !::player.isInitialized) return
    if (cachedRevision != order.revision) {
      cachedItems = (0 until player.mediaItemCount).map(player::getMediaItemAt)
      cachedOrder = QueueOrder().apply { restore(order.base, order.displayed, order.shuffled, order.priority) }
      cachedRevision = order.revision
    }
    saves.trySend(Save(cachedItems, cachedOrder, player.currentMediaItem?.mediaId, player.currentPosition.coerceAtLeast(0), player.repeatMode, player.playbackState == Player.STATE_ENDED))
  }
  override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session
  override fun onTaskRemoved(rootIntent: Intent?) { persist(); if (!player.playWhenReady || player.playbackState == Player.STATE_ENDED) stopSelf() }
  override fun onDestroy() {
    persist(); stopping = true; artworkJob?.cancel()
    sleepTimer.release()
    handler.removeCallbacksAndMessages(null); saves.close(); history.close()
    session?.release(); player.release(); active = null
    // The IO writer drains its final checkpoint independently of the released decoder.
    super.onDestroy()
  }
}
