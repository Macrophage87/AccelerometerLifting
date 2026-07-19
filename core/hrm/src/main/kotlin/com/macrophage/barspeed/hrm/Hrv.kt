package com.macrophage.barspeed.hrm

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Heart-rate variability from R-R intervals (the standard BLE HR characteristic
 * carries them; the Garmin HRM 600 sends them every beat).
 *
 * RMSSD is the metric of choice for short recordings: it reflects beat-to-beat
 * (parasympathetic) variability and is robust over the 1–5 minute windows we
 * get during rest periods and sessions.
 */
object Hrv {
    private const val MIN_RR_MS = 300.0
    private const val MAX_RR_MS = 2000.0

    /** Beats that jump more than this fraction from the previous accepted beat are artifacts. */
    private const val MAX_JUMP_FRACTION = 0.25

    const val DEFAULT_MIN_INTERVALS = 10

    /**
     * Artifact rejection: drop physiologically implausible intervals and
     * ectopic/missed-beat jumps, which otherwise dominate RMSSD.
     */
    fun clean(rrMs: List<Double>): List<Double> {
        val plausible = rrMs.filter { it in MIN_RR_MS..MAX_RR_MS }
        if (plausible.size < 2) return plausible
        val out = mutableListOf(plausible.first())
        for (i in 1 until plausible.size) {
            val prev = out.last()
            if (abs(plausible[i] - prev) <= prev * MAX_JUMP_FRACTION) out += plausible[i]
        }
        return out
    }

    /** Root mean square of successive differences, in ms. Null below [minIntervals] clean beats. */
    fun rmssdMs(rrMs: List<Double>, minIntervals: Int = DEFAULT_MIN_INTERVALS): Double? {
        val rr = clean(rrMs)
        if (rr.size < minIntervals + 1) return null
        val diffs = rr.zipWithNext { a, b -> b - a }
        return sqrt(diffs.map { it * it }.average())
    }

    /** Standard deviation of the (cleaned) R-R intervals, in ms. */
    fun sdnnMs(rrMs: List<Double>, minIntervals: Int = DEFAULT_MIN_INTERVALS): Double? {
        val rr = clean(rrMs)
        if (rr.size < minIntervals) return null
        val mean = rr.average()
        return sqrt(rr.map { (it - mean) * (it - mean) }.average())
    }
}
