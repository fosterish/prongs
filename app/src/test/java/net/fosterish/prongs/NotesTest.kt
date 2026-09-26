package net.fosterish.prongs

import org.junit.Assert.assertEquals
import org.junit.Test

class NotesTest {

    @Test
    fun referencePitchesHaveKnownFrequencies() {
        assertEquals(440.0, Notes.frequency(69), 1e-9)
        assertEquals(261.6256, Notes.frequency(60), 1e-3)
        assertEquals(82.4069, Notes.frequency(40), 1e-3)
        assertEquals(4186.009, Notes.frequency(108), 1e-2)
    }

    @Test
    fun frequencyAndMidiRoundTrip() {
        for (midi in Notes.MIN_MIDI..Notes.MAX_MIDI) {
            assertEquals(midi.toDouble(), Notes.midi(Notes.frequency(midi)), 1e-9)
        }
    }

    @Test
    fun centsAreSignedAndSymmetric() {
        assertEquals(0.0, Notes.cents(440.0, 440.0), 1e-9)
        assertEquals(1200.0, Notes.cents(880.0, 440.0), 1e-9)
        assertEquals(-1200.0, Notes.cents(220.0, 440.0), 1e-9)
        // A semitone up from A4 is 100 cents.
        assertEquals(100.0, Notes.cents(Notes.frequency(70), 440.0), 1e-9)
    }

    @Test
    fun namingFollowsScientificPitchNotation() {
        fun spelled(midi: Int) = "${Notes.name(midi)}${Notes.octave(midi)}"
        assertEquals("C4", spelled(60))
        assertEquals("A4", spelled(69))
        assertEquals("E2", spelled(40))
        assertEquals("C-1", spelled(0))
        assertEquals("F#2", spelled(42))
    }

    @Test
    fun midiOfInvertsPitchClassAndOctave() {
        for (midi in 0..127) {
            assertEquals(midi, Notes.midiOf(Notes.pitchClass(midi), Notes.octave(midi)))
        }
    }

    @Test
    fun alternateConcertPitchShiftsEverything() {
        assertEquals(432.0, Notes.frequency(69, a4Hz = 432.0), 1e-9)
        assertEquals(69.0, Notes.midi(432.0, a4Hz = 432.0), 1e-9)
    }
}
