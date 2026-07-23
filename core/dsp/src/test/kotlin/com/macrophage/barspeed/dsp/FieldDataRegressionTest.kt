package com.macrophage.barspeed.dsp

import com.macrophage.barspeed.model.Phase
import com.macrophage.barspeed.model.StartPhase
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Regression fixtures from a real gym session recorded at the sensor's 10 Hz
 * factory-default rate (the app failed to raise it — see WitmotionCommands.unlock).
 *
 * At 10 Hz the velocity signal is heavily attenuated, so rep DETECTION is
 * best-effort (the lifter's ~0.2 m/s grinder concentrics measure near the
 * noise floor). What these tests pin down is the failure mode from the field:
 * the live tracker used to lock into a phantom "Lowering" phase forever — its
 * filter was designed for 100 Hz, velocity drifted, and the ZUPT anchor
 * rejection then refused to ever re-anchor.
 */
class FieldDataRegressionTest {
    private fun load(name: String) =
        ImuCsv.decode(javaClass.getResourceAsStream("/$name")!!.readBytes().decodeToString())

    @Test
    fun `batch analysis segments slow squats from the 10 Hz field set`() {
        val samples = load("field-backsquat-10hz.csv")
        val analysis = SetAnalyzer.analyze(samples, StartPhase.ECCENTRIC, loadKg = 47.6)
        // 5 real reps; 10 Hz attenuation hides some, but the slow eccentric
        // character of the found reps must be right.
        assertTrue(analysis.reps.size in 2..6, "reps ${analysis.reps.size}")
        analysis.reps.forEach { rep ->
            assertTrue(rep.eccS in 3.0..8.0, "ecc ${rep.eccS} should reflect the ~5s tempo")
        }
    }

    @Test
    fun `streaming tracker follows the 10 Hz field set without locking up`() {
        val samples = load("field-backsquat-10hz.csv")
        val tracker = StreamingSetTracker(StartPhase.ECCENTRIC)
        var last = LiveSetState()
        samples.forEach { last = tracker.feed(it) }
        assertTrue(last.repCount in 3..6, "live rep count ${last.repCount} (5 real reps)")
        assertNotEquals(Phase.ECCENTRIC, last.phase, "tracker ended stuck in a lowering phase")
        assertTrue(abs(last.velocityMps) < 0.15, "velocity drifted: ${last.velocityMps}")
    }

    @Test
    fun `two quiet minutes on the rack stay anchored with no phantom reps (set 5)`() {
        val samples = load("field-backsquat-10hz-set5.csv")
        val startMs = samples.first().timestampMs
        val tracker = StreamingSetTracker(StartPhase.ECCENTRIC)
        var quietMaxV = 0.0
        var repsDuringQuiet = 0
        samples.forEach { sample ->
            val state = tracker.feed(sample)
            if (sample.timestampMs - startMs < 120_000) {
                quietMaxV = maxOf(quietMaxV, abs(state.velocityMps))
                repsDuringQuiet = state.repCount
            }
        }
        assertTrue(quietMaxV < 0.1, "velocity drifted to $quietMaxV during a quiet stretch")
        assertEquals(0, repsDuringQuiet, "phantom reps while the bar sat still")
    }
}
