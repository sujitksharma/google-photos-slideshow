package com.skharma.casioclock

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import java.util.Calendar
import kotlin.math.max

/**
 * A full-screen Nixie-tube clock face: six glowing amber glass tubes (HH:MM:SS)
 * with faint "unlit" ghost numerals behind the lit digit, a wire mesh anode,
 * and a metal base/pins under each tube, plus a dim date row underneath.
 */
class NixieClockView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var use24Hour: Boolean = true
        set(value) {
            field = value
            invalidate()
        }

    companion object {
        private val GHOSTS = charArrayOf('8', '0', '6')
        private val GLOW_SCALES = floatArrayOf(1.35f, 1.22f, 1.10f)
        private val GLOW_ALPHAS = floatArrayOf(0.05f, 0.10f, 0.18f)

        private const val TUBE_ASPECT = 2.3f // tube height / width
        private const val COLON_RATIO = 0.35f // colon gap width, x tubeW
    }

    private val glowColor = Color.parseColor("#FF7A1A")
    private val coreColor = Color.parseColor("#FFD9A0")
    private val ambientTop = Color.parseColor("#2A1206")

    private val digitTypeface = Typeface.MONOSPACE

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = digitTypeface
        style = Paint.Style.FILL
    }
    private val ghostStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = digitTypeface
        style = Paint.Style.STROKE
        color = Color.parseColor("#B0B0B0")
    }

    private val calendar = Calendar.getInstance()
    private val scratchPath = Path()
    private val scratchBounds = RectF()
    private val scratchMatrix = Matrix()

    /** Advances the calendar to "now" and repaints. Call once per second. */
    fun tick() {
        calendar.timeInMillis = System.currentTimeMillis()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        // base ambient background (warm center fading to black)
        fillPaint.shader = RadialGradient(
            w / 2f, h / 2f, max(w, h) * 0.7f,
            ambientTop, Color.BLACK, Shader.TileMode.CLAMP
        )
        fillPaint.alpha = 255
        canvas.drawRect(0f, 0f, w, h, fillPaint)
        fillPaint.shader = null

        val sideMargin = w * 0.03f
        val availW = w - sideMargin * 2f
        val unitWidth = 6 + 2 * COLON_RATIO

        var tubeW = availW / unitWidth
        var tubeH = tubeW * TUBE_ASPECT
        val maxTubeH = h * 0.62f
        if (tubeH > maxTubeH) {
            tubeH = maxTubeH
            tubeW = tubeH / TUBE_ASPECT
        }
        val colonW = tubeW * COLON_RATIO
        val totalW = tubeW * 6 + colonW * 2
        val startX = (w - totalW) / 2f
        val tubeY = (h - tubeH) / 2f - h * 0.03f

        // soft amber glow behind the whole tube row
        fillPaint.shader = RadialGradient(
            w / 2f, tubeY + tubeH * 0.45f, totalW * 0.62f,
            intArrayOf(Color.argb(26, 0xFF, 0x9A, 0x40), Color.argb(0, 0xFF, 0x9A, 0x40)),
            floatArrayOf(0f, 1f), Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w, h, fillPaint)
        fillPaint.shader = null

        val hour24 = calendar.get(Calendar.HOUR_OF_DAY)
        val hourVal = if (use24Hour) hour24 else {
            val h12 = hour24 % 12
            if (h12 == 0) 12 else h12
        }
        val hourStr = if (use24Hour) "%02d".format(hourVal) else "%2d".format(hourVal)
        val minuteStr = "%02d".format(calendar.get(Calendar.MINUTE))
        val secondStr = "%02d".format(calendar.get(Calendar.SECOND))
        val timeStr = hourStr + minuteStr + secondStr

        var x = startX
        for (i in 0 until 6) {
            drawTube(canvas, timeStr[i], x, tubeY, tubeW, tubeH)
            x += tubeW
            if (i == 1 || i == 3) {
                drawColonDots(canvas, x + colonW / 2f, tubeY, tubeH)
                x += colonW
            }
        }

        // dim date row below the tubes
        val dayStr = arrayOf("SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT")[calendar.get(Calendar.DAY_OF_WEEK) - 1]
        val monthStr = arrayOf(
            "JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC"
        )[calendar.get(Calendar.MONTH)]
        val dateStr = "$dayStr   $monthStr ${calendar.get(Calendar.DAY_OF_MONTH)}"
        textPaint.typeface = Typeface.SANS_SERIF
        textPaint.textSize = tubeH * 0.055f
        textPaint.shader = null
        textPaint.color = Color.parseColor("#FF9A40")
        textPaint.alpha = 140
        canvas.drawText(
            dateStr,
            (w - textPaint.measureText(dateStr)) / 2f,
            tubeY + tubeH + tubeH * 0.27f,
            textPaint
        )
        textPaint.alpha = 255
        textPaint.typeface = digitTypeface
    }

    private fun drawTube(canvas: Canvas, digit: Char, x: Float, y: Float, w: Float, h: Float) {
        // glass tube outline (capsule)
        val r = w * 0.46f
        fillPaint.shader = null
        fillPaint.color = Color.argb(18, 255, 255, 255)
        canvas.drawRoundRect(x, y, x + w, y + h, r, r, fillPaint)
        strokePaint.color = Color.argb(55, 255, 255, 255)
        strokePaint.strokeWidth = w * 0.009f
        canvas.drawRoundRect(x, y, x + w, y + h, r, r, strokePaint)

        // vertical glass highlight streak
        strokePaint.color = Color.WHITE
        strokePaint.alpha = 46 // ~0.18 * 255
        strokePaint.strokeWidth = w * 0.06f
        strokePaint.strokeCap = Paint.Cap.ROUND
        canvas.drawLine(x + w * 0.22f, y + h * 0.08f, x + w * 0.22f, y + h * 0.92f, strokePaint)
        strokePaint.alpha = 255

        // faint mesh anode grid lines behind the digit
        strokePaint.color = Color.LTGRAY
        strokePaint.alpha = 15 // ~0.06 * 255
        strokePaint.strokeWidth = w * 0.0045f
        strokePaint.strokeCap = Paint.Cap.BUTT
        val lines = 10
        for (i in 1 until lines) {
            val ly = y + h * i / lines.toFloat()
            canvas.drawLine(x + w * 0.12f, ly, x + w * 0.88f, ly, strokePaint)
        }
        strokePaint.alpha = 255

        drawNixieDigit(canvas, digit, x + w / 2f, y + h * 0.46f, h * 0.62f, w)

        // metal base / socket below the tube
        val baseH = h * 0.10f
        val baseW = w * 0.7f
        val baseX = x + (w - baseW) / 2f
        val baseY = y + h - baseH * 0.3f
        fillPaint.shader = LinearGradient(
            baseX, baseY, baseX, baseY + baseH,
            Color.parseColor("#555555"), Color.parseColor("#1A1A1A"), Shader.TileMode.CLAMP
        )
        val baseCorner = minOf(baseW, baseH) * 0.15f
        canvas.drawRoundRect(baseX, baseY, baseX + baseW, baseY + baseH, baseCorner, baseCorner, fillPaint)
        fillPaint.shader = null

        // pins
        fillPaint.color = Color.parseColor("#888888")
        val pinW = w * 0.013f
        for (i in 0 until 4) {
            val pinX = baseX + baseW * (0.15f + i * 0.23f)
            canvas.drawRect(pinX, baseY + baseH, pinX + pinW, baseY + baseH * 1.8f, fillPaint)
        }
    }

    private fun charPath(paint: Paint, c: Char, cx: Float, baseline: Float, out: Path) {
        val s = c.toString()
        val cw = paint.measureText(s)
        out.reset()
        paint.getTextPath(s, 0, 1, cx - cw / 2f, baseline, out)
    }

    private fun drawNixieDigit(canvas: Canvas, digit: Char, cx: Float, cyCenter: Float, boxH: Float, tubeW: Float) {
        val size = boxH * 0.82f
        textPaint.textSize = size
        ghostStrokePaint.textSize = size

        // vertical centering: measure a reference glyph ("8") at this size
        val fm = Path()
        charPath(textPaint, '8', cx, 0f, fm)
        fm.computeBounds(scratchBounds, true)
        val baseline = cyCenter - scratchBounds.centerY()

        // ghost digits (unlit wire numerals behind, very faint)
        ghostStrokePaint.strokeWidth = tubeW * 0.007f
        for (gch in GHOSTS) {
            if (gch == digit) continue
            charPath(ghostStrokePaint, gch, cx, baseline, scratchPath)
            ghostStrokePaint.alpha = 13 // ~0.05 * 255
            canvas.drawPath(scratchPath, ghostStrokePaint)
        }
        ghostStrokePaint.alpha = 255

        // glow halo (enlarged translucent copies)
        charPath(textPaint, digit, cx, baseline, scratchPath)
        scratchPath.computeBounds(scratchBounds, true)
        val mcx = scratchBounds.centerX()
        val mcy = scratchBounds.centerY()
        textPaint.style = Paint.Style.FILL
        textPaint.color = glowColor
        for (i in GLOW_SCALES.indices) {
            scratchMatrix.reset()
            scratchMatrix.postScale(GLOW_SCALES[i], GLOW_SCALES[i], mcx, mcy)
            val glowPath = Path()
            scratchPath.transform(scratchMatrix, glowPath)
            textPaint.alpha = (GLOW_ALPHAS[i] * 255).toInt()
            canvas.drawPath(glowPath, textPaint)
        }

        // crisp core glyph
        textPaint.color = coreColor
        textPaint.alpha = 242 // ~0.95 * 255
        canvas.drawPath(scratchPath, textPaint)
        textPaint.color = Color.WHITE
        textPaint.alpha = 140 // ~0.55 * 255
        canvas.drawPath(scratchPath, textPaint)
        textPaint.alpha = 255
    }

    private fun drawColonDots(canvas: Canvas, cx: Float, y: Float, h: Float) {
        val r = h * 0.035f
        val y1 = y + h * 0.35f
        val y2 = y + h * 0.60f
        for (dy in floatArrayOf(y1, y2)) {
            fillPaint.shader = null
            fillPaint.color = Color.parseColor("#FF9A40")
            fillPaint.alpha = 46 // ~0.18 * 255
            canvas.drawCircle(cx, dy, r * 2.2f, fillPaint)
            fillPaint.color = Color.parseColor("#FFD9A0")
            fillPaint.alpha = 242 // ~0.95 * 255
            canvas.drawCircle(cx, dy, r, fillPaint)
        }
        fillPaint.alpha = 255
    }
}
