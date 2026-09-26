package net.fosterish.prongs

import kotlin.math.abs
import kotlin.math.roundToInt

data class TuningReading(
    val targetMidi: Int,
    val targetHz: Double,
    val detectedHz: Double,
    val cents: Double,
)

/**
 * Decides which target note the meter is showing. Empty targets mean chromatic.
 *
 * A challenger takes the display only once it is:
 *
 *  - [switchWithinCents]: genuinely near, not just nearer than the incumbent. Between widely
 *    spaced targets a transient is hundreds of cents from both, yet still closer to one.
 *  - [switchMarginCents]: ahead by a margin, so a pitch midway between semitones cannot flicker.
 *  - [dwellMillis]: holding that lead, so an attack transient cannot steal the display.
 */
class TargetSelector(
    a4Hz: Double = Notes.DEFAULT_A4_HZ,
    private val switchMarginCents: Double = 35.0,
    private val switchWithinCents: Double = 60.0,
    private val dwellMillis: Long = 250L,
) {

    /** Concert-A reference. Set from the main thread, read on the audio thread. */
    @Volatile
    var a4Hz: Double = a4Hz

    private var currentTarget: Int? = null
    private var challenger: Int? = null
    private var challengerSinceMillis: Long = 0L

    fun reset() {
        currentTarget = null
        challenger = null
        challengerSinceMillis = 0L
    }

    fun select(detectedHz: Double, targets: Set<Int>, nowMillis: Long): TuningReading {
        // In MIDI space every distance below is a subtraction, so the logarithm is paid once.
        val detectedMidi = Notes.midi(detectedHz, a4Hz)
        val best = bestCandidate(detectedMidi, targets)
        val held = currentTarget

        val target = when {
            held == null -> adopt(best)
            targets.isNotEmpty() && held !in targets -> adopt(best)
            best == held -> { challenger = null; held }
            distanceCents(detectedMidi, best) > switchWithinCents -> { challenger = null; held }
            improvementOver(held, best, detectedMidi) <= switchMarginCents -> { challenger = null; held }
            else -> {
                if (challenger != best) {
                    challenger = best
                    challengerSinceMillis = nowMillis
                }
                if (nowMillis - challengerSinceMillis >= dwellMillis) adopt(best) else held
            }
        }

        val targetHz = Notes.frequency(target, a4Hz)
        return TuningReading(target, targetHz, detectedHz, Notes.cents(detectedHz, targetHz))
    }

    private fun adopt(target: Int): Int {
        currentTarget = target
        challenger = null
        return target
    }

    /** How many cents closer [candidate] is than [held]. */
    private fun improvementOver(held: Int, candidate: Int, detectedMidi: Double): Double =
        distanceCents(detectedMidi, held) - distanceCents(detectedMidi, candidate)

    private fun distanceCents(detectedMidi: Double, midi: Int): Double =
        abs(detectedMidi - midi) * 100.0

    private fun bestCandidate(detectedMidi: Double, targets: Set<Int>): Int {
        if (targets.isEmpty()) {
            return detectedMidi.roundToInt().coerceIn(Notes.MIN_MIDI, Notes.MAX_MIDI)
        }
        return targets.minBy { abs(detectedMidi - it) }
    }
}
