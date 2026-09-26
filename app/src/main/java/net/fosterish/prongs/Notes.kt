package net.fosterish.prongs

import kotlin.math.log2
import kotlin.math.pow

/** Equal-temperament conversions between MIDI numbers, frequencies and cents. */
object Notes {

    const val A4_MIDI = 69
    const val DEFAULT_A4_HZ = 440.0

    /** Bounds on the notes chromatic mode will name: B0 to C8. */
    const val MIN_MIDI = 23
    const val MAX_MIDI = 108

    private val NAMES = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

    fun frequency(midi: Double, a4Hz: Double = DEFAULT_A4_HZ): Double =
        a4Hz * 2.0.pow((midi - A4_MIDI) / 12.0)

    fun frequency(midi: Int, a4Hz: Double = DEFAULT_A4_HZ): Double =
        frequency(midi.toDouble(), a4Hz)

    fun midi(frequencyHz: Double, a4Hz: Double = DEFAULT_A4_HZ): Double =
        A4_MIDI + 12.0 * log2(frequencyHz / a4Hz)

    /** Signed cents from [targetHz] to [frequencyHz]; positive means sharp. */
    fun cents(frequencyHz: Double, targetHz: Double): Double =
        1200.0 * log2(frequencyHz / targetHz)

    fun pitchClass(midi: Int): Int = Math.floorMod(midi, 12)

    fun octave(midi: Int): Int = Math.floorDiv(midi, 12) - 1

    fun name(midi: Int): String = NAMES[pitchClass(midi)]

    fun nameOf(pitchClass: Int): String = NAMES[Math.floorMod(pitchClass, 12)]

    fun midiOf(pitchClass: Int, octave: Int): Int = (octave + 1) * 12 + pitchClass
}
