package com.primesk.keyflow.playback

import org.junit.Assert.*
import org.junit.Test

class TransportEnvelopeTest {
  @Test fun fadesReachSilenceAndUnityAndReverseWithoutDiscontinuity() {
    for (curve in listOf("linear", "equal-power")) {
      val envelope = TransportEnvelope()
      envelope.begin(false, 1000, curve); envelope.step(400)
      val partial = envelope.gain
      assertTrue(partial in 0.01f..0.99f)
      envelope.begin(true, 1000, curve); assertEquals(partial, envelope.gain, 0f)
      envelope.step(1000); assertEquals(1f, envelope.gain, 0f); assertFalse(envelope.active)
      envelope.begin(false, 1000, curve); assertTrue(envelope.step(1000))
      assertEquals(0f, envelope.gain, 0f)
      envelope.begin(true, 1000, curve); envelope.step(1000)
      assertEquals(1f, envelope.gain, 0f)
    }
  }
}
