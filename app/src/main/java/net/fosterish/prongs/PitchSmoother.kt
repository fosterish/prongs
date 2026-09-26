package net.fosterish.prongs

import kotlin.math.abs

/**
 * Stabilises the raw per-frame pitch stream.
 *
 * Filtering runs in the MIDI domain so it behaves the same at every pitch. Jumps too large to
 * be a retune wait for a repeat, discarding YIN's single-frame octave errors.
 */
class PitchSmoother(
    private val jumpThresholdCents: Double = 60.0,
    private val framesToConfirmJump: Int = 2,
    private val filter: OneEuroFilter = OneEuroFilter(),
) {

    private var currentMidi = Double.NaN
    private var pendingMidi = Double.NaN
    private var pendingCount = 0

    fun accept(frequencyHz: Double, timeNanos: Long): Double {
        val midi = Notes.midi(frequencyHz)

        val isContinuation = currentMidi.isNaN() ||
            abs(midi - currentMidi) * 100.0 < jumpThresholdCents

        if (isContinuation) {
            pendingCount = 0
            pendingMidi = Double.NaN
            currentMidi = filter.filter(midi, timeNanos)
            return Notes.frequency(currentMidi)
        }

        val continuesPendingJump = !pendingMidi.isNaN() &&
            abs(midi - pendingMidi) * 100.0 < jumpThresholdCents
        pendingCount = if (continuesPendingJump) pendingCount + 1 else 1
        pendingMidi = midi

        if (pendingCount >= framesToConfirmJump) {
            // A confirmed new note starts the filter over rather than sliding into it.
            filter.reset()
            currentMidi = filter.filter(midi, timeNanos)
            pendingCount = 0
            pendingMidi = Double.NaN
        }
        return Notes.frequency(currentMidi)
    }

    fun reset() {
        currentMidi = Double.NaN
        pendingMidi = Double.NaN
        pendingCount = 0
        filter.reset()
    }
}
