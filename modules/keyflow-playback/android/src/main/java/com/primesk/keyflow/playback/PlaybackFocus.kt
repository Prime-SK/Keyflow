package com.primesk.keyflow.playback

import android.content.Context
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build

/** One focus owner for both decoders, including ducking the outgoing tail. */
internal class PlaybackFocus(context: Context, private val changed: (Int) -> Unit) {
  private val manager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
  private val listener = AudioManager.OnAudioFocusChangeListener(changed)
  private val request = if (Build.VERSION.SDK_INT >= 26) AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
    .setAudioAttributes(android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_MEDIA)
      .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC).build())
    .setOnAudioFocusChangeListener(listener).setWillPauseWhenDucked(false).build() else null
  private var held = false
  @Suppress("DEPRECATION")
  fun acquire(): Boolean {
    if (held) return true
    held = (if (Build.VERSION.SDK_INT >= 26) manager.requestAudioFocus(request!!)
      else manager.requestAudioFocus(listener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    return held
  }
  @Suppress("DEPRECATION")
  fun release() {
    if (Build.VERSION.SDK_INT >= 26) manager.abandonAudioFocusRequest(request!!) else manager.abandonAudioFocus(listener)
    held = false
  }
}
