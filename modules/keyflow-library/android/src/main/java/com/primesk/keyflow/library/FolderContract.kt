package com.primesk.keyflow.library

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import expo.modules.kotlin.activityresult.AppContextActivityResultContract

internal data class FolderSelection(val uri: Uri, val flags: Int)
internal class FolderContract : AppContextActivityResultContract<String, FolderSelection?> {
  override fun createIntent(context: Context, input: String) = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
    putExtra(Intent.EXTRA_LOCAL_ONLY, true)
    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
  }
  override fun parseResult(input: String, resultCode: Int, intent: Intent?): FolderSelection? =
    if (resultCode == Activity.RESULT_OK && intent?.data != null) FolderSelection(intent.data!!, intent.flags) else null
}
