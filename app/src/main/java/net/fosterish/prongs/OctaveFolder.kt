package net.fosterish.prongs

import kotlin.math.abs
import kotlin.math.roundToInt

/** [octaves] is how far above the targeted octave the pitch was heard, and folded back from. */
data class FoldedPitch(val frequencyHz: Double, val octaves: Int)

/**
 * Folds a detected pitch back onto a targeted octave.
 *
 * A string rich in harmonics lets YIN latch onto one, naming a note an octave or more from the
 * one being played. If that note is not targeted but an octave relative of a target is, the
 * octave is the detector's mistake rather than the player's, so the reading is moved back.
 *
 * [toleranceCents] matches the meter's range: a reading is folded only if it would then be on
 * scale, which keeps a genuinely wrong note wrong.
 */
class OctaveFolder(private val toleranceCents: Double = 50.0) {

    /** The last pitch reported, so an ambiguous octave follows the string being tuned. */
    private var lastMidi = Double.NaN

    fun fold(
        frequencyHz: Double,
        targets: Set<Int>,
        a4Hz: Double = Notes.DEFAULT_A4_HZ,
    ): FoldedPitch {
        val midi = Notes.midi(frequencyHz, a4Hz)
        val octaves = octaveShiftOnto(midi, targets)
        lastMidi = midi - 12.0 * octaves
        val folded = if (octaves == 0) frequencyHz else Notes.frequency(lastMidi, a4Hz)
        return FoldedPitch(folded, octaves)
    }

    fun reset() {
        lastMidi = Double.NaN
    }

    /** Octaves to drop to land on a target, zero when the reading stands as it was heard. */
    private fun octaveShiftOnto(midi: Double, targets: Set<Int>): Int {
        // Chromatic mode asks for no octave in particular, so nothing can be out of place.
        if (targets.isEmpty()) return 0
        // Hearing a targeted note makes the octave the player's choice rather than an error.
        if (midi.roundToInt() in targets) return 0

        var shift = 0
        var nearestOctaves = Int.MAX_VALUE
        var bestTieBreak = Double.MAX_VALUE
        for (target in targets) {
            val octaves = ((midi - target) / 12.0).roundToInt()
            if (octaves == 0) continue
            if (abs(midi - target - 12.0 * octaves) * 100.0 > toleranceCents) continue

            val distance = abs(octaves)
            if (distance > nearestOctaves) continue
            // Octaves equally far off go to the string already being tuned. Before anything has
            // been heard the lower target wins, so iteration order cannot decide the reading.
            val tieBreak = if (lastMidi.isNaN()) target.toDouble() else abs(target - lastMidi)
            if (distance < nearestOctaves || tieBreak < bestTieBreak) {
                nearestOctaves = distance
                bestTieBreak = tieBreak
                shift = octaves
            }
        }
        return shift
    }
}
