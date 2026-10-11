package com.primesk.keyflow.library

import android.app.Activity
import android.Manifest
import android.os.Build
import android.os.Bundle
import android.content.pm.PackageManager
import android.widget.TextView

/** Test-only activity: use the normal user-approved dialog on OEMs restricting shell grants. */
class PermissionProbeActivity : Activity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(TextView(this).apply {
      text = "Keyflow library tests\n\nPlease allow audio access for the device-scanning test. Your audio files remain untouched."
      textSize = 20f; setPadding(40, 80, 40, 40)
    })
    val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
    if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) requestPermissions(arrayOf(permission), 1)
  }
}
