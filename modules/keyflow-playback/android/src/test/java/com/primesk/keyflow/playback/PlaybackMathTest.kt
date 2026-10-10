package com.primesk.keyflow.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackMathTest {
  @Test fun tempoChangesDoNotTranspose() {
    assertEquals(0.75f, PlaybackMath.speedFactor(0.75), 0.00001f)
    assertEquals(1f, PlaybackMath.pitchFactor(0.0, 0.0), 0.00001f)
  }
  @Test fun octaveAndFineTuningUseMusicalRatios() {
    assertEquals(2f, PlaybackMath.pitchFactor(12.0, 0.0), 0.00001f)
    assertEquals(0.5f, PlaybackMath.pitchFactor(-12.0, 0.0), 0.00001f)
    assertEquals(PlaybackMath.pitchFactor(1.0, 0.0), PlaybackMath.pitchFactor(0.0, 100.0), 0.00001f)
    assertEquals(1.122462f, PlaybackMath.pitchFactor(2.0, 0.0), 0.00001f)
  }
  @Test fun seeksRespectKnownBounds() {
    assertEquals(0L, PlaybackMath.seekPosition(-20.0, 1000))
    assertEquals(1000L, PlaybackMath.seekPosition(1500.0, 1000))
    assertEquals(500L, PlaybackMath.seekPosition(500.0, -1))
  }
  @Test(expected = IllegalArgumentException::class) fun rejectsInvalidTempo() { PlaybackMath.speedFactor(Double.NaN) }
  @Test(expected = IllegalArgumentException::class) fun rejectsExtremePitch() { PlaybackMath.pitchFactor(13.0, 0.0) }
  @Test(expected = IllegalArgumentException::class) fun rejectsInvalidSeek() { PlaybackMath.seekPosition(Double.POSITIVE_INFINITY, 1000) }
}
