package com.primesk.keyflow.playback

import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ShuffleOrder

/** The physical playlist stays in base order; only its traversal order changes. */
@UnstableApi
internal object QueueTimeline {
  fun apply(player: ExoPlayer, order: QueueOrder) {
    val indices = (0 until player.mediaItemCount).associateBy { player.getMediaItemAt(it).mediaId }
    require(indices.size == order.displayed.size && indices.keys == order.displayed.toSet())
    player.setShuffleOrder(ShuffleOrder.DefaultShuffleOrder(order.displayed.map { indices.getValue(it) }.toIntArray(), 0L))
    player.shuffleModeEnabled = order.shuffled
  }
}
