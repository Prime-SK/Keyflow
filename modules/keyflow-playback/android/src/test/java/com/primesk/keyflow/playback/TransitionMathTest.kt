package com.primesk.keyflow.playback

import org.junit.Assert.*
import org.junit.Test

class TransitionMathTest {
  @Test fun linearAndEqualPowerEnvelopesHaveCorrectEndpointsAndShape() {
    for (curve in listOf("linear", "equal-power")) {
      assertEquals(1f to 0f, TransitionMath.gains(0.0, curve))
      val (a, b) = TransitionMath.gains(1.0, curve)
      assertEquals(0f, a, 0.00001f); assertEquals(1f, b, 0.00001f)
      for (step in 0..100) {
        val (outgoing, incoming) = TransitionMath.gains(step / 100.0, curve)
        if (curve == "linear") assertEquals(1f, outgoing + incoming, 0.00001f)
        else { assertEquals(1f, outgoing * outgoing + incoming * incoming, 0.00001f); assertTrue((outgoing + incoming) * .70710677f <= 1.00001f) }
      }
    }
  }
  @Test fun overlapIsBoundedByBothTracksAndUnknownDurationsDisableIt() {
    assertEquals(1000, TransitionMath.overlap(12000, 2000, 8000))
    assertEquals(1000, TransitionMath.overlap(12000, 8000, 2000))
    assertEquals(0, TransitionMath.overlap(5000, 0, 8000))
    assertEquals(0, TransitionMath.overlap(5000, 8000, -1))
  }
  @Test fun settingsKeepDurationsDisabledAndMutuallyExcludeGapless() {
    val initial = TransitionSettings()
    val durationOnly = initial.updated(mapOf("automaticMs" to 7500.0, "manualMs" to 500))
    assertFalse(durationOnly.automatic); assertFalse(durationOnly.manual)
    assertEquals(7500, durationOnly.automaticMs)
    val gapless = durationOnly.updated(mapOf("automatic" to true)).updated(mapOf("gapless" to true))
    assertTrue(gapless.gapless); assertFalse(gapless.automatic)
    val auto = gapless.updated(mapOf("automatic" to true, "manual" to true))
    assertTrue(auto.automatic); assertFalse(auto.gapless); assertTrue(auto.manual)
  }
  @Test fun rejectsNonFiniteAndOutOfRangeInputs() {
    for (bad in listOf(Double.NaN, Double.POSITIVE_INFINITY, -1.0, 12001.0)) {
      assertThrows(IllegalArgumentException::class.java) { TransitionSettings().updated(mapOf("automaticMs" to bad)) }
    }
    assertThrows(IllegalArgumentException::class.java) { TransitionSettings().updated(mapOf("manualCurve" to "mystery")) }
  }
}
