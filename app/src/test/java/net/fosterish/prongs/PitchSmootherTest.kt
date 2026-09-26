package net.fosterish.prongs

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PitchSmootherTest {

    private val frameNanos = (2048.0 / 48_000.0 * 1e9).toLong()

    private fun feed(smoother: PitchSmoother, values: DoubleArray, startTime: Long = 0): Double {
        var t = startTime
        var last = 0.0
        for (value in values) {
            t += frameNanos
            last = smoother.accept(value, t)
        }
        return last
    }

    @Test
    fun aSteadyNoteIsReportedAsItself() {
        val smoother = PitchSmoother()
        assertEquals(440.0, feed(smoother, DoubleArray(40) { 440.0 }), 1e-6)
    }

    @Test
    fun theFirstFrameIsNotSmoothedAway() {
        val smoother = PitchSmoother()
        assertEquals(440.0, smoother.accept(440.0, frameNanos), 1e-9)
    }

    @Test
    fun anIsolatedOctaveErrorIsRejected() {
        val smoother = PitchSmoother()
        feed(smoother, DoubleArray(20) { 440.0 })

        val afterGlitch = smoother.accept(880.0, 21 * frameNanos)
        assertTrue(
            "a single octave jump should not move the output, got $afterGlitch",
            abs(Notes.cents(afterGlitch, 440.0)) < 5.0,
        )
    }

    @Test
    fun aSustainedNewNoteIsAdoptedQuickly() {
        val smoother = PitchSmoother()
        feed(smoother, DoubleArray(20) { 440.0 })

        smoother.accept(587.33, 21 * frameNanos)
        val adopted = smoother.accept(587.33, 22 * frameNanos)
        assertEquals("should snap to the new string, not slide", 587.33, adopted, 0.01)
    }

    @Test
    fun alternatingGlitchesDoNotAccumulateIntoAJump() {
        val smoother = PitchSmoother()
        feed(smoother, DoubleArray(10) { 440.0 })

        var t = 10 * frameNanos
        repeat(6) {
            t += frameNanos
            smoother.accept(880.0, t)
            t += frameNanos
            smoother.accept(440.0, t)
        }
        assertEquals(440.0, smoother.accept(440.0, t + frameNanos), 1.0)
    }

    @Test
    fun jitterAroundATargetIsSmoothedTowardTheCentre() {
        val smoother = PitchSmoother()
        var t = 0L
        var last = 0.0
        // Alternating +/-8 cents, which should average out rather than be tracked.
        repeat(80) { i ->
            t += frameNanos
            val offset = if (i % 2 == 0) 8.0 else -8.0
            last = smoother.accept(Notes.frequency(69 + offset / 100.0), t)
        }
        assertTrue(
            "expected to settle near A440, got $last",
            abs(Notes.cents(last, 440.0)) < 3.0,
        )
    }

    @Test
    fun resetDropsTheHeldPitch() {
        val smoother = PitchSmoother()
        feed(smoother, DoubleArray(20) { 440.0 })

        // Without the reset this jump would be held back for confirmation; see the glitch tests.
        smoother.reset()
        assertEquals(880.0, smoother.accept(880.0, frameNanos), 1e-9)
    }
}
