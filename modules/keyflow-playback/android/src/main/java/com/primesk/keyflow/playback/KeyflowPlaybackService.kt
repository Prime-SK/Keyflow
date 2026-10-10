package com.primesk.keyflow.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/** The service, not the React activity or JS bridge, owns the decoder and its lifetime. */
@UnstableApi
class KeyflowPlaybackService : MediaSessionService() {
  private var session: MediaSession? = null
  private var player: ExoPlayer? = null

  override fun onCreate() {
    super.onCreate()
    val audioPlayer = ExoPlayer.Builder(this).build().apply {
      setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA)
        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
      setHandleAudioBecomingNoisy(true)
      setWakeMode(C.WAKE_MODE_LOCAL)
      // Keep the default software processing path for independent speed and pitch.
    }
    player = audioPlayer
    val builder = MediaSession.Builder(this, audioPlayer).setCallback(object : MediaSession.Callback {
      override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
        return if (controller.uid == applicationInfo.uid || controller.isTrusted) {
          MediaSession.ConnectionResult.AcceptedResultBuilder(session).build()
        } else MediaSession.ConnectionResult.reject()
      }

      override fun onAddMediaItems(session: MediaSession, controller: MediaSession.ControllerInfo,
        mediaItems: List<MediaItem>): ListenableFuture<List<MediaItem>> {
        // Session transport omits localConfiguration; carry the URI in requestMetadata as well.
        return try {
          Futures.immediateFuture(mediaItems.map { item ->
            val uri = item.requestMetadata.mediaUri ?: item.localConfiguration?.uri
              ?: throw IllegalArgumentException("No local audio file was supplied.")
            require(uri.scheme == "content" || uri.scheme == "file") { "Only local audio files are supported." }
            item.buildUpon().setUri(uri).build()
          })
        } catch (error: Exception) {
          Futures.immediateFailedFuture(error)
        }
      }
    })
    packageManager.getLaunchIntentForPackage(packageName)?.let { launchIntent ->
      launchIntent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
      builder.setSessionActivity(PendingIntent.getActivity(this, 0, launchIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
    }
    session = builder.build()
  }

  override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

  override fun onTaskRemoved(rootIntent: Intent?) {
    // Dismissing the UI must not stop actively playing music.
    if (player?.playWhenReady != true || player?.playbackState == Player.STATE_ENDED) stopSelf()
  }

  override fun onDestroy() {
    session?.release()
    player?.release()
    session = null
    player = null
    super.onDestroy()
  }
}
