package net.fosterish.prongs

/**
 * Decides whether a frame is a note or just the room.
 *
 * Keeping a note takes less confidence than starting one. A decaying pluck measures 0.93 at
 * -38dB and 0.71 a further -8dB down, while white noise never passes 0.07, so one threshold
 * would either cut the decay short or let noise grab the display.
 */
class SignalGate(
    private val acquireClarity: Double = 0.82,
    private val sustainClarity: Double = 0.62,
    private val absoluteFloorRms: Double = 0.0012,
    private val noiseMargin: Double = 2.5,
    private val releaseFrames: Int = 4,
    private val noiseCreepPerFrame: Double = 1.01,
) {

    /** True while a note is tracked, and so while the easier sustain threshold applies. */
    var isLocked: Boolean = false
        private set

    private var quietFrames = 0
    private var noiseFloorRms = absoluteFloorRms

    /** Cheap pre-check so silence does not pay for pitch detection. */
    fun isAudible(rms: Double): Boolean = rms >= floor()

    /** True when this frame should be shown as a note. */
    fun accept(rms: Double, clarity: Double?): Boolean {
        val required = if (isLocked) sustainClarity else acquireClarity
        if (clarity != null && clarity >= required && rms >= floor()) {
            isLocked = true
            quietFrames = 0
            return true
        }

        if (!isLocked) learnNoiseFloor(rms)
        if (++quietFrames >= releaseFrames) isLocked = false
        return false
    }

    fun reset() {
        isLocked = false
        quietFrames = 0
        noiseFloorRms = absoluteFloorRms
    }

    /**
     * Drops instantly, rises only by [noiseCreepPerFrame]. Without that asymmetry a note which
     * just missed the acquire bar would teach the gate its own level, raising the bar each frame.
     */
    private fun learnNoiseFloor(rms: Double) {
        noiseFloorRms = if (rms < noiseFloorRms) {
            maxOf(rms, absoluteFloorRms)
        } else {
            minOf(noiseFloorRms * noiseCreepPerFrame, rms)
        }
    }

    private fun floor() = maxOf(absoluteFloorRms, noiseFloorRms * noiseMargin)
}
