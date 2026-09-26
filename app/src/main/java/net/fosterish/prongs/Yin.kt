package net.fosterish.prongs

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** [clarity] runs 0..1: clean tones score above 0.99, white noise below 0.07. */
data class PitchEstimate(val frequencyHz: Double, val clarity: Double)

/**
 * YIN fundamental frequency estimator (de Cheveigne & Kawahara, 2002).
 *
 * The 30 Hz floor reaches B0, the bottom of [Notes.MIN_MIDI], which needs lags out to 1600 of
 * the 2047 a 4096 window allows - so bounding the search buys correctness, not speed.
 * [differenceFunction] is the app's one real cost: ~2.3ms per frame against a 43ms hop.
 */
class Yin(
    private val sampleRate: Int,
    private val windowSize: Int,
    minFrequencyHz: Double = 30.0,
    maxFrequencyHz: Double = 2200.0,
    private val threshold: Double = 0.12,
) {

    private val minLag: Int = max(2, floor(sampleRate / maxFrequencyHz).toInt())
    private val maxLag: Int = min(windowSize / 2 - 1, ceil(sampleRate / minFrequencyHz).toInt())

    /** One lag of headroom past the search range so the lowest note can still be interpolated. */
    private val computedLag: Int = maxLag + 1

    /** Samples summed for each lag. Larger than the usual windowSize/2, so high notes average more. */
    private val integrationLength: Int = windowSize - computedLag

    private val difference = DoubleArray(computedLag + 1)
    private val normalized = DoubleArray(computedLag + 1)

    init {
        require(minLag < maxLag) { "frequency range is empty for this sample rate" }
        require(integrationLength > computedLag) { "window of $windowSize is too short for ${minFrequencyHz}Hz" }
    }

    /** [buffer] must hold at least [windowSize] samples. */
    fun detect(buffer: FloatArray): PitchEstimate? {
        differenceFunction(buffer)
        cumulativeMeanNormalize()

        val lag = absoluteThreshold() ?: return null
        val refined = parabolicInterpolation(lag)
        if (refined <= 0.0) return null

        val clarity = (1.0 - normalized[lag]).coerceIn(0.0, 1.0)
        return PitchEstimate(sampleRate / refined, clarity)
    }

    private fun differenceFunction(buffer: FloatArray) {
        difference[0] = 0.0
        for (lag in 1..computedLag) {
            var sum = 0.0
            for (i in 0 until integrationLength) {
                val delta = buffer[i] - buffer[i + lag]
                sum += delta.toDouble() * delta.toDouble()
            }
            difference[lag] = sum
        }
    }

    private fun cumulativeMeanNormalize() {
        normalized[0] = 1.0
        var runningSum = 0.0
        for (lag in 1..computedLag) {
            runningSum += difference[lag]
            normalized[lag] = if (runningSum == 0.0) 1.0 else difference[lag] * lag / runningSum
        }
    }

    /**
     * First lag dipping below the threshold, walked down to the bottom of that dip. The global
     * minimum fallback lets a weak signal register, with low clarity flagging it as such.
     */
    private fun absoluteThreshold(): Int? {
        var lag = minLag
        while (lag <= maxLag) {
            if (normalized[lag] < threshold) {
                while (lag + 1 <= maxLag && normalized[lag + 1] < normalized[lag]) lag++
                return lag
            }
            lag++
        }

        var best = minLag
        for (candidate in minLag..maxLag) {
            if (normalized[candidate] < normalized[best]) best = candidate
        }
        return if (normalized[best] < 1.0) best else null
    }

    /**
     * Sub-sample refinement, worth an order of magnitude in accuracy. Neighbours outside the
     * searched range are still valid, and excluding them quantises hard at the range edges.
     */
    private fun parabolicInterpolation(lag: Int): Double {
        val left = if (lag > 1) lag - 1 else lag
        val right = if (lag < computedLag) lag + 1 else lag
        if (left == lag) return if (normalized[lag] <= normalized[right]) lag.toDouble() else right.toDouble()
        if (right == lag) return if (normalized[lag] <= normalized[left]) lag.toDouble() else left.toDouble()

        val a = normalized[left]
        val b = normalized[lag]
        val c = normalized[right]
        val denominator = 2.0 * (2.0 * b - c - a)
        return if (denominator == 0.0) lag.toDouble() else lag + (c - a) / denominator
    }
}
