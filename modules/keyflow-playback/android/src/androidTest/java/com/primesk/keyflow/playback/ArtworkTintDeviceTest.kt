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
  @Test fun artworkSamplingHandlesColorMissingAndMonochromeWithoutReadingAudio() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val file = File(context.cacheDir, "tint-test.png")
    val bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
    try {
      bitmap.eraseColor(Color.rgb(180, 60, 100))
      file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
      val uri = Uri.fromFile(file).toString()
      assertEquals("#B43C64", ArtworkTint.read(context, uri))
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
