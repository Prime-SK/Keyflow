package com.primesk.keyflow.playback

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

@UnstableApi
@RunWith(AndroidJUnit4::class)
class SoundDeviceTest {
  @Test fun realFormatsUseSoftwareChainAndLiveEditsDoNotReloadDecoder() {
    val i = InstrumentationRegistry.getInstrumentation()
    for (ext in listOf("mp3","m4a","flac","wav","ogg")) {
      val file = File(i.targetContext.cacheDir,"sound-tone.$ext")
      i.context.assets.open("audio/tone.$ext").use { input -> file.outputStream().use(input::copyTo) }
      val settings = AtomicReference(SoundSettings(enabled=true,bass=3.0))
      val buffers = AtomicInteger(); val ended = CountDownLatch(1); val ready = CountDownLatch(1)
      var failure: PlaybackException? = null; var buffering = 0
      lateinit var player: ExoPlayer
      i.runOnMainSync {
        player = TransitionPlayer.newDeck(i.targetContext) { buffers.incrementAndGet(); settings.get() }
        player.volume = 0f
        player.addListener(object : Player.Listener {
          override fun onPlaybackStateChanged(state: Int) {
            if(state == Player.STATE_READY) ready.countDown()
            if(state == Player.STATE_BUFFERING) buffering++
            if(state == Player.STATE_ENDED) ended.countDown()
          }
          override fun onPlayerError(error: PlaybackException) { failure=error; ended.countDown(); ready.countDown() }
        })
        player.setMediaItem(MediaItem.fromUri(android.net.Uri.fromFile(file))); player.prepare(); player.play()
      }
      try {
        assertTrue("$ext did not prepare",ready.await(10,TimeUnit.SECONDS)); assertNull(failure)
        Thread.sleep(150)
        var count=0; i.runOnMainSync { count=buffering }
        settings.set(SoundSettings(enabled=true,bands=List(10) { 2.0 },preamp=-4.0))
        Thread.sleep(100); settings.set(SoundSettings())
        assertTrue("$ext did not finish",ended.await(10,TimeUnit.SECONDS)); assertNull(failure)
        assertTrue("$ext bypassed custom PCM chain",buffers.get() > 2)
        i.runOnMainSync { assertEquals("$ext reloaded decoder",count,buffering) }
      } finally { i.runOnMainSync { player.release() }; file.delete() }
    }
  }
  @Test fun persistentPresetsAreExplicitAndBuiltInsReadOnly() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val prefs = context.getSharedPreferences("keyflow-sound",0)
    prefs.edit().clear().commit()
    try {
      val store = SoundStore(context)
      assertFalse(store.current.enabled)
      store.select("bass"); assertFalse(store.current.enabled)
      val id = store.save("Practice")
      store.update(mapOf("preamp" to -6.0,"enabled" to true))
      store.select(id); assertEquals(0.0,store.current.preamp,0.0); assertTrue(store.current.enabled)
      store.update(mapOf("bass" to -3.0)); store.edit(id,"update",null); store.edit(id,"rename","Renamed")
      val restored = SoundStore(context)
      restored.select(id); assertEquals(-3.0,restored.current.bass,0.0)
      assertTrue(restored.state(0.0).toString().contains("Renamed"))
      try { restored.edit("flat","delete",null); fail("Built-in edited") } catch (_: IllegalArgumentException) {}
      restored.edit(id,"delete",null)
      assertFalse(SoundStore(context).state(0.0).toString().contains(id))
    } finally { prefs.edit().clear().commit() }
  }
  @Test fun pcmBypassAndSmoothingDoNotReconfigureAndBothInstancesReceiveChanges() {
    var sound = SoundSettings()
    val first = SoundAudioProcessor { sound }; val second = SoundAudioProcessor { sound }
    for (processor in listOf(first,second)) { processor.configure(AudioFormat(48000,2,C.ENCODING_PCM_16BIT)); processor.flush() }
    fun render(processor: SoundAudioProcessor, frames: Int): ShortArray {
      val input = ByteBuffer.allocateDirect(frames*4).order(ByteOrder.nativeOrder())
      repeat(frames) { input.putShort(12000); input.putShort(-12000) }; input.flip()
      processor.queueInput(input)
      val out = processor.output.order(ByteOrder.nativeOrder())
      return ShortArray(out.remaining()/2) { out.short }
    }
    val bypass = render(first,128); assertEquals(12000,bypass[0].toInt()); assertEquals(-12000,bypass[1].toInt())
    sound = SoundSettings(enabled=true,preamp=-12.0)
    val a = render(first,2048); val b = render(second,2048)
    assertArrayEquals(a,b)
    assertEquals(12000,a[0].toInt()); assertTrue(kotlin.math.abs(3014 + a.last().toInt()) <= 2)
    var largest = 0
    for(i in 2 until a.size step 2) largest = maxOf(largest,kotlin.math.abs(a[i].toInt()-a[i-2].toInt()))
    assertTrue("Ramp clicked: $largest",largest <= 11)
    sound = SoundSettings()
    render(first,2048)
    assertArrayEquals(bypass,render(first,128))
  }
}
