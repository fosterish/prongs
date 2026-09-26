package net.fosterish.prongs

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

/**
 * Chooses which octave a keyboard tap applies to.
 *
 * A dot marks an octave holding at least one target, so the strip doubles as an overview.
 */
class OctaveStripView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    var octaves: IntRange = 1..6
        set(value) {
            field = value
            invalidate()
        }

    var activeOctave: Int = 4
        set(value) {
            field = value
            invalidate()
        }

    var selection: Set<Int> = emptySet()
        set(value) {
            field = value
            invalidate()
        }

    var onOctaveSelected: ((Int) -> Unit)? = null

    /** Stacks the cells ascending upward, for the wide layout's right-edge column. */
    var isVertical: Boolean = false
        set(value) {
            field = value
            requestLayout()
            invalidate()
        }

    private val cellPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cellRect = RectF()

    private val colorSurface = context.getColor(R.color.surface)
    private val colorAccent = context.getColor(R.color.accent)
    private val colorPrimary = context.getColor(R.color.text_primary)
    private val colorSecondary = context.getColor(R.color.text_secondary)
    private val colorBackground = context.getColor(R.color.background)

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked != MotionEvent.ACTION_UP) {
            return event.actionMasked == MotionEvent.ACTION_DOWN
        }
        val count = octaves.count()
        val fraction = if (isVertical) 1f - event.y / height else event.x / width
        val index = (fraction * count).toInt().coerceIn(0, count - 1)
        val octave = octaves.first + index
        if (octave != activeOctave) onOctaveSelected?.invoke(octave)
        performClick()
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onDraw(canvas: Canvas) {
        val count = octaves.count()
        val inset = dp(3f)
        val radius = dp(7f)

        textPaint.textSize = sp(15f)

        octaves.forEachIndexed { index, octave ->
            val isActive = octave == activeOctave
            cellBounds(index, count, inset)

            cellPaint.color = if (isActive) colorAccent else colorSurface
            canvas.drawRoundRect(cellRect, radius, radius, cellPaint)

            textPaint.color = if (isActive) colorBackground else colorSecondary
            canvas.drawText(
                octave.toString(),
                cellRect.centerX(),
                cellRect.centerY() + textPaint.textSize / 3f,
                textPaint,
            )

            // The dot takes the trailing edge, clear of the centred digit.
            if (selection.any { Notes.octave(it) == octave }) {
                dotPaint.color = if (isActive) colorBackground else colorPrimary
                val dotX = if (isVertical) cellRect.right - dp(8f) else cellRect.centerX()
                val dotY = if (isVertical) cellRect.centerY() else cellRect.bottom - dp(9f)
                canvas.drawCircle(dotX, dotY, dp(2.5f), dotPaint)
            }
        }
    }

    /** Fills [cellRect] with cell [index]'s bounds. */
    private fun cellBounds(index: Int, count: Int, inset: Float) {
        if (isVertical) {
            val cellHeight = height.toFloat() / count
            val bottom = height - index * cellHeight
            cellRect.set(inset, bottom - cellHeight + inset, width - inset, bottom - inset)
        } else {
            val cellWidth = width.toFloat() / count
            cellRect.set(index * cellWidth + inset, inset, (index + 1) * cellWidth - inset, height - inset)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val longEdge = dp(320f).toInt()
        val shortEdge = dp(48f).toInt()
        val width = resolveSize(if (isVertical) shortEdge else longEdge, widthMeasureSpec)
        val height = resolveSize(if (isVertical) longEdge else shortEdge, heightMeasureSpec)
        setMeasuredDimension(width, height)
    }
}
