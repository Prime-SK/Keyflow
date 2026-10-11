package com.primesk.keyflow.playback

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import java.io.File
import java.util.Locale

/** Sample cached artwork off the playback thread; bounded decode and 32-color LRU. */
internal object ArtworkTint {
  private val cache = object : LinkedHashMap<String, String?>(32, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String?>) = size > 32
  }
  @Synchronized fun read(context: Context, value: String): String? {
    val uri = Uri.parse(value)
    if (uri.scheme != "file") return null
    val file = File(uri.path ?: return null).canonicalFile
    if (!file.path.startsWith(context.cacheDir.canonicalPath + File.separator) || !file.isFile || file.length() > 4 * 1024 * 1024) return null
    val key = "${file.path}:${file.length()}:${file.lastModified()}"
    if (cache.containsKey(key)) return cache[key]
    val tint = try {
      val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
      BitmapFactory.decodeFile(file.path, bounds)
      if (bounds.outWidth <= 0 || bounds.outHeight <= 0 || maxOf(bounds.outWidth, bounds.outHeight) > 32768) return null
      var sample = 1
      while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 64) sample *= 2
      val bitmap = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
      try {
        var red = 0L; var green = 0L; var blue = 0L; var count = 0
        for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
          val pixel = bitmap.getPixel(x, y)
          val r = Color.red(pixel); val g = Color.green(pixel); val b = Color.blue(pixel)
          if (Color.alpha(pixel) >= 128 && maxOf(r, g, b) > 30 && minOf(r, g, b) < 225) {
            red += r; green += g; blue += b; count++
          }
        }
        if (count == 0) null else String.format(Locale.US, "#%02X%02X%02X", red / count, green / count, blue / count)
      } finally { bitmap.recycle() }
    } catch (_: Exception) { null }
    cache[key] = tint
    return tint
  }
}
