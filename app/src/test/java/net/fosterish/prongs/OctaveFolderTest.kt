package net.fosterish.prongs

import org.junit.Assert.assertEquals
import org.junit.Test

class OctaveFolderTest {

    private val g3 = 55
    private val e2 = 40
    private val e3 = 52
    private val e4 = 64
    private val mandolin = setOf(55, 62, 69, 76)
    private val guitar = setOf(40, 45, 50, 55, 59, 64)

    private fun hzAtCents(midi: Int, cents: Double) = Notes.frequency(midi + cents / 100.0)

    private fun foldedMidi(
        frequencyHz: Double,
        targets: Set<Int>,
        folder: OctaveFolder = OctaveFolder(),
    ) = Notes.midi(folder.fold(frequencyHz, targets).frequencyHz)

    @Test
    fun chromaticModeLeavesEveryReadingAlone() {
        val octaveUp = Notes.frequency(g3 + 12)
        assertEquals(octaveUp, OctaveFolder().fold(octaveUp, emptySet()).frequencyHz, 1e-9)
    }

    @Test
    fun aHarmonicAnOctaveUpFoldsDownToTheTarget() {
        assertEquals(g3.toDouble(), foldedMidi(Notes.frequency(g3 + 12), mandolin), 1e-9)
    }

    @Test
    fun aSubharmonicAnOctaveDownFoldsUpToTheTarget() {
        assertEquals(g3.toDouble(), foldedMidi(Notes.frequency(g3 - 12), mandolin), 1e-9)
    }

    @Test
    fun twoOctavesFoldTheWholeWay() {
        assertEquals(g3.toDouble(), foldedMidi(Notes.frequency(g3 + 24), mandolin), 1e-9)
    }

    @Test
    fun foldingCarriesTheTuningErrorWithIt() {
        // A string 30 cents flat, heard an octave up, is still 30 cents flat once folded.
        val cents = Notes.cents(
            OctaveFolder().fold(hzAtCents(g3 + 12, -30.0), mandolin).frequencyHz,
            Notes.frequency(g3),
        )
        assertEquals(-30.0, cents, 1e-6)
    }

    @Test
    fun aTargetedOctaveIsThePlayersChoiceAndSurvives() {
        // E4 is a guitar string in its own right, so hearing it must not fold it onto E2.
        assertEquals(e4.toDouble(), foldedMidi(Notes.frequency(e4), guitar), 1e-9)
    }

    @Test
    fun aDifferentPitchClassIsLeftWrong() {
        // F3 is no octave of any mandolin string, so it is a wrong note rather than an error.
        assertEquals(53.0, foldedMidi(Notes.frequency(53), mandolin), 1e-9)
    }

    @Test
    fun aReadingTooFarOffPitchToBeAnOctaveIsLeftAlone() {
        // 70 cents from the octave would still be off scale after folding, so it is not folded.
        val detected = hzAtCents(g3 + 12, 70.0)
        assertEquals(Notes.midi(detected), foldedMidi(detected, mandolin), 1e-9)
    }

    @Test
    fun theToleranceBoundIsConfigurable() {
        val detected = hzAtCents(g3 + 12, 70.0)
        val generous = OctaveFolder(toleranceCents = 80.0)
        assertEquals(g3 + 0.70, foldedMidi(detected, mandolin, generous), 1e-6)
    }

    @Test
    fun theNearerOctaveWins() {
        // E5 is one octave above the E4 string and three above the E2 one.
        assertEquals(e4.toDouble(), foldedMidi(Notes.frequency(e4 + 12), setOf(e2, e4)), 1e-9)
    }

    @Test
    fun equidistantOctavesResolveToTheLowerTargetBeforeAnythingIsHeard() {
        // E3 is one octave from both E2 and E4, so the choice must not depend on set order.
        assertEquals(e2.toDouble(), foldedMidi(Notes.frequency(e3), guitar), 1e-9)
        assertEquals(e2.toDouble(), foldedMidi(Notes.frequency(e3), setOf(e4, e2)), 1e-9)
    }

    @Test
    fun anAmbiguousOctaveFollowsTheLowStringBeingTuned() {
        val folder = OctaveFolder()
        // Tuning the low E, its second harmonic must not be read as the high E gone flat.
        folder.fold(Notes.frequency(e2), guitar)
        assertEquals(e2.toDouble(), foldedMidi(Notes.frequency(e3), guitar, folder), 1e-9)
    }

    @Test
    fun anAmbiguousOctaveFollowsTheHighStringBeingTuned() {
        val folder = OctaveFolder()
        // The same E3, heard while tuning the high E, is that string's pitch halved.
        folder.fold(Notes.frequency(e4), guitar)
        assertEquals(e4.toDouble(), foldedMidi(Notes.frequency(e3), guitar, folder), 1e-9)
    }

    @Test
    fun aNearerOctaveOutranksTheStringBeingTuned() {
        val folder = OctaveFolder()
        folder.fold(Notes.frequency(e2), guitar)
        // E5 is one octave above the high E and three above the low one being tuned.
        assertEquals(e4.toDouble(), foldedMidi(Notes.frequency(e4 + 12), guitar, folder), 1e-9)
    }

    @Test
    fun resetForgetsTheStringBeingTuned() {
        val folder = OctaveFolder()
        folder.fold(Notes.frequency(e4), guitar)
        folder.reset()
        assertEquals(e2.toDouble(), foldedMidi(Notes.frequency(e3), guitar, folder), 1e-9)
    }

    @Test
    fun foldingFollowsAnAlternateConcertPitch() {
        val a4Hz = 432.0
        val folder = OctaveFolder()
        val detected = Notes.frequency(g3 + 12, a4Hz)
        assertEquals(
            Notes.frequency(g3, a4Hz),
            folder.fold(detected, mandolin, a4Hz).frequencyHz,
            1e-9,
        )
    }

    @Test
    fun anOvertoneIsReportedAsOctavesAboveTheTarget() {
        assertEquals(1, OctaveFolder().fold(Notes.frequency(g3 + 12), mandolin).octaves)
        assertEquals(2, OctaveFolder().fold(Notes.frequency(g3 + 24), mandolin).octaves)
    }

    @Test
    fun anUndertoneIsReportedAsOctavesBelowTheTarget() {
        assertEquals(-1, OctaveFolder().fold(Notes.frequency(g3 - 12), mandolin).octaves)
    }

    @Test
    fun aPitchThatStandsAsHeardIsReportedAsNoOctaves() {
        assertEquals(0, OctaveFolder().fold(Notes.frequency(g3), mandolin).octaves)
        assertEquals(0, OctaveFolder().fold(Notes.frequency(53), mandolin).octaves)
        assertEquals(0, OctaveFolder().fold(Notes.frequency(g3 + 12), emptySet()).octaves)
    }
}
