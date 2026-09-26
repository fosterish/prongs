package net.fosterish.prongs

import org.junit.Assert.assertEquals
import org.junit.Test

class TargetSelectorTest {

    private val e2 = 40
    private val a2 = 45
    private val a4 = 69
    private val guitar = setOf(40, 45, 50, 55, 59, 64)

    private fun hzAtCents(midi: Int, cents: Double) = Notes.frequency(midi + cents / 100.0)

    @Test
    fun chromaticModeLocksOntoTheNearestSemitone() {
        val selector = TargetSelector()
        val reading = selector.select(Notes.frequency(a4), emptySet(), nowMillis = 0)
        assertEquals(a4, reading.targetMidi)
        assertEquals(440.0, reading.targetHz, 1e-9)
        assertEquals(0.0, reading.cents, 1e-9)
    }

    @Test
    fun centsAreReportedRelativeToTheChosenTarget() {
        val selector = TargetSelector()
        val reading = selector.select(hzAtCents(a4, 17.0), emptySet(), nowMillis = 0)
        assertEquals(a4, reading.targetMidi)
        assertEquals(17.0, reading.cents, 1e-6)
    }

    @Test
    fun chromaticModeHoldsPastTheMidpointBetweenSemitones() {
        val selector = TargetSelector()
        var now = 0L
        selector.select(Notes.frequency(a4), emptySet(), now)

        // Drifting sharp past 50 cents would normally hand over to A#4 immediately.
        for (cents in intArrayOf(40, 48, 52, 60, 66)) {
            now += 50
            val reading = selector.select(hzAtCents(a4, cents.toDouble()), emptySet(), now)
            assertEquals("held target at $cents cents", a4, reading.targetMidi)
        }

        // Past the margin, the challenger starts its dwell but does not take over yet.
        now += 50
        val challengeStart = now
        assertEquals(a4, selector.select(hzAtCents(a4, 80.0), emptySet(), now).targetMidi)

        now = challengeStart + 249
        assertEquals(a4, selector.select(hzAtCents(a4, 80.0), emptySet(), now).targetMidi)
        now = challengeStart + 250
        assertEquals(a4 + 1, selector.select(hzAtCents(a4, 80.0), emptySet(), now).targetMidi)
    }

    @Test
    fun picksTheNearestSelectedTargetIgnoringUnselectedNotes() {
        val selector = TargetSelector()
        // Playing a C#3 (49) when only guitar strings are selected: D3 (50) is nearest.
        val reading = selector.select(Notes.frequency(49), guitar, nowMillis = 0)
        assertEquals(50, reading.targetMidi)
    }

    @Test
    fun aSingleStrayFrameDoesNotStealTheDisplay() {
        val selector = TargetSelector()
        var now = 0L
        selector.select(Notes.frequency(e2), guitar, now)

        now += 40
        val glitch = selector.select(Notes.frequency(a2), guitar, now)
        assertEquals("one frame should not be enough to switch", e2, glitch.targetMidi)

        now += 40
        assertEquals(e2, selector.select(Notes.frequency(e2), guitar, now).targetMidi)
    }

    @Test
    fun aSustainedNewNoteTakesOverAfterTheDwell() {
        val selector = TargetSelector()
        var now = 0L
        selector.select(Notes.frequency(e2), guitar, now)

        // The dwell starts here, and 249ms later the challenger still has not earned the display.
        now += 40
        assertEquals(e2, selector.select(Notes.frequency(a2), guitar, now).targetMidi)
        now += 249
        assertEquals(e2, selector.select(Notes.frequency(a2), guitar, now).targetMidi)
        now += 1
        assertEquals(a2, selector.select(Notes.frequency(a2), guitar, now).targetMidi)
    }

    @Test
    fun dwellRestartsWhenTheChallengerChanges() {
        val selector = TargetSelector()
        var now = 0L
        selector.select(Notes.frequency(e2), guitar, now)

        now += 200
        selector.select(Notes.frequency(a2), guitar, now)
        // A second challenger restarts the clock: 200ms of A2 plus 200ms of G3 is not a switch.
        now += 200
        val secondChallenge = now
        assertEquals(e2, selector.select(Notes.frequency(55), guitar, now).targetMidi)

        now = secondChallenge + 249
        assertEquals(e2, selector.select(Notes.frequency(55), guitar, now).targetMidi)
        now = secondChallenge + 250
        assertEquals(55, selector.select(Notes.frequency(55), guitar, now).targetMidi)
    }

    @Test
    fun aPitchBetweenWidelySpacedTargetsNeverTakesOver() {
        val selector = TargetSelector()
        var now = 0L
        selector.select(Notes.frequency(e2), guitar, now)

        // Halfway between A2 and D3: 250 cents from either, so closer to one but near neither.
        val betweenTargets = Notes.frequency(47.5)
        repeat(50) {
            now += 40
            assertEquals(
                "a pitch nobody played must not take the display",
                e2,
                selector.select(betweenTargets, guitar, now).targetMidi,
            )
        }
    }

    @Test
    fun aChallengerTakesOverOnceThePitchIsGenuinelyNearIt() {
        val selector = TargetSelector()
        var now = 0L
        selector.select(Notes.frequency(e2), guitar, now)

        now += 40
        selector.select(Notes.frequency(47.5), guitar, now)

        // Landing within the absolute bound of A2 does earn the display, after the dwell.
        val nearA2 = hzAtCents(a2, 40.0)
        now += 40
        assertEquals(e2, selector.select(nearA2, guitar, now).targetMidi)
        now += 250
        assertEquals(a2, selector.select(nearA2, guitar, now).targetMidi)
    }

    @Test
    fun theAbsoluteBoundNeverBlocksChromaticMode() {
        val selector = TargetSelector()
        var now = 0L
        selector.select(Notes.frequency(a4), emptySet(), now)

        // Every pitch is within 50 cents of some semitone, so the bound can never bite here.
        now += 40
        selector.select(hzAtCents(a4, 180.0), emptySet(), now)
        now += 250
        assertEquals(a4 + 2, selector.select(hzAtCents(a4, 180.0), emptySet(), now).targetMidi)
    }

    @Test
    fun deselectingTheDisplayedTargetSwitchesImmediately() {
        val selector = TargetSelector()
        selector.select(Notes.frequency(e2), guitar, nowMillis = 0)

        val remaining = guitar - e2
        val reading = selector.select(Notes.frequency(e2), remaining, nowMillis = 10)
        assertEquals(45, reading.targetMidi)
    }

    @Test
    fun resetClearsTheHeldTarget() {
        val selector = TargetSelector()
        selector.select(Notes.frequency(e2), guitar, nowMillis = 0)
        selector.reset()
        assertEquals(a2, selector.select(Notes.frequency(a2), guitar, nowMillis = 10).targetMidi)
    }

    @Test
    fun alternateConcertPitchMovesTheTargetFrequency() {
        val selector = TargetSelector(a4Hz = 432.0)
        val reading = selector.select(432.0, emptySet(), nowMillis = 0)
        assertEquals(a4, reading.targetMidi)
        assertEquals(432.0, reading.targetHz, 1e-9)
        assertEquals(0.0, reading.cents, 1e-9)
    }
}
