package net.fosterish.prongs

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View

/**
 * One octave of keys choosing which pitches the tuner listens for. A long press sounds a tone.
 *
 * Badges list the octaves a key is selected in, so targets outside the active one stay visible.
 */
class PianoKeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    companion object {
        private val WHITE_PITCH_CLASSES = intArrayOf(0, 2, 4, 5, 7, 9, 11)

        /** Each black key and the index of the white key it sits after. */
        private val BLACK_KEYS = arrayOf(
            1 to 0, 3 to 1, 6 to 3, 8 to 4, 10 to 5,
        )

        /** How far a black key reaches across the keyboard, and how wide it is. */
        private const val BLACK_REACH_RATIO = 0.62f
        private const val BLACK_WIDTH_RATIO = 0.60f
    }

    /** MIDI note numbers currently targeted. */
    var selection: Set<Int> = emptySet()
        set(value) {
            field = value
            // Indexed once here rather than re-derived per key on every draw.
            octavesByPitchClass = value.groupBy(Notes::pitchClass)
                .mapValues { (_, midis) -> midis.map(Notes::octave).sorted() }
            invalidate()
        }

    private var octavesByPitchClass: Map<Int, List<Int>> = emptyMap()

    /** Octave a tap applies to. */
    var activeOctave: Int = 4
        set(value) {
            field = value
            invalidate()
        }

    /** True while a reference tone is sounding; the next touch stops it instead of selecting. */
    var tonePlaying: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    /** Quarter turn for the wide layout: white keys ascend from the bottom, black keys reach left. */
    var isVertical: Boolean = false
        set(value) {
            field = value
            requestLayout()
            invalidate()
        }

    var onToggle: ((midi: Int) -> Unit)? = null
    var onSoundTone: ((midi: Int) -> Unit)? = null
    var onStopTone: (() -> Unit)? = null

    private val keyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val keyRect = RectF()
    private val pillRect = RectF()

    private val colorWhite = context.getColor(R.color.key_white)
    private val colorWhitePressed = context.getColor(R.color.key_white_pressed)
    private val colorBlack = context.getColor(R.color.key_black)
    private val colorBlackPressed = context.getColor(R.color.key_black_pressed)
    private val colorWhiteOther = context.getColor(R.color.key_white_other)
    private val colorBlackOther = context.getColor(R.color.key_black_other)
    private val colorBorder = context.getColor(R.color.key_border)
    private val colorAccent = context.getColor(R.color.accent)
    private val colorBackground = context.getColor(R.color.background)
    private var pressedPitchClass: Int? = null
    private var swallowingTouch = false

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true

        override fun onSingleTapUp(e: MotionEvent): Boolean {
            val pitchClass = pitchClassAt(e.x, e.y) ?: return false
            onToggle?.invoke(Notes.midiOf(pitchClass, activeOctave))
            performClick()
            return true
        }

        override fun onLongPress(e: MotionEvent) {
            val pitchClass = pitchClassAt(e.x, e.y) ?: return
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            onSoundTone?.invoke(Notes.midiOf(pitchClass, activeOctave))
        }
    })

    // performClick() is reached through the detector's onSingleTapUp, which lint cannot follow.
    // A screen reader sees one view; per-key nodes would need an AccessibilityNodeProvider.
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (tonePlaying) {
                    onStopTone?.invoke()
                    swallowingTouch = true
                    return true
                }
                pressedPitchClass = pitchClassAt(event.x, event.y)
                invalidate()
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                pressedPitchClass = null
                invalidate()
                if (swallowingTouch) {
                    swallowingTouch = false
                    return true
                }
            }
        }

        if (swallowingTouch) return true
        return gestureDetector.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    /** Extent of one white key along the axis the keys are laid out on. */
    private fun whiteExtent() = (if (isVertical) height else width) / 7f

    /** Fills [keyRect] with the bounds of white key [index], counting up from C. */
    private fun whiteKeyBounds(index: Int) {
        val extent = whiteExtent()
        if (isVertical) {
            val bottom = height - index * extent
            keyRect.set(0f, bottom - extent, width.toFloat(), bottom)
        } else {
            keyRect.set(index * extent, 0f, (index + 1) * extent, height.toFloat())
        }
    }

    /** Fills [keyRect] with the black key straddling the edge after white [afterWhite]. */
    private fun blackKeyBounds(afterWhite: Int) {
        val extent = whiteExtent()
        val thickness = extent * BLACK_WIDTH_RATIO
        if (isVertical) {
            val center = height - (afterWhite + 1) * extent
            keyRect.set(0f, center - thickness / 2, width * BLACK_REACH_RATIO, center + thickness / 2)
        } else {
            val center = (afterWhite + 1) * extent
            keyRect.set(center - thickness / 2, 0f, center + thickness / 2, height * BLACK_REACH_RATIO)
        }
    }

    /** Black keys are tested first because they overlap the front of the white keys. */
    private fun pitchClassAt(x: Float, y: Float): Int? {
        val extent = whiteExtent()
        val thickness = extent * BLACK_WIDTH_RATIO
        val reach = (if (isVertical) width else height) * BLACK_REACH_RATIO
        val across = if (isVertical) x else y
        val along = if (isVertical) height - y else x

        if (across <= reach) {
            for ((pitchClass, afterWhite) in BLACK_KEYS) {
                val center = (afterWhite + 1) * extent
                if (along >= center - thickness / 2 && along <= center + thickness / 2) return pitchClass
            }
        }

        val whiteIndex = (along / extent).toInt()
        if (whiteIndex < 0 || whiteIndex >= WHITE_PITCH_CLASSES.size) return null
        return WHITE_PITCH_CLASSES[whiteIndex]
    }

    /** Octaves in which [pitchClass] is targeted, ascending. */
    private fun selectedOctaves(pitchClass: Int): List<Int> =
        octavesByPitchClass[pitchClass] ?: emptyList()

    override fun onDraw(canvas: Canvas) {
        val radius = dp(5f)

        borderPaint.strokeWidth = dp(1f)
        borderPaint.color = colorBorder

        WHITE_PITCH_CLASSES.forEachIndexed { index, pitchClass ->
            whiteKeyBounds(index)
            val selected = isSelectedInActiveOctave(pitchClass)
            val elsewhere = !selected && selectedOctaves(pitchClass).isNotEmpty()
            keyPaint.color = when {
                selected -> colorAccent
                pressedPitchClass == pitchClass -> colorWhitePressed
                elsewhere -> colorWhiteOther
                else -> colorWhite
            }
            canvas.drawRoundRect(keyRect, radius, radius, keyPaint)
            canvas.drawRoundRect(keyRect, radius, radius, borderPaint)

            // key_black is dark in both palettes, so it reads on faint blue as well as white.
            val ink = when {
                selected -> colorWhite
                elsewhere -> colorBlack
                else -> colorBorder
            }
            drawKeyText(canvas, keyRect, pitchClass, ink, isBlack = false)
        }

        for ((pitchClass, afterWhite) in BLACK_KEYS) {
            blackKeyBounds(afterWhite)
            val selected = isSelectedInActiveOctave(pitchClass)
            val elsewhere = !selected && selectedOctaves(pitchClass).isNotEmpty()
            keyPaint.color = when {
                selected -> colorAccent
                pressedPitchClass == pitchClass -> colorBlackPressed
                elsewhere -> colorBlackOther
                else -> colorBlack
            }
            canvas.drawRoundRect(keyRect, radius, radius, keyPaint)
            canvas.drawRoundRect(keyRect, radius, radius, borderPaint)

            drawKeyText(canvas, keyRect, pitchClass, colorWhite, isBlack = true)
        }
    }

    private fun isSelectedInActiveOctave(pitchClass: Int) =
        Notes.midiOf(pitchClass, activeOctave) in selection

    private fun drawKeyText(
        canvas: Canvas,
        rect: RectF,
        pitchClass: Int,
        ink: Int,
        isBlack: Boolean,
    ) {
        namePaint.color = ink
        namePaint.textSize = if (isBlack) sp(10f) else sp(13f)

        val name = Notes.nameOf(pitchClass)
        val nameX: Float
        val nameY: Float
        if (isVertical) {
            namePaint.textAlign = Paint.Align.RIGHT
            nameX = rect.right - dp(9f)
            nameY = rect.centerY() + namePaint.textSize / 3f
        } else {
            namePaint.textAlign = Paint.Align.CENTER
            nameX = rect.centerX()
            nameY = rect.bottom - dp(10f)
        }
        canvas.drawText(name, nameX, nameY, namePaint)

        val octaves = selectedOctaves(pitchClass)
        if (octaves.isEmpty()) return

        // Upright the badge sits above the name; rotated it runs inward from it on the same line.
        badgePaint.textSize = sp(10f)
        val digitWidth = if (isVertical) sp(11f) else rect.width() / (octaves.size + 0.6f)
        val badgeY = if (isVertical) nameY else rect.bottom - dp(if (isBlack) 26f else 32f)
        val startX = if (isVertical) {
            nameX - namePaint.measureText(name) - dp(5f) - digitWidth * (octaves.size - 0.5f)
        } else {
            rect.centerX() - digitWidth * (octaves.size - 1) / 2f
        }

        octaves.forEachIndexed { index, octave ->
            val x = startX + index * digitWidth
            if (octave == activeOctave) {
                pillRect.set(
                    x - digitWidth / 2 + dp(1f),
                    badgeY - sp(10f),
                    x + digitWidth / 2 - dp(1f),
                    badgeY + dp(4f),
                )
                pillPaint.color = if (isSelectedInActiveOctave(pitchClass)) colorBackground else colorAccent
                canvas.drawRoundRect(pillRect, dp(4f), dp(4f), pillPaint)
                badgePaint.color = if (isSelectedInActiveOctave(pitchClass)) colorAccent else colorWhite
            } else {
                badgePaint.color = if (isSelectedInActiveOctave(pitchClass)) colorWhite else ink
            }
            canvas.drawText(octave.toString(), x, badgeY, badgePaint)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val alongEdge = dp(320f).toInt()
        val acrossEdge = dp(180f).toInt()
        val width = resolveSize(if (isVertical) acrossEdge else alongEdge, widthMeasureSpec)
        val height = resolveSize(if (isVertical) alongEdge else acrossEdge, heightMeasureSpec)
        setMeasuredDimension(width, height)
    }
}
