package net.fosterish.prongs

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SignalGateTest {

    private val loud = 0.05

    @Test
    fun aConfidentLoudFrameAcquires() {
        val gate = SignalGate()
        assertTrue(gate.accept(loud, clarity = 0.98))
        assertTrue(gate.isLocked)
    }

    @Test
    fun aMarginalFrameCannotAcquireButCanSustain() {
        val gate = SignalGate()
        // 0.70 sits between the sustain and acquire thresholds.
        assertFalse("should not start tracking on a marginal frame", gate.accept(loud, 0.70))

        assertTrue(gate.accept(loud, 0.95))
        assertTrue("an acquired note should survive at 0.70", gate.accept(loud, 0.70))
        assertTrue(gate.accept(loud, 0.64))
    }

    /** The decay curve measured from a synthesised plucked low E. */
    @Test
    fun followsAPluckedNoteDownItsDecayThenLetsGo() {
        val gate = SignalGate()
        val decay = doubleArrayOf(1.00, 1.00, 0.999, 0.996, 0.983, 0.934, 0.71, 0.65)
        for ((index, clarity) in decay.withIndex()) {
            assertTrue("dropped the note at step $index (clarity $clarity)", gate.accept(loud, clarity))
        }
        assertFalse("0.39 is noise, not a note", gate.accept(loud, 0.39))
    }

    /**
     * A note that misses the acquire bar must not teach the gate its own level as the noise
     * floor, or every following frame faces a higher bar than the last.
     */
    @Test
    fun aLoudUnacquiredNoteDoesNotRaiseTheFloorAgainstItself() {
        val gate = SignalGate()
        repeat(60) { gate.accept(loud, clarity = 0.75) }

        assertTrue("the same level must still be audible", gate.isAudible(loud))
        assertTrue("and a clean frame must still acquire", gate.accept(loud, 0.95))
    }

    @Test
    fun theFloorDropsBackAsSoonAsTheRoomGoesQuiet() {
        val gate = SignalGate()
        repeat(200) { gate.accept(0.02, clarity = 0.3) }
        assertFalse(gate.isAudible(0.01))

        gate.accept(0.0005, clarity = 0.1)
        assertTrue("a quiet room should not stay gated", gate.isAudible(0.01))
    }

    @Test
    fun whiteNoiseNeverAcquires() {
        val gate = SignalGate()
        for (clarity in doubleArrayOf(0.068, 0.060, 0.051, 0.051, 0.07, 0.05)) {
            assertFalse(gate.accept(loud, clarity))
        }
        assertFalse(gate.isLocked)
    }

    @Test
    fun aBriefDropoutKeepsTheLockSoSustainStillApplies() {
        val gate = SignalGate()
        gate.accept(loud, 0.98)

        assertFalse(gate.accept(loud, 0.2))
        assertTrue("lock should survive a couple of bad frames", gate.isLocked)
        assertTrue("so a 0.80 frame is still enough", gate.accept(loud, 0.80))
    }

    @Test
    fun aSustainedDropoutReleasesTheLockAndRequiresReacquisition() {
        val gate = SignalGate()
        gate.accept(loud, 0.98)
        repeat(4) { gate.accept(loud, 0.2) }

        assertFalse(gate.isLocked)
        assertFalse("must clear the harder acquire threshold again", gate.accept(loud, 0.80))
    }

    @Test
    fun silenceIsNotAudibleAndNeverAcquires() {
        val gate = SignalGate()
        assertFalse(gate.isAudible(0.0))
        assertFalse(gate.accept(0.0, clarity = 1.0))
    }

    @Test
    fun aNoisyRoomRaisesTheLoudnessFloor() {
        val gate = SignalGate()
        // A steady hiss well above the absolute floor but below anything worth calling a note.
        repeat(200) { gate.accept(0.02, clarity = 0.3) }

        assertFalse("a quiet signal is now below the learned floor", gate.isAudible(0.01))
        assertFalse(gate.accept(0.01, clarity = 1.0))
        assertTrue("a genuinely loud note still gets through", gate.accept(0.5, clarity = 0.98))
    }

    @Test
    fun theLearnedFloorIsNotPoisonedByTheNoteItself() {
        val gate = SignalGate()
        repeat(200) { gate.accept(0.5, clarity = 0.98) }

        // Having heard nothing but a loud note, a quiet one must still register.
        assertTrue(gate.isAudible(0.01))
    }

    @Test
    fun resetClearsLockAndLearnedFloor() {
        val gate = SignalGate()
        repeat(200) { gate.accept(0.02, clarity = 0.3) }
        gate.reset()

        assertFalse(gate.isLocked)
        assertTrue(gate.isAudible(0.01))
    }
}
