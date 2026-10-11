package com.primesk.keyflow.playback

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

@UnstableApi
@RunWith(AndroidJUnit4::class)
class TransitionPlayerDeviceTest {
  private val i get() = InstrumentationRegistry.getInstrumentation()
  private fun await(message: String, condition: () -> Boolean) {
    val deadline = System.currentTimeMillis() + 8000
    while (System.currentTimeMillis() < deadline) {
      var ready = false; i.runOnMainSync { ready = condition() }
      if (ready) return
      Thread.sleep(20)
    }
    fail(message)
  }
  private fun withPlayer(sound: () -> SoundSettings = { SoundSettings() }, test: (TransitionPlayer, QueueOrder) -> Unit) {
    val file = File(i.targetContext.cacheDir, "transition-tone.wav")
    i.context.assets.open("audio/tone.wav").use { input -> file.outputStream().use(input::copyTo) }
    lateinit var player: TransitionPlayer
    val original = TransitionSettings.load(i.targetContext)
    val order = QueueOrder().apply { replace((0..3).map { "transition-$it" }) }
    i.runOnMainSync {
      player = TransitionPlayer(i.targetContext, TransitionPlayer.newDeck(i.targetContext, sound), order,
        { _, position, _ -> player.seekCurrent(position) }, { enabled -> order.shuffle(enabled, player.currentMediaItem?.mediaId) }, { _, _ -> }, sound)
      player.update(mapOf("automatic" to false, "manual" to true, "manualMs" to 1500, "manualCurve" to "linear", "gapless" to false))
      player.current.setMediaItems(order.base.map { MediaItem.Builder().setMediaId(it).setUri(Uri.fromFile(file)).build() })
      player.current.prepare(); player.current.play()
    }
    try { await("Initial decoder did not play") { player.current.isPlaying }; test(player, order) }
    finally { i.runOnMainSync { player.release(); original.save(i.targetContext) }; file.delete() }
  }
  @Test fun overlapPauseResumeSeekAndRapidRetargetKeepOneLogicalPlayer() = withPlayer { player, _ ->
    i.runOnMainSync { player.change(1) }
    await("Incoming deck did not become displayed") { player.currentMediaItem?.mediaId == "transition-1" && player.transitionState()["outgoingEntryId"] != null }
    var progress = 0.0
    i.runOnMainSync { player.pause(); progress = player.transitionState()["progress"] as Double; assertFalse(player.playWhenReady) }
    Thread.sleep(200)
    i.runOnMainSync { assertEquals(progress, player.transitionState()["progress"] as Double, .001); player.pauseImmediately(); player.current.play(); player.seekCurrent(100) }
    i.runOnMainSync { assertNull(player.transitionState()["outgoingEntryId"]); assertEquals("transition-1", player.currentMediaItem!!.mediaId) }
    i.runOnMainSync { player.change(2); player.change(3); player.change(0) }
    await("Rapid retarget did not reach final request") { player.currentMediaItem?.mediaId == "transition-0" }
  }
  @Test fun queueEditDuringOverlapKeepsIncomingAndOutgoingRemovalStopsOnlyTail() = withPlayer { player, order ->
    i.runOnMainSync { player.change(1) }
    await("No overlap") { player.currentMediaItem?.mediaId == "transition-1" && player.transitionState()["outgoingEntryId"] != null }
    i.runOnMainSync {
      order.move("transition-3", -1); QueueTimeline.apply(player.current, order); player.invalidatePreload()
      assertEquals("transition-0", player.transitionState()["outgoingEntryId"])
      player.tailRemoved(setOf("transition-0")); assertNull(player.transitionState()["outgoingEntryId"])
      assertEquals("transition-1", player.currentMediaItem!!.mediaId); assertTrue(player.current.playWhenReady)
    }
  }
  @Test fun automaticRepeatUsesAnotherInstanceAndEndTimerSuppressesOverlap() = withPlayer { player, _ ->
    i.runOnMainSync {
      player.update(mapOf("automatic" to true, "automaticMs" to 1000, "manual" to false))
      player.current.repeatMode = Player.REPEAT_MODE_ONE
    }
    Thread.sleep(500)
    i.runOnMainSync { player.seekCurrent((player.duration - 800).coerceAtLeast(0)) }
    await("Repeat one did not crossfade") { player.transitionState()["kind"] == "automatic" }
    i.runOnMainSync {
      assertEquals("transition-0", player.transitionState()["incomingEntryId"])
      assertEquals("transition-0", player.transitionState()["outgoingEntryId"])
      player.setEndTimer(true); assertNull(player.transitionState()["outgoingEntryId"])
      player.seekCurrent((player.duration - 200).coerceAtLeast(0))
    }
    Thread.sleep(500)
    i.runOnMainSync { assertNull(player.transitionState()["outgoingEntryId"]); assertFalse(player.playWhenReady) }
  }
  @Test fun decoderFormatsCanBecomeTheIncomingSongWithoutLosingTheQueue() = withPlayer { player, order ->
    val files = listOf("mp3", "m4a", "flac", "wav", "ogg").map { extension ->
      File(i.targetContext.cacheDir, "transition-format.$extension").also { file ->
        i.context.assets.open("audio/tone.$extension").use { input -> file.outputStream().use(input::copyTo) }
      }
    }
    try {
      for ((index, file) in files.withIndex()) {
        val queue = files.mapIndexed { n, source -> MediaItem.Builder().setMediaId("format-$n").setUri(Uri.fromFile(source)).build() }
        i.runOnMainSync {
          player.changeQueue(queue, index, true) { order.replace(queue.map { it.mediaId }) }
        }
        await("Incoming ${file.extension} failed") { player.currentMediaItem?.mediaId == "format-$index" && player.current.isPlaying }
        i.runOnMainSync { assertNull(player.playerError); assertEquals(files.size, player.mediaItemCount); player.finishTail() }
      }
    } finally { files.forEach { it.delete() } }
  }
  @Test fun restartAndPauseFadeKeepTheSameEntryAndImmediatePauseStopsBothDecks() = withPlayer { player, _ ->
    i.runOnMainSync { player.change(player.currentMediaItemIndex) }
    await("Restart did not fade") { player.transitionState()["outgoingEntryId"] == "transition-0" }
    var progress = 0.0
    i.runOnMainSync { player.pause(); progress = player.transitionState()["progress"] as Double; assertFalse(player.playWhenReady) }
    Thread.sleep(120)
    i.runOnMainSync {
      assertEquals(progress, player.transitionState()["progress"] as Double, .001)
      assertTrue("Pause fade should still be rendering", player.current.playWhenReady)
      player.pauseImmediately(); assertFalse(player.current.playWhenReady)
      assertEquals("transition-0", player.currentMediaItem!!.mediaId)
    }
  }
  @Test fun pauseFadeNearTheEndDoesNotStartTheNextSong() = withPlayer { player, _ ->
    i.runOnMainSync { player.seekCurrent((player.duration - 300).coerceAtLeast(0)) }
    await("Seek did not resume") { player.current.isPlaying }
    i.runOnMainSync { player.pause() }
    await("Near-end pause did not stop") { !player.current.playWhenReady }
    Thread.sleep(200)
    i.runOnMainSync { assertEquals("transition-0", player.currentMediaItem!!.mediaId); assertFalse(player.playWhenReady) }
  }
  private fun wav(rate: Int, channels: Int): File {
    val frames = rate * 2; val bytes = frames * channels * 2
    val data = ByteBuffer.allocate(44 + bytes).order(ByteOrder.LITTLE_ENDIAN)
    data.put("RIFF".toByteArray()).putInt(bytes+36).put("WAVEfmt ".toByteArray()).putInt(16)
      .putShort(1).putShort(channels.toShort()).putInt(rate).putInt(rate*channels*2)
      .putShort((channels*2).toShort()).putShort(16).put("data".toByteArray()).putInt(bytes)
    repeat(frames) { frame -> repeat(channels) { ch -> data.putShort((4000*kotlin.math.sin(2*kotlin.math.PI*(440+ch*110)*frame/rate)).toInt().toShort()) } }
    return File(i.targetContext.cacheDir,"transition-$rate-$channels.wav").apply { writeBytes(data.array()) }
  }
  @Test fun mixedSampleRatesAndChannelsOverlapWithSoundProcessing() = withPlayer(
    sound = { SoundSettings(true, listOf(4.0,3.0,1.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0),2.0) }
  ) { player, order ->
    val files = listOf(wav(48000,2),wav(22050,1),wav(44100,2))
    try {
      val queue = files.mapIndexed { n,file -> MediaItem.Builder().setMediaId("mixed-$n").setUri(Uri.fromFile(file)).build() }
      for(index in files.indices) {
        i.runOnMainSync { player.changeQueue(queue,index,true) { order.replace(queue.map { it.mediaId }) } }
        await("Mixed-rate incoming $index failed") { player.currentMediaItem?.mediaId == "mixed-$index" && player.current.isPlaying && player.transitionState()["outgoingEntryId"] != null }
        i.runOnMainSync {
          assertNull(player.playerError); assertEquals(listOf(48000,22050,44100)[index],player.current.audioFormat!!.sampleRate)
          assertEquals(listOf(2,1,2)[index],player.current.audioFormat!!.channelCount); player.finishTail()
        }
      }
    } finally { files.forEach { it.delete() } }
  }
  @Test fun gaplessAndDisablingAutomaticEndTailAndCurveEditsAffectNextTransition() = withPlayer { player, _ ->
    i.runOnMainSync { player.change(1) }
    await("No initial overlap") { player.transitionState()["outgoingEntryId"] != null }
    i.runOnMainSync {
      player.update(mapOf("manualCurve" to "equal-power", "manualMs" to 500))
      assertNotNull(player.transitionState()["outgoingEntryId"])
      player.update(mapOf("gapless" to true)); assertNull(player.transitionState()["outgoingEntryId"])
      assertEquals("transition-1",player.currentMediaItem!!.mediaId)
      player.change(2)
    }
    await("Manual fade must remain enabled with gapless") { player.currentMediaItem?.mediaId == "transition-2" && player.transitionState()["outgoingEntryId"] != null }
    i.runOnMainSync {
      player.update(mapOf("manualMs" to 1000)); assertNotNull("Editing gapless settings must keep manual overlap",player.transitionState()["outgoingEntryId"])
      player.finishTail(); player.update(mapOf("automatic" to true)); assertFalse(player.settings.gapless)
      player.change(3)
    }
    await("No last overlap") { player.transitionState()["outgoingEntryId"] != null }
    i.runOnMainSync { player.update(mapOf("automatic" to false)); assertNull(player.transitionState()["outgoingEntryId"]); assertEquals("transition-3",player.currentMediaItem!!.mediaId) }
  }
  @Test fun failedPreloadIsSilentAndManualFailureReachesLogicalErrorState() = withPlayer { player, order ->
    val bad = File(i.targetContext.cacheDir,"transition-bad.mp3").apply { writeText("not a song") }
    lateinit var good: MediaItem
    i.runOnMainSync { good = player.current.getMediaItemAt(0) }
    try {
      val queue = listOf(good,MediaItem.Builder().setMediaId("bad").setUri(Uri.fromFile(bad)).build())
      i.runOnMainSync {
        player.changeQueue(queue,0,true,false) { order.replace(queue.map { it.mediaId }) }
        player.current.play(); player.update(mapOf("automatic" to true,"automaticMs" to 1000))
      }
      await("Good entry not ready") { player.current.isPlaying }
      Thread.sleep(350)
      i.runOnMainSync {
        assertNull("Preload error leaked to current player",player.playerError)
        assertEquals(good.mediaId,player.currentMediaItem!!.mediaId)
        assertNull(player.transitionState()["outgoingEntryId"])
        player.change(1)
      }
      await("Manual error did not reach logical player") { player.currentMediaItem?.mediaId == "bad" && player.playerError != null }
      i.runOnMainSync { assertNull(player.transitionState()["outgoingEntryId"]); assertEquals(2,player.mediaItemCount) }
    } finally { bad.delete() }
  }
}
