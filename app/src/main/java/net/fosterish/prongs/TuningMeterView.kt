package net.fosterish.prongs

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The detail view: which note is targeted, and how far off it is in cents.
 *
 * The letter stays centred whatever is attached to it, so it never shifts while you turn a peg.
 * A lost reading holds then fades, rather than vanishing as a pluck decays.
 */
class TuningMeterView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    companion object {
        /** Full-scale deflection. */
        private const val RANGE_CENTS = 50.0f

        /** Fully green at or inside this, fully amber at or beyond [AMBER_BEYOND_CENTS]. */
        private const val GREEN_WITHIN_CENTS = 10.0f
        private const val AMBER_BEYOND_CENTS = 30.0f

        /** Fraction of the remaining distance covered each frame. */
        private const val NEEDLE_EASING = 0.22f

        private const val SETTLED_EPSILON = 0.05f

        /** How long a lost reading stays fully visible, and how long it then takes to fade. */
        private const val HOLD_MILLIS = 1300L
        private const val FADE_MILLIS = 900L

        /** Boundary between the note block above and the scale below. */
        private const val SCALE_TOP_FRACTION = 0.56f
    }

    private val letterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
    }
    private val modifierPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        textAlign = Paint.Align.LEFT
    }
    private val centsPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
    }
    private val readoutPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        textAlign = Paint.Align.LEFT
    }
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    private val needlePath = Path()

    private val colorPrimary = context.getColor(R.color.text_primary)
    private val colorSecondary = context.getColor(R.color.text_secondary)
    private val colorDisabled = context.getColor(R.color.text_disabled)
    private val colorInTune = context.getColor(R.color.in_tune)
    private val colorOffPitch = context.getColor(R.color.off_pitch)
    private val colorTrack = context.getColor(R.color.surface_raised)

    private var reading: TuningReading? = null
    private var pinned: TuningReading? = null
    private var lostAtMillis = 0L
    private var targetCents = 0f
    private var needleCents = 0f

    /**
     * Holds [reading] in place of live input, for the tone the keyboard sounds; null hands the
     * meter back. Also discards whatever the engine posted just before it was stopped.
     */
    fun pin(reading: TuningReading?) {
        pinned = reading
        this.reading = reading
        lostAtMillis = 0L
        targetCents = 0f
        if (reading == null) needleCents = 0f
        invalidate()
    }

    fun update(incoming: TuningReading?) {
        if (pinned != null) return
        if (incoming != null) {
            reading = incoming
            lostAtMillis = 0L
            targetCents = incoming.cents.toFloat().coerceIn(-RANGE_CENTS, RANGE_CENTS)
        } else if (reading != null && lostAtMillis == 0L) {
            lostAtMillis = SystemClock.uptimeMillis()
        }
        invalidate()
    }

    /** 1 while live, 1 through the hold, ramping to 0 across the fade. */
    private fun readingAlpha(): Float {
        if (reading == null) return 0f
        if (lostAtMillis == 0L) return 1f
        val elapsed = SystemClock.uptimeMillis() - lostAtMillis
        return when {
            elapsed <= HOLD_MILLIS -> 1f
            elapsed >= HOLD_MILLIS + FADE_MILLIS -> 0f
            else -> 1f - (elapsed - HOLD_MILLIS).toFloat() / FADE_MILLIS
        }
    }

    /** Green when close, amber when not, blended across the band between. */
    private fun deviationColor(cents: Double): Int {
        val distance = abs(cents).toFloat()
        val t = ((distance - GREEN_WITHIN_CENTS) / (AMBER_BEYOND_CENTS - GREEN_WITHIN_CENTS))
            .coerceIn(0f, 1f)
        return blend(colorInTune, colorOffPitch, t)
    }

    private fun blend(from: Int, to: Int, t: Float): Int = Color.rgb(
        (Color.red(from) + (Color.red(to) - Color.red(from)) * t).roundToInt(),
        (Color.green(from) + (Color.green(to) - Color.green(from)) * t).roundToInt(),
        (Color.blue(from) + (Color.blue(to) - Color.blue(from)) * t).roundToInt(),
    )

    override fun onDraw(canvas: Canvas) {
        val alpha = readingAlpha()
        if (alpha <= 0f && lostAtMillis != 0L) {
            reading = null
            lostAtMillis = 0L
            needleCents = 0f
            targetCents = 0f
        }

        val current = reading
        val activeColor = current?.let { deviationColor(it.cents) } ?: colorOffPitch

        if (current == null) drawListening(canvas) else drawNote(canvas, current, activeColor, alpha)
        drawScale(canvas, current, activeColor, alpha)

        if (advanceNeedle() || lostAtMillis != 0L) postInvalidateOnAnimation()
    }

    /** True while the needle is still moving. */
    private fun advanceNeedle(): Boolean {
        val remaining = targetCents - needleCents
        if (abs(remaining) < SETTLED_EPSILON) {
            needleCents = targetCents
            return false
        }
        needleCents += remaining * NEEDLE_EASING
        return true
    }

    private fun Paint.setColor(color: Int, alpha: Float) {
        this.color = color
        this.alpha = (255 * alpha).roundToInt().coerceIn(0, 255)
    }

    private fun drawListening(canvas: Canvas) {
        val region = height * SCALE_TOP_FRACTION
        labelPaint.textSize = sp(18f)
        labelPaint.setColor(colorSecondary, 1f)
        // Centred in the space the note block would otherwise occupy.
        canvas.drawText(
            context.getString(R.string.listening),
            width / 2f,
            region / 2f + labelPaint.textSize / 3f,
            labelPaint,
        )
    }

    private fun drawNote(canvas: Canvas, reading: TuningReading, activeColor: Int, alpha: Float) {
        val centerX = width / 2f
        val baselineY = height * 0.36f

        val name = Notes.name(reading.targetMidi)
        val letter = name.substring(0, 1)
        val accidental = name.substring(1)

        val letterSize = minOf(width * 0.26f, height * 0.30f)
        letterPaint.textSize = letterSize
        letterPaint.setColor(colorPrimary, alpha)
        canvas.drawText(letter, centerX, baselineY, letterPaint)

        val modifierSize = letterSize * 0.40f
        modifierPaint.textSize = modifierSize
        var x = centerX + letterPaint.measureText(letter) / 2f + dp(4f)

        if (accidental.isNotEmpty()) {
            modifierPaint.setColor(colorPrimary, alpha)
            canvas.drawText(accidental, x, baselineY - letterSize * 0.34f, modifierPaint)
            x += modifierPaint.measureText(accidental) + dp(3f)
        }

        modifierPaint.setColor(colorSecondary, alpha)
        canvas.drawText(Notes.octave(reading.targetMidi).toString(), x, baselineY, modifierPaint)

        drawFrequencies(canvas, reading, alpha, centerX, height * 0.47f)
    }

    /** The measured pitch is what is watched; the target is reference material. */
    private fun drawFrequencies(
        canvas: Canvas,
        reading: TuningReading,
        alpha: Float,
        centerX: Float,
        baselineY: Float,
    ) {
        val measured = "%.1f Hz".format(reading.detectedHz)
        val target = "target %.1f Hz".format(reading.targetHz)
        val measuredSize = sp(21f)
        val targetSize = sp(13f)
        val gap = dp(10f)

        readoutPaint.textSize = measuredSize
        val measuredWidth = readoutPaint.measureText(measured)
        readoutPaint.textSize = targetSize
        val targetWidth = readoutPaint.measureText(target)

        var x = centerX - (measuredWidth + gap + targetWidth) / 2f
        readoutPaint.textSize = measuredSize
        readoutPaint.setColor(colorPrimary, alpha)
        canvas.drawText(measured, x, baselineY, readoutPaint)

        x += measuredWidth + gap
        readoutPaint.textSize = targetSize
        readoutPaint.setColor(colorSecondary, alpha)
        canvas.drawText(target, x, baselineY, readoutPaint)
    }

    private fun drawScale(canvas: Canvas, reading: TuningReading?, activeColor: Int, alpha: Float) {
        val centerX = width / 2f
        val scaleTop = height * SCALE_TOP_FRACTION
        val scaleHeight = height - scaleTop
        val margin = dp(28f)
        val halfSpan = (width - margin * 2) / 2f

        val majorTick = (scaleHeight * 0.16f).coerceIn(dp(10f), dp(30f))
        val minorTick = majorTick * 0.52f
        val pointerHeight = (scaleHeight * 0.10f).coerceIn(dp(9f), dp(18f))
        val axisY = scaleTop + scaleHeight * 0.38f

        trackPaint.setColor(colorTrack, 1f)
        val trackHeight = dp(4f)
        canvas.drawRoundRect(
            centerX - halfSpan, axisY - trackHeight / 2,
            centerX + halfSpan, axisY + trackHeight / 2,
            trackHeight, trackHeight, trackPaint,
        )

        tickPaint.strokeWidth = dp(2f)
        for (cents in -50..50 step 10) {
            val x = centerX + halfSpan * (cents / RANGE_CENTS)
            val length = if (cents % 50 == 0 || cents == 0) majorTick else minorTick
            tickPaint.setColor(if (cents == 0) colorPrimary else colorDisabled, 1f)
            canvas.drawLine(x, axisY - length, x, axisY + length, tickPaint)
        }

        labelPaint.textSize = sp(12f)
        labelPaint.setColor(colorDisabled, 1f)
        val labelY = axisY + majorTick + (scaleHeight * 0.10f).coerceIn(dp(12f), dp(22f))
        canvas.drawText("-50", centerX - halfSpan, labelY, labelPaint)
        canvas.drawText("0", centerX, labelY, labelPaint)
        canvas.drawText("+50", centerX + halfSpan, labelY, labelPaint)

        if (reading == null) return

        centsPaint.textSize = sp(24f)
        centsPaint.setColor(activeColor, alpha)
        val centsY = labelY + (scaleHeight * 0.20f).coerceIn(dp(24f), dp(44f))
        val rounded = reading.cents.roundToInt()
        canvas.drawText(
            if (rounded >= 0) "+$rounded\u00A2" else "$rounded\u00A2",
            centerX, centsY, centsPaint,
        )

        // A bar growing out of centre reads as flat or sharp at a glance.
        val needleX = centerX + halfSpan * (needleCents / RANGE_CENTS)
        barPaint.setColor(activeColor, alpha)
        barPaint.strokeWidth = dp(9f)
        canvas.drawLine(centerX, axisY, needleX, axisY, barPaint)

        val pointerBase = axisY - majorTick - dp(6f)
        val pointerHalfWidth = pointerHeight * 0.75f
        needlePath.reset()
        needlePath.moveTo(needleX, pointerBase)
        needlePath.lineTo(needleX - pointerHalfWidth, pointerBase - pointerHeight)
        needlePath.lineTo(needleX + pointerHalfWidth, pointerBase - pointerHeight)
        needlePath.close()
        canvas.drawPath(needlePath, barPaint)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = resolveSize(dp(320f).toInt(), widthMeasureSpec)
        val height = resolveSize(dp(260f).toInt(), heightMeasureSpec)
        setMeasuredDimension(width, height)
    }

    init {
        setBackgroundColor(Color.TRANSPARENT)
    }
}
