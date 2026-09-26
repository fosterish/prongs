package net.fosterish.prongs

import kotlin.math.PI
import kotlin.math.abs

/**
 * The 1 Euro filter (Casiez, Roussel and Vogel, CHI 2012): cutoff rises with signal speed.
 *
 * [derivativeCutoffHz] is the subtle one - the speed estimate is itself noisy, so filtering it
 * lightly lets jitter masquerade as movement and hold the filter open. Defaults swept at 23Hz
 * frames give +/-1.5 cents of jitter, 9 cents of retune lag.
 */
class OneEuroFilter(
    private val minCutoffHz: Double = 0.2,
    private val beta: Double = 2.5,
    private val derivativeCutoffHz: Double = 0.2,
) {

    private var lastValue = Double.NaN
    private var lastFiltered = Double.NaN
    private var lastDerivative = 0.0
    private var lastTimeNanos = 0L

    fun filter(value: Double, timeNanos: Long): Double {
        if (lastFiltered.isNaN()) {
            lastValue = value
            lastFiltered = value
            lastTimeNanos = timeNanos
            return value
        }

        val dt = (timeNanos - lastTimeNanos) / 1e9
        if (dt <= 0.0) return lastFiltered
        lastTimeNanos = timeNanos

        val derivative = (value - lastValue) / dt
        lastDerivative += alpha(derivativeCutoffHz, dt) * (derivative - lastDerivative)

        val cutoff = minCutoffHz + beta * abs(lastDerivative)
        lastFiltered += alpha(cutoff, dt) * (value - lastFiltered)
        lastValue = value
        return lastFiltered
    }

    fun reset() {
        lastValue = Double.NaN
        lastFiltered = Double.NaN
        lastDerivative = 0.0
        lastTimeNanos = 0L
    }

    private fun alpha(cutoffHz: Double, dt: Double): Double {
        val tau = 1.0 / (2.0 * PI * cutoffHz)
        return 1.0 / (1.0 + tau / dt)
    }
}
