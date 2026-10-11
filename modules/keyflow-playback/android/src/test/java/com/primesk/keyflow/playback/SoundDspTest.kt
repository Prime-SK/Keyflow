package com.primesk.keyflow.playback

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class SoundDspTest {
  @Test fun rangeValidationAndIndependentBypass() {
    val off = SoundSettings().updated(mapOf("bands" to List(10) { 4.0 }, "bass" to 2.0))
    assertFalse(off.enabled); assertEquals(0.0, SoundDsp.reduction(off), 0.0)
    for (values in listOf(mapOf("bands" to listOf(0.0)), mapOf("bass" to 13.0), mapOf("preamp" to -25), mapOf("preamp" to Double.NaN))) {
      assertThrows(IllegalArgumentException::class.java) { off.updated(values) }
    }
  }
  @Test fun peakCentersAndShelfFrequencyResponse() {
    for (rate in listOf(44100,48000,96000)) for (hz in SoundSettings.frequencies) {
      val filter = Biquad.make(hz, 6.0, rate)
      assertEquals(6.0, 20*log10(filter.magnitude(2*PI*hz/rate)), .001)
    }
    val shelf = Biquad.make(100.0, 8.0, 48000, true)
    assertEquals(8.0, 20*log10(shelf.magnitude(0.0)), .001)
    assertEquals(4.0, 20*log10(shelf.magnitude(2*PI*100/48000)), .001)
    assertEquals(0.0, 20*log10(shelf.magnitude(PI)), .001)
    assertEquals(Biquad(), Biquad.make(16000.0,12.0,22050))
  }
  @Test fun flatAndBypassAreExactAndChannelsHaveIndependentMemory() {
    for (s in listOf(SoundSettings(), SoundSettings(enabled = true))) {
      val engine = SoundEngine(s,48000,2)
      for (value in listOf(-32768.0,-1000.0,0.0,123.0,32767.0)) assertEquals(value,engine.sample(value,0),0.0)
    }
    val engine = SoundEngine(SoundSettings(true,bass=12.0),48000,2)
    repeat(1000) { engine.sample(20000.0,0); assertEquals(0.0,engine.sample(0.0,1),0.0) }
  }
  @Test fun headroomBoundsBoostedSteadyTonesAndExtremesStayFinite() {
    for (rate in listOf(22050,44100,48000,96000)) {
      val s = SoundSettings(true,List(10) {12.0},12.0,12.0)
      assertTrue(SoundDsp.reduction(s,rate) >= 24.0)
      for (hz in listOf(31.0,100.0,500.0,2000.0,8000.0)) {
        val engine = SoundEngine(s,rate,1)
        var peak = 0.0
        repeat(rate) { n -> val out = engine.sample(32700*sin(2*PI*hz*n/rate),0); assertTrue(out.isFinite()); if(n > rate/2) peak=max(peak,abs(out)) }
        assertTrue("$rate Hz / $hz: $peak",peak < 32768)
      }
    }
  }
  @Test fun boostedPresetsKeepTransientAndMultitoneFixturesWithinPcmRange() {
    val fixtures = listOf(
      SoundSettings(true,listOf(4.0,3.0,1.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0),2.0),
      SoundSettings(true,listOf(0.0,0.0,-2.0,-1.0,1.0,2.0,3.0,1.0,0.0,0.0)),
      SoundSettings(true,listOf(0.0,0.0,0.0,0.0,0.0,0.0,0.0,2.0,3.0,4.0)),
      SoundSettings(true,List(10) { 12.0 },12.0,12.0))
    for(rate in listOf(22050,44100,48000,96000)) for(s in fixtures) {
      val engine = SoundEngine(s,rate,1)
      var peak=0.0
      repeat(rate) { n ->
        val input = when {
          n < rate/4 -> if(n % 2048 == 0) 32000.0 else 0.0
          n < rate/2 -> if(n % 240 < 120) 30000.0 else -30000.0
          else -> 10000*sin(2*PI*63*n/rate) + 10000*sin(2*PI*997*n/rate) + 10000*sin(2*PI*7001*n/rate)
        }
        val out = engine.sample(input,0); assertTrue(out.isFinite()); peak=max(peak,abs(out))
      }
      assertTrue("$rate / ${s.bands}: transient peak $peak",peak < 32768.0)
    }
  }
}
