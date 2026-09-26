package net.fosterish.prongs

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YinTest {

    private val sampleRate = 48_000
    private val windowSize = 4096
    private val yin = Yin(sampleRate, windowSize)

    private fun tone(
        frequencyHz: Double,
        harmonics: DoubleArray = doubleArrayOf(1.0),
        noise: Double = 0.0,
        seed: Int = 1,
    ): FloatArray {
        val random = Random(seed)
        val phases = DoubleArray(harmonics.size) { random.nextDouble() * 2 * PI }
        return FloatArray(windowSize) { i ->
            val t = i.toDouble() / sampleRate
            var sample = 0.0
            for (h in harmonics.indices) {
                sample += harmonics[h] * sin(2 * PI * frequencyHz * (h + 1) * t + phases[h])
            }
            if (noise > 0.0) sample += noise * (random.nextDouble() * 2 - 1)
            (sample * 0.3).toFloat()
        }
    }

    private fun assertDetects(expectedHz: Double, buffer: FloatArray, toleranceCents: Double) {
        val estimate = yin.detect(buffer)
        assertNotNull("no pitch detected for $expectedHz Hz", estimate)
        val error = Notes.cents(estimate!!.frequencyHz, expectedHz)
        assertTrue(
            "expected $expectedHz Hz, got ${estimate.frequencyHz} Hz (${"%.2f".format(error)} cents off)",
            abs(error) <= toleranceCents,
        )
    }

    /**
     * Sub-cent accuracy from [Notes.MIN_MIDI], the low B of a five-string bass, up to C6. Above
     * that the lag is a few dozen samples and interpolation coarsens, which nothing is tuned by.
     */
    @Test
    fun tracksPureSinesAcrossTheInstrumentRange() {
        for (midi in Notes.MIN_MIDI..96) {
            val frequency = Notes.frequency(midi)
            val tolerance = if (midi <= 84) 1.0 else 2.0
            assertDetects(frequency, tone(frequency, seed = midi), tolerance)
        }
    }

    @Test
    fun resolvesPitchesBetweenSemitones() {
        for (offsetCents in intArrayOf(-49, -23, -7, 7, 23, 49)) {
            val frequency = Notes.frequency(49 + offsetCents / 100.0)
            assertDetects(frequency, tone(frequency, seed = offsetCents), toleranceCents = 1.0)
        }
    }

    @Test
    fun findsTheFundamentalOfAHarmonicRichTone() {
        val harmonics = DoubleArray(8) { 1.0 / (it + 1) }
        for (midi in intArrayOf(40, 45, 50, 55, 59, 64)) {
            val frequency = Notes.frequency(midi)
            assertDetects(frequency, tone(frequency, harmonics, seed = midi), toleranceCents = 2.0)
        }
    }

    @Test
    fun findsTheFundamentalWhenTheSecondHarmonicDominates() {
        // Typical of a plucked string near the 12th fret: the octave is louder than the root.
        val harmonics = doubleArrayOf(0.3, 1.0, 0.5, 0.25)
        for (midi in intArrayOf(40, 45, 52, 59)) {
            val frequency = Notes.frequency(midi)
            assertDetects(frequency, tone(frequency, harmonics, seed = midi), toleranceCents = 2.0)
        }
    }

    @Test
    fun survivesModerateNoise() {
        val harmonics = DoubleArray(5) { 1.0 / (it + 1) }
        for (midi in intArrayOf(40, 52, 64)) {
            val frequency = Notes.frequency(midi)
            assertDetects(frequency, tone(frequency, harmonics, noise = 0.2, seed = midi), toleranceCents = 5.0)
        }
    }

    @Test
    fun cleanTonesScoreHigherClarityThanNoise() {
        val clean = yin.detect(tone(220.0))
        assertNotNull(clean)
        assertTrue("clean tone clarity was ${clean!!.clarity}", clean.clarity > 0.9)

        val random = Random(7)
        val hiss = FloatArray(windowSize) { (random.nextDouble() * 2 - 1).toFloat() * 0.3f }
        val noisy = yin.detect(hiss)
        assertTrue(
            "noise scored clarity ${noisy?.clarity}",
            noisy == null || noisy.clarity < 0.6,
        )
    }

    @Test
    fun silenceDoesNotProduceAConfidentPitch() {
        val estimate = yin.detect(FloatArray(windowSize))
        assertTrue("silence produced $estimate", estimate == null || estimate.clarity <= 0.0)
    }

    @Test
    fun respectsTheConfiguredFrequencyBounds() {
        val bounded = Yin(sampleRate, windowSize, minFrequencyHz = 100.0, maxFrequencyHz = 500.0)
        val estimate = bounded.detect(tone(300.0))
        assertNotNull(estimate)
        assertEquals(300.0, estimate!!.frequencyHz, 1.0)
    }
}
