package com.primesk.keyflow.playback

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import expo.modules.kotlin.activityresult.AppContextActivityResultContract

internal data class AudioSelection(val uri: Uri, val flags: Int)

internal class AudioFileContract : AppContextActivityResultContract<String, AudioSelection?> {
  override fun createIntent(context: Context, input: String) = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
    addCategory(Intent.CATEGORY_OPENABLE)
    type = "audio/*"
    putExtra(Intent.EXTRA_LOCAL_ONLY, true)
    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
  }

  override fun parseResult(input: String, resultCode: Int, intent: Intent?): AudioSelection? {
    if (resultCode != Activity.RESULT_OK) return null
    val uri = intent?.data ?: return null
    return AudioSelection(uri, intent.flags)
  }
}
