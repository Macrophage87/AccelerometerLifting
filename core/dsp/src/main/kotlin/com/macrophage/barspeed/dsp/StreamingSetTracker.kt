package com.macrophage.barspeed.dsp

import com.macrophage.barspeed.model.ImuSample
import com.macrophage.barspeed.model.Phase
import com.macrophage.barspeed.model.StartPhase
import kotlin.math.abs

/** Live state for the in-set display. */
data class LiveSetState(
    val velocityMps: Double = 0.0,
    val phase: Phase = Phase.IDLE,
    val repCount: Int = 0,
    val currentPhaseElapsedS: Double = 0.0,
    /** Mean concentric velocity of each completed rep, for live per-rep bars. */
    val repMeanVelocities: List<Double> = emptyList(),
    /** Peak concentric velocity of each completed rep — the metric for explosive lifts. */
    val repPeakVelocities: List<Double> = emptyList(),
)

/**
 * Incremental, low-latency tracker for live in-set feedback (velocity readout,
 * rep counter, current phase). Uses ZUPT anchor resets but no retroactive drift
 * correction — authoritative metrics come from [SetAnalyzer] at set end.
 */
class StreamingSetTracker(
    private val startsWith: StartPhase = StartPhase.ECCENTRIC,
    private val config: DspConfig = DspConfig(),
    expectedSampleRateHz: Double = 100.0,
) {
    private var filter = Biquad.lowPass(config.lowPassCutoffHz, expectedSampleRateHz)

    // The sensor may stream at its 10 Hz factory default instead of the rate we
    // requested; a filter designed for the wrong rate lags badly and the
    // integrator drifts. Measure the delivered rate and rebuild the filter.
    private var rateCalibrated = false
    private var firstTimeS = Double.NaN
    private var sampleCount = 0

    private var lastTimeS = Double.NaN
    private var lastAccel = 0.0
    private var rawV = 0.0
    private var anchorOffset = 0.0
    private var stableRejectedWindows = 0

    private var quietWindowStartS = Double.NaN
    private var quietWindowLo = 0.0
    private var quietWindowHi = 0.0

    private var runType = 0
    private var runStartS = 0.0
    private var runPeak = 0.0

    private var repCount = 0
    private val repVelocities = mutableListOf<Double>()
    private val repPeaks = mutableListOf<Double>()
    private var runVelocitySum = 0.0
    private var runSampleCount = 0
    private var runVelocityMax = 0.0
    private var runDisplacement = 0.0
    private var lastDtS = 0.0

    /** Ecc-first lifts: a concentric only counts after a qualified eccentric (kills walkout/re-rack bumps). */
    private var eccentricPending = false

    var state: LiveSetState = LiveSetState()
        private set

    fun feed(sample: ImuSample): LiveSetState {
        val timeS = sample.timestampMs / 1000.0
        calibrateSampleRate(timeS)
        val accel = filter.process(FrameTransform.verticalLinearAccelMps2(sample, config.gravityMps2))
        if (!lastTimeS.isNaN()) {
            val dt = (timeS - lastTimeS).coerceIn(0.0, MAX_INTEGRATION_DT_S)
            rawV += 0.5 * (accel + lastAccel) * dt
            lastDtS = dt
        }
        lastTimeS = timeS
        lastAccel = accel

        updateZupt(sample, timeS)
        val v = rawV - anchorOffset
        updateRuns(v, timeS)

        state =
            LiveSetState(
                velocityMps = v,
                phase = currentPhase(),
                repCount = repCount,
                currentPhaseElapsedS = if (runType == 0 && repCount == 0) 0.0 else timeS - runStartS,
                repMeanVelocities = repVelocities.toList(),
                repPeakVelocities = repPeaks.toList(),
            )
        return state
    }

    private fun calibrateSampleRate(timeS: Double) {
        if (rateCalibrated) return
        if (firstTimeS.isNaN()) {
            firstTimeS = timeS
            sampleCount = 1
            return
        }
        sampleCount++
        if (sampleCount < RATE_WARMUP_SAMPLES) return
        val span = timeS - firstTimeS
        val measuredHz = (sampleCount - 1) / span
        if (span > 0 && measuredHz in MIN_PLAUSIBLE_HZ..MAX_PLAUSIBLE_HZ) {
            filter = Biquad.lowPass(config.lowPassCutoffHz, measuredHz)
        }
        rateCalibrated = true
    }

    private fun updateZupt(sample: ImuSample, timeS: Double) {
        val quiet =
            abs(FrameTransform.accMagnitudeG(sample) - 1.0) < config.stationaryAccBandG &&
                FrameTransform.gyroMagnitudeDps(sample) < config.stationaryGyroBandDps
        if (quiet) {
            if (quietWindowStartS.isNaN()) {
                quietWindowStartS = timeS
                quietWindowLo = rawV
                quietWindowHi = rawV
            }
            quietWindowLo = minOf(quietWindowLo, rawV)
            quietWindowHi = maxOf(quietWindowHi, rawV)
            if (timeS - quietWindowStartS >= config.minStationaryS) {
                // Anchor only on flat, near-zero windows: a slow eccentric is IMU-quiet
                // but its raw velocity ramps and sits far from the last anchor.
                val stable = quietWindowHi - quietWindowLo <= config.anchorStabilityBandMps
                if (stable && abs(rawV - anchorOffset) <= config.anchorRejectThresholdMps) {
                    anchorOffset = rawV
                    stableRejectedWindows = 0
                } else if (stable) {
                    // Escape hatch: repeated flat windows far from the anchor mean the
                    // integrator drifted past the rejection band. Without this the
                    // tracker locks into a phantom phase for the rest of the set.
                    stableRejectedWindows++
                    if (stableRejectedWindows >= FORCE_ANCHOR_AFTER_STABLE_WINDOWS) {
                        anchorOffset = rawV
                        stableRejectedWindows = 0
                    }
                }
                quietWindowStartS = timeS
                quietWindowLo = rawV
                quietWindowHi = rawV
            }
        } else {
            quietWindowStartS = Double.NaN
        }
    }

    private fun updateRuns(v: Double, timeS: Double) {
        val type =
            when {
                v > config.pauseBandMps -> 1
                v < -config.pauseBandMps -> -1
                else -> 0
            }
        if (type == runType) {
            if (type != 0) {
                runPeak = maxOf(runPeak, abs(v))
                runVelocitySum += v
                runSampleCount++
                runVelocityMax = maxOf(runVelocityMax, v)
                runDisplacement += abs(v) * lastDtS
            }
            return
        }
        // A movement run just ended; count it if it qualified. The displacement
        // gate filters dead-band jitter, walkout steps, and re-rack bumps.
        if (runType != 0) {
            val duration = timeS - runStartS
            val qualified =
                runPeak >= config.startThresholdMps &&
                    duration >= config.minPhaseS &&
                    runDisplacement >= config.minRomM
            if (qualified) onQualifiedRun(runType)
        }
        runType = type
        runStartS = timeS
        runPeak = abs(v)
        runVelocitySum = v
        runSampleCount = 1
        runVelocityMax = v
        runDisplacement = abs(v) * lastDtS
    }

    private fun onQualifiedRun(direction: Int) {
        val concentric = direction == 1
        if (startsWith == StartPhase.ECCENTRIC) {
            // Pair phases: down arms the rep, the following up completes it.
            if (!concentric) {
                eccentricPending = true
            } else if (eccentricPending) {
                eccentricPending = false
                countRep()
            }
        } else if (concentric) {
            countRep()
        }
    }

    private fun countRep() {
        repCount++
        if (runSampleCount > 0) repVelocities += runVelocitySum / runSampleCount
        repPeaks += runVelocityMax
    }

    private companion object {
        const val RATE_WARMUP_SAMPLES = 12
        const val MIN_PLAUSIBLE_HZ = 4.0
        const val MAX_PLAUSIBLE_HZ = 250.0
        const val FORCE_ANCHOR_AFTER_STABLE_WINDOWS = 2
        const val MAX_INTEGRATION_DT_S = 0.15
    }

    private fun currentPhase(): Phase = when (runType) {
        1 -> Phase.CONCENTRIC
        -1 -> Phase.ECCENTRIC
        else ->
            when {
                repCount == 0 && startsWith == StartPhase.ECCENTRIC -> Phase.IDLE
                startsWith == StartPhase.ECCENTRIC -> Phase.TOP_PAUSE
                else -> Phase.BOTTOM_PAUSE
            }
    }
}
