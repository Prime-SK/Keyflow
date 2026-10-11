package com.primesk.keyflow.playback

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import java.io.File

/** Small software-rendered PNGs; at most 32 cached colors, never the original audio. */
internal object ArtworkAmbient {
  @Synchronized fun read(context: Context, artwork: String?, fallback: String): Map<String, String?> {
    require(Regex("#[0-9a-fA-F]{6}").matches(fallback))
    val color = artwork?.let { ArtworkTint.read(context, it) }
    val glow = glow(context, color ?: fallback)
    return mapOf("color" to color, "glowUri" to Uri.fromFile(glow).toString())
  }
  private fun glow(context: Context, color: String): File {
    val directory = File(context.cacheDir, "keyflow-ambient-v1").apply { mkdirs() }
    val target = File(directory, color.substring(1).uppercase() + ".png")
    if (!target.isFile) {
      val bitmap = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
      try {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
          this.color = Color.parseColor(color); alpha = 190
          maskFilter = BlurMaskFilter(28f, BlurMaskFilter.Blur.NORMAL)
        }
        Canvas(bitmap).drawRoundRect(RectF(40f, 40f, 216f, 216f), 16f, 16f, paint)
        val temporary = File(directory, "pending.png")
        temporary.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        check(temporary.renameTo(target))
      } finally { bitmap.recycle() }
    }
    target.setLastModified(System.currentTimeMillis())
    directory.listFiles()?.filter { it.extension == "png" && it != target }
      ?.sortedByDescending { it.lastModified() }?.drop(31)?.forEach { it.delete() }
    return target
  }
}
