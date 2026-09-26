package net.fosterish.prongs

import kotlin.math.abs
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OneEuroFilterTest {

    /** The tuner's real frame interval: 2048 samples at 48 kHz. */
    private val frameNanos = (2048.0 / 48_000.0 * 1e9).toLong()

    private fun run(filter: OneEuroFilter, values: DoubleArray): DoubleArray {
        var t = 0L
        return DoubleArray(values.size) { i ->
            t += frameNanos
            filter.filter(values[i], t)
        }
    }

    @Test
    fun aConstantSignalPassesThroughUnchanged() {
        val out = run(OneEuroFilter(), DoubleArray(50) { 69.0 })
        assertEquals(69.0, out.last(), 1e-9)
    }

    @Test
    fun jitterOnAHeldNoteStaysWellInsideTheInTuneBand() {
        val random = Random(4)
        // +/-6 cents of noise on a held A4, about what YIN produces frame to frame.
        val noisy = DoubleArray(200) { 69.0 + (random.nextDouble() * 2 - 1) * 0.06 }
        val out = run(OneEuroFilter(), noisy)

        val spreadCents = out.drop(50).let { it.max() - it.min() } * 100
        assertTrue("residual jitter was $spreadCents cents", spreadCents < 3.5)
    }

    @Test
    fun aDeliberateRetuneIsFollowedFarFasterThanTheRestingCutoffWould() {
        // Sliding a semitone over about a second, the speed a peg actually moves.
        val ramp = DoubleArray(24) { 69.0 + it * (1.0 / 24.0) }
        val adaptive = run(OneEuroFilter(), ramp)

        // A fixed low-pass at the same resting cutoff, for comparison.
        val fixed = OneEuroFilter(minCutoffHz = 0.4, beta = 0.0)
        val nonAdaptive = run(fixed, ramp)

        val adaptiveLag = abs(ramp.last() - adaptive.last())
        val fixedLag = abs(ramp.last() - nonAdaptive.last())
        assertTrue(
            "adaptive lag $adaptiveLag should beat fixed lag $fixedLag",
            adaptiveLag < fixedLag / 2,
        )
    }

    @Test
    fun itConvergesOnAStepRatherThanOvershooting() {
        val step = DoubleArray(120) { if (it < 20) 69.0 else 70.0 }
        val out = run(OneEuroFilter(), step)

        assertTrue("never exceeds the target", out.all { it <= 70.0 + 1e-9 })
        assertEquals("settles on it", 70.0, out.last(), 0.01)
    }

    @Test
    fun outOfOrderOrDuplicateTimestampsDoNotCorruptTheState() {
        val filter = OneEuroFilter()
        filter.filter(69.0, 1_000_000)
        val settled = filter.filter(69.5, 2_000_000)
        assertEquals(settled, filter.filter(80.0, 2_000_000), 1e-9)
        assertEquals(settled, filter.filter(80.0, 1_500_000), 1e-9)
    }

    @Test
    fun resetForgetsPreviousHistory() {
        val filter = OneEuroFilter()
        run(filter, DoubleArray(50) { 69.0 })
        filter.reset()
        assertEquals(50.0, filter.filter(50.0, frameNanos), 1e-9)
    }
}
