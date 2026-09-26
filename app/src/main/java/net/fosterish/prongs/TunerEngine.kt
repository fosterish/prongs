package net.fosterish.prongs

import android.content.Context
import android.media.AudioManager
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.os.SystemClock

/**
 * Owns the microphone, detector and target selection, publishing a reading per frame to the main
 * thread. A null reading means nothing is being heard.
 */
class TunerEngine(
    private val context: Context,
    private val onReading: (TuningReading?) -> Unit,
) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val yin = Yin(AudioCapture.SAMPLE_RATE, AudioCapture.WINDOW_SIZE)
    private val gate = SignalGate()
    private val smoother = PitchSmoother()
    private val selector = TargetSelector()
    private val capture = AudioCapture(audioSource = preferredAudioSource(), onWindow = ::onWindow)

    /** Empty means chromatic. Written from the main thread, read on the audio thread. */
    @Volatile
    var targets: Set<Int> = emptySet()

    var a4Hz: Double
        get() = selector.a4Hz
        set(value) {
            selector.a4Hz = value
        }

    fun start() {
        if (capture.start()) return
        publish(null)
    }

    fun stop() {
        capture.stop()
        gate.reset()
        smoother.reset()
        selector.reset()
    }

    /** Runs on the capture thread. */
    private fun onWindow(window: FloatArray, rms: Double) {
        val estimate = if (gate.isAudible(rms)) yin.detect(window) else null

        if (!gate.accept(rms, estimate?.clarity)) {
            // The meter holds and fades, so reporting a loss at once costs nothing visually.
            if (!gate.isLocked) smoother.reset()
            publish(null)
            return
        }

        val smoothed = smoother.accept(estimate!!.frequencyHz, System.nanoTime())
        publish(selector.select(smoothed, targets, SystemClock.uptimeMillis()))
    }

    private fun publish(reading: TuningReading?) {
        mainHandler.post { onReading(reading) }
    }

    /** UNPROCESSED skips the gain control and noise suppression that distort a sustained tone. */
    private fun preferredAudioSource(): Int {
        val manager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val supported = manager
            ?.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED)
            .toBoolean()
        return if (supported) {
            MediaRecorder.AudioSource.UNPROCESSED
        } else {
            MediaRecorder.AudioSource.VOICE_RECOGNITION
        }
    }
}
