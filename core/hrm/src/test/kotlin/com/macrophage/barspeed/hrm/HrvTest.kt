package com.macrophage.barspeed.hrm

import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HrvTest {
    @Test
    fun `rmssd of alternating series matches hand calculation`() {
        // 800/850 alternation: every successive diff is 50 ms → RMSSD = 50.
        val rr = List(40) { if (it % 2 == 0) 800.0 else 850.0 }
        val rmssd = Hrv.rmssdMs(rr)!!
        assertTrue(abs(rmssd - 50.0) < 1e-9, "rmssd $rmssd")
    }

    @Test
    fun `steady series has near-zero rmssd and sdnn`() {
        val rr = List(30) { 900.0 }
        assertEquals(0.0, Hrv.rmssdMs(rr)!!, 1e-9)
        assertEquals(0.0, Hrv.sdnnMs(rr)!!, 1e-9)
    }

    @Test
    fun `artifacts are rejected before computing`() {
        // A dropped-beat artifact (1700 ms ≈ two beats) would wreck RMSSD.
        val rr = List(30) { 800.0 + (it % 2) * 20.0 }
        val withArtifacts = rr.toMutableList().apply {
            add(15, 1700.0)
            add(5, 120.0)
        }
        val cleanRmssd = Hrv.rmssdMs(rr)!!
        val dirtyRmssd = Hrv.rmssdMs(withArtifacts)!!
        assertTrue(abs(cleanRmssd - dirtyRmssd) < 2.0, "artifact leaked: $dirtyRmssd vs $cleanRmssd")
    }

    @Test
    fun `too few beats returns null`() {
        assertNull(Hrv.rmssdMs(List(5) { 800.0 }))
        assertNull(Hrv.sdnnMs(List(3) { 800.0 }))
    }

    @Test
    fun `sdnn matches population standard deviation`() {
        val rr = listOf(780.0, 820.0, 800.0, 790.0, 810.0, 805.0, 795.0, 800.0, 815.0, 785.0, 800.0)
        val mean = rr.average()
        val expected = sqrt(rr.map { (it - mean) * (it - mean) }.average())
        assertEquals(expected, Hrv.sdnnMs(rr, minIntervals = 10)!!, 1e-9)
    }
}
