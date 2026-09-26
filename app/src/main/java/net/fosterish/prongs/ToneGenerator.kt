package net.fosterish.prongs

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/**
 * Plays a steady sine reference tone.
 *
 * Phase accumulates across buffers and amplitude ramps over [RAMP_SAMPLES], so it never clicks.
 */
class ToneGenerator(private val sampleRate: Int = 48_000) {

    companion object {
        private const val TAG = "ToneGenerator"
        private const val AMPLITUDE = 0.25f
        private const val BUFFER_SAMPLES = 1024
        private const val RAMP_SAMPLES = 480 // 10ms
    }

    @Volatile
    private var frequency = 0.0

    @Volatile
    private var running = false

    /** As in AudioCapture: a loop outliving its join must not stop a later one. */
    @Volatile
    private var generation = 0

    private var thread: Thread? = null

    /** Starts the tone, or retunes it if one is already sounding. */
    fun play(frequencyHz: Double) {
        frequency = frequencyHz
        if (running) return

        running = true
        val mine = ++generation
        thread = Thread({ renderLoop(mine) }, "tuner-tone").apply {
            isDaemon = true
            start()
        }
    }

    fun stop() {
        running = false
        generation++
        thread?.join(500)
        thread = null
    }

    private fun renderLoop(mine: Int) {
        val minBufferBytes = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_FLOAT,
        )
        if (minBufferBytes <= 0) {
            if (generation == mine) running = false
            return
        }

        val track = try {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(maxOf(minBufferBytes, BUFFER_SAMPLES * Float.SIZE_BYTES * 2))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                .build()
        } catch (e: Exception) {
            Log.e(TAG, "could not open an audio output", e)
            if (generation == mine) running = false
            return
        }

        val buffer = FloatArray(BUFFER_SAMPLES)
        var phase = 0.0
        var rendered = 0L

        try {
            track.play()

            while (running && generation == mine) {
                val step = 2.0 * PI * frequency / sampleRate
                for (i in buffer.indices) {
                    buffer[i] = (sin(phase) * AMPLITUDE * attack(rendered + i)).toFloat()
                    phase += step
                    if (phase > 2 * PI) phase -= 2 * PI
                }
                rendered += buffer.size
                track.write(buffer, 0, buffer.size, AudioTrack.WRITE_BLOCKING)
            }

            fadeOut(track, buffer, phase)
        } catch (e: Exception) {
            Log.e(TAG, "tone playback stopped", e)
        } finally {
            runCatching { track.stop() }
            track.release()
            if (generation == mine) running = false
        }
    }

    private fun attack(sampleIndex: Long): Double =
        min(1.0, sampleIndex.toDouble() / RAMP_SAMPLES)

    private fun fadeOut(track: AudioTrack, buffer: FloatArray, startPhase: Double) {
        var phase = startPhase
        val step = 2.0 * PI * frequency / sampleRate
        for (i in 0 until RAMP_SAMPLES) {
            val gain = 1.0 - i.toDouble() / RAMP_SAMPLES
            buffer[i % buffer.size] = (sin(phase) * AMPLITUDE * gain).toFloat()
            phase += step
            if ((i + 1) % buffer.size == 0) {
                track.write(buffer, 0, buffer.size, AudioTrack.WRITE_BLOCKING)
            }
        }
        track.write(buffer, 0, RAMP_SAMPLES % buffer.size, AudioTrack.WRITE_BLOCKING)
    }
}
