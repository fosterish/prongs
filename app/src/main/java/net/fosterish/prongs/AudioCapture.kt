package net.fosterish.prongs

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Process
import android.util.Log
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Streams overlapping windows of microphone audio to [onWindow] from a dedicated thread.
 *
 * [onWindow] runs on the capture thread and must return inside the hop, 43ms at the defaults.
 */
class AudioCapture(
    private val sampleRate: Int = SAMPLE_RATE,
    private val windowSize: Int = WINDOW_SIZE,
    private val hopSize: Int = HOP_SIZE,
    private val audioSource: Int = MediaRecorder.AudioSource.VOICE_RECOGNITION,
    private val onWindow: (window: FloatArray, rms: Double) -> Unit,
) {

    companion object {
        const val SAMPLE_RATE = 48_000
        const val WINDOW_SIZE = 4096
        const val HOP_SIZE = 2048
        private const val TAG = "AudioCapture"
    }

    @Volatile
    private var running = false

    /** [stop] waits only 500ms, so a loop outliving its join must not stop a later one. */
    @Volatile
    private var generation = 0

    private var thread: Thread? = null

    @SuppressLint("MissingPermission") // callers start capture only after RECORD_AUDIO is granted
    fun start(): Boolean {
        if (running) return true

        val minBufferBytes = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_FLOAT,
        )
        if (minBufferBytes <= 0) {
            Log.e(TAG, "device reports no usable buffer size at $sampleRate Hz")
            return false
        }

        // Several hops of slack so a slow callback costs latency rather than dropped samples.
        val bufferBytes = max(minBufferBytes * 2, windowSize * 4 * Float.SIZE_BYTES)

        val record = try {
            AudioRecord.Builder()
                .setAudioSource(audioSource)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufferBytes)
                .build()
        } catch (e: Exception) {
            Log.e(TAG, "could not open the microphone", e)
            return false
        }

        if (record.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord failed to initialise")
            record.release()
            return false
        }

        running = true
        val mine = ++generation
        thread = Thread({ captureLoop(record, mine) }, "tuner-audio").apply {
            isDaemon = true
            start()
        }
        return true
    }

    fun stop() {
        running = false
        generation++
        thread?.join(500)
        thread = null
    }

    private fun captureLoop(record: AudioRecord, mine: Int) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        val window = FloatArray(windowSize)
        val keep = windowSize - hopSize

        try {
            record.startRecording()
            while (running && generation == mine) {
                // Slide the previous window down and fill the tail with fresh samples.
                System.arraycopy(window, hopSize, window, 0, keep)
                if (!readFully(record, window, keep, hopSize)) break
                onWindow(window, rms(window))
            }
        } catch (e: Exception) {
            Log.e(TAG, "capture loop stopped", e)
        } finally {
            runCatching { record.stop() }
            record.release()
            if (generation == mine) running = false
        }
    }

    private fun readFully(record: AudioRecord, into: FloatArray, offset: Int, count: Int): Boolean {
        var filled = 0
        while (filled < count) {
            val read = record.read(into, offset + filled, count - filled, AudioRecord.READ_BLOCKING)
            if (read <= 0) {
                // A stopping loop is expected to read short; only a live one is a problem.
                if (running) Log.e(TAG, "AudioRecord.read returned $read")
                return false
            }
            filled += read
        }
        return true
    }

    private fun rms(samples: FloatArray): Double {
        var sum = 0.0
        for (sample in samples) sum += sample.toDouble() * sample.toDouble()
        return sqrt(sum / samples.size)
    }
}
