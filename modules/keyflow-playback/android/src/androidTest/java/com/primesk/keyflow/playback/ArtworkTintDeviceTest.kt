package com.primesk.keyflow.playback

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ArtworkTintDeviceTest {
  @Test fun ambientImagesAreBoundedTransparentCachedAndUseThemeFallback() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val appearance = ArtworkAmbient.read(context, "file:///sdcard/never-read-audio.mp3", "#6D91FF")
    assertNull(appearance["color"])
    val path = Uri.parse(appearance.getValue("glowUri")).path!!
    val glow = android.graphics.BitmapFactory.decodeFile(path)
    try {
      assertEquals(256, glow.width); assertEquals(256, glow.height)
      assertTrue(Color.alpha(glow.getPixel(0, 0)) < 12)
      assertTrue(Color.alpha(glow.getPixel(128, 128)) > 100)
      assertEquals(appearance, ArtworkAmbient.read(context, null, "#6D91FF"))
    } finally { glow.recycle() }
    for (index in 0..35) ArtworkAmbient.read(context, null, String.format("#%06X", index * 1000))
    val directory = File(context.cacheDir, "keyflow-ambient-v1")
    assertTrue(directory.listFiles()!!.size <= 32)
    assertThrows(IllegalArgumentException::class.java) { ArtworkAmbient.read(context, null, "not a color") }
  }
  @Test fun artworkSamplingHandlesColorMissingAndMonochromeWithoutReadingAudio() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val file = File(context.cacheDir, "tint-test.png")
    val bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
    try {
      bitmap.eraseColor(Color.rgb(180, 60, 100))
      file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
      val uri = Uri.fromFile(file).toString()
      assertEquals("#B43C64", ArtworkTint.read(context, uri))
      assertEquals("#B43C64", ArtworkAmbient.read(context, uri, "#6D91FF")["color"])
      assertEquals("#B43C64", ArtworkTint.read(context, uri))
      assertNull(ArtworkTint.read(context, "content://media/external/audio/media/1"))
      assertNull(ArtworkTint.read(context, "file:///sdcard/never-read-audio.mp3"))
      file.delete(); assertNull(ArtworkTint.read(context, uri))
      bitmap.eraseColor(Color.WHITE)
      val white = File(context.cacheDir, "tint-white.png")
      try {
        white.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertNull(ArtworkTint.read(context, Uri.fromFile(white).toString()))
      } finally { white.delete() }
    } finally { bitmap.recycle(); file.delete() }
  }
}
