package com.skharma.casioclock

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import java.util.Calendar
import kotlin.math.max

/**
 * A full-screen digital "LCD" clock face: big seven-segment HH:MM with
 * smaller seconds, and a dot-matrix day/date row, styled after a digital
 * watch display but scaled up and stripped of the watch case so it reads
 * clearly from across a room.
 */
class CasioWatchFaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var use24Hour: Boolean = true
        set(value) {
            field = value
            invalidate()
        }

    companion object {
        private val DAY_NAMES = arrayOf("SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT")

        // 3x5 dot-matrix font, one row of 3 chars each, '#' = lit.
        private val FONT: Map<Char, Array<String>> = mapOf(
            '0' to arrayOf("###", "#.#", "#.#", "#.#", "###"),
            '1' to arrayOf(".#.", "##.", ".#.", ".#.", "###"),
            '2' to arrayOf("###", "..#", "###", "#..", "###"),
            '3' to arrayOf("###", "..#", "###", "..#", "###"),
            '4' to arrayOf("#.#", "#.#", "###", "..#", "..#"),
            '5' to arrayOf("###", "#..", "###", "..#", "###"),
            '6' to arrayOf("###", "#..", "###", "#.#", "###"),
            '7' to arrayOf("###", "..#", "..#", "..#", "..#"),
            '8' to arrayOf("###", "#.#", "###", "#.#", "###"),
            '9' to arrayOf("###", "#.#", "###", "..#", "###"),
            '-' to arrayOf("...", "...", "###", "...", "..."),
            'A' to arrayOf(".#.", "#.#", "###", "#.#", "#.#"),
            'D' to arrayOf("##.", "#.#", "#.#", "#.#", "##."),
            'E' to arrayOf("###", "#..", "##.", "#..", "###"),
            'F' to arrayOf("###", "#..", "##.", "#..", "#.."),
            'H' to arrayOf("#.#", "#.#", "###", "#.#", "#.#"),
            'I' to arrayOf("###", ".#.", ".#.", ".#.", "###"),
            'M' to arrayOf("#.#", "###", "###", "#.#", "#.#"),
            'N' to arrayOf("#.#", "##.", "#.#", ".##", "#.#"),
            'O' to arrayOf("###", "#.#", "#.#", "#.#", "###"),
            'P' to arrayOf("###", "#.#", "###", "#..", "#.."),
            'R' to arrayOf("##.", "#.#", "##.", "#.#", "#.#"),
            'S' to arrayOf("###", "#..", "###", "..#", "###"),
            'T' to arrayOf("###", ".#.", ".#.", ".#.", ".#."),
            'U' to arrayOf("#.#", "#.#", "#.#", "#.#", "###"),
            'W' to arrayOf("#.#", "#.#", "#.#", "###", "#.#"),
            ' ' to arrayOf("...", "...", "...", "...", "...")
        )

        // Seven-segment map for digits: a b c d e f g
        private val SEG: Map<Char, BooleanArray> = mapOf(
            '0' to booleanArrayOf(true, true, true, true, true, true, false),
            '1' to booleanArrayOf(false, true, true, false, false, false, false),
            '2' to booleanArrayOf(true, true, false, true, true, false, true),
            '3' to booleanArrayOf(true, true, true, true, false, false, true),
            '4' to booleanArrayOf(false, true, true, false, false, true, true),
            '5' to booleanArrayOf(true, false, true, true, false, true, true),
            '6' to booleanArrayOf(true, false, true, true, true, true, true),
            '7' to booleanArrayOf(true, true, true, false, false, false, false),
            '8' to booleanArrayOf(true, true, true, true, true, true, true),
            '9' to booleanArrayOf(true, true, true, true, false, true, true),
            ' ' to booleanArrayOf(false, false, false, false, false, false, false)
        )

        private val SEG_POINT_INDEX = arrayOf(
            intArrayOf(0, 1), // a: top-left -> top-right
            intArrayOf(1, 3), // b: top-right -> mid-right
            intArrayOf(3, 5), // c: mid-right -> bot-right
            intArrayOf(4, 5), // d: bot-left -> bot-right
            intArrayOf(2, 4), // e: mid-left -> bot-left
            intArrayOf(0, 2), // f: top-left -> mid-left
            intArrayOf(2, 3)  // g: mid-left -> mid-right
        )
        private val UNIT_POINTS = arrayOf(
            floatArrayOf(0f, 0f), floatArrayOf(1f, 0f),
            floatArrayOf(0f, 0.5f), floatArrayOf(1f, 0.5f),
            floatArrayOf(0f, 1f), floatArrayOf(1f, 1f)
        )
    }

    private val lcdTop = Color.parseColor("#C7D4CE")
    private val lcdBottom = Color.parseColor("#A6B5AD")
    private val lcdOn = Color.parseColor("#202B27")

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val segStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val calendar = Calendar.getInstance()

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

        // full-bleed LCD background
        fillPaint.shader = LinearGradient(0f, 0f, w, h, lcdTop, lcdBottom, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, fillPaint)

        // soft vignette for depth
        fillPaint.shader = RadialGradient(
            w / 2f, h / 2f, max(w, h) * 0.75f,
            intArrayOf(Color.argb(0, 0, 0, 0), Color.argb(45, 0, 0, 0)),
            floatArrayOf(0f, 1f), Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w, h, fillPaint)

        // glass highlight streak
        fillPaint.shader = null
        fillPaint.color = Color.WHITE
        fillPaint.alpha = 30
        val streak = Path().apply {
            moveTo(w * 0.02f, 0f)
            lineTo(w * 0.16f, 0f)
            lineTo(w * 0.05f, h)
            lineTo(-w * 0.05f, h)
            close()
        }
        canvas.drawPath(streak, fillPaint)
        fillPaint.alpha = 255

        val pad = minOf(w, h) * 0.06f
        val dotSize = w * 0.017f

        // ---- top row: day, optional PM, date ----
        val topRowY = pad
        val hour24 = calendar.get(Calendar.HOUR_OF_DAY)
        val dayStr = DAY_NAMES[calendar.get(Calendar.DAY_OF_WEEK) - 1]

        var dayX = pad
        if (!use24Hour && hour24 >= 12) {
            drawDotText(canvas, "PM", dayX, topRowY, dotSize)
            dayX += textWidth("PM", dotSize) + dotSize * 3f
        }
        drawDotText(canvas, dayStr, dayX, topRowY, dotSize)

        val dateStr = "${calendar.get(Calendar.MONTH) + 1}-${calendar.get(Calendar.DAY_OF_MONTH)}"
        val dateW = textWidth(dateStr, dotSize)
        drawDotText(canvas, dateStr, w - pad - dateW, topRowY, dotSize)

        // ---- main row: HH:MM big + SS smaller ----
        val topRowH = dotSize * 6.5f
        val mainAreaY = topRowH + pad * 0.6f
        val mainAreaH = h - mainAreaY - pad
        val availW = w - pad * 2f

        val thickness = 0.20f
        val secThickness = 0.22f
        val wRatio = 0.50f
        val slantRatio = 0.12f
        val secHRatio = 0.44f
        val gapD = wRatio * 0.28f
        val gapC = wRatio * 0.24f
        val colonWr = wRatio * 0.18f
        val gapS = wRatio * 0.5f
        val gapSs = wRatio * secHRatio * 0.28f

        val unitWidth = 4 * wRatio + 2 * gapD + 2 * gapC + colonWr + gapS + 2 * (wRatio * secHRatio) + gapSs + slantRatio
        val digitH = minOf(availW / unitWidth, mainAreaH * 0.98f)
        val digitW = digitH * wRatio
        val slant = digitH * slantRatio
        val gap = digitW * (gapD / wRatio)
        val totalW = digitH * unitWidth
        var mx = pad + (availW - totalW) / 2f
        val mainY = mainAreaY + (mainAreaH - digitH) / 2f

        val hourVal = if (use24Hour) hour24 else {
            val h12 = hour24 % 12
            if (h12 == 0) 12 else h12
        }
        val hourStr = if (use24Hour) {
            "%02d".format(hourVal)
        } else {
            if (hourVal < 10) " $hourVal" else "$hourVal"
        }
        val minuteStr = "%02d".format(calendar.get(Calendar.MINUTE))
        val secondStr = "%02d".format(calendar.get(Calendar.SECOND))

        drawDigit(canvas, hourStr[0], mx, mainY, digitW, digitH, slant, thickness)
        mx += digitW + gap
        drawDigit(canvas, hourStr[1], mx, mainY, digitW, digitH, slant, thickness)
        mx += digitW + digitH * gapC
        val colonR = digitH * colonWr * 0.45f
        val colonCx = mx + colonR
        fillPaint.color = lcdOn
        canvas.drawCircle(colonCx, mainY + digitH * 0.28f, colonR, fillPaint)
        canvas.drawCircle(colonCx, mainY + digitH * 0.68f, colonR, fillPaint)
        mx += digitH * colonWr + digitH * gapC
        drawDigit(canvas, minuteStr[0], mx, mainY, digitW, digitH, slant, thickness)
        mx += digitW + gap
        drawDigit(canvas, minuteStr[1], mx, mainY, digitW, digitH, slant, thickness)
        mx += digitW + digitH * gapS

        val secH = digitH * secHRatio
        val secW = secH * wRatio
        val secSlant = secH * slantRatio
        val secGap = digitH * gapSs
        val secY = mainY + (digitH - secH) * 0.60f
        drawDigit(canvas, secondStr[0], mx, secY, secW, secH, secSlant, secThickness)
        mx += secW + secGap
        drawDigit(canvas, secondStr[1], mx, secY, secW, secH, secSlant, secThickness)
    }

    private fun drawRoundRect(canvas: Canvas, x: Float, y: Float, w: Float, h: Float, r: Float) {
        canvas.drawRoundRect(x, y, x + w, y + h, r, r, fillPaint)
    }

    private fun drawDotText(canvas: Canvas, text: String, x: Float, y: Float, dot: Float) {
        var cx = x
        fillPaint.shader = null
        fillPaint.color = lcdOn
        for (c in text) {
            val glyph = FONT[c.uppercaseChar()] ?: FONT[' ']!!
            for (r in 0 until 5) {
                for (col in 0 until 3) {
                    if (glyph[r][col] == '#') {
                        drawRoundRect(
                            canvas, cx + col * dot, y + r * dot,
                            dot * 0.82f, dot * 0.82f, dot * 0.25f
                        )
                    }
                }
            }
            cx += dot * 4.1f
        }
    }

    private fun textWidth(text: String, dot: Float): Float = text.length * dot * 4.1f - dot * 1.1f

    private fun drawDigit(
        canvas: Canvas, c: Char, dx: Float, dy: Float, w: Float, h: Float, slant: Float, thickness: Float
    ) {
        val seg = SEG[c] ?: SEG[' ']!!
        segStroke.color = lcdOn
        segStroke.strokeWidth = w * thickness
        for (i in 0 until 7) {
            if (!seg[i]) continue
            val p1 = SEG_POINT_INDEX[i][0]
            val p2 = SEG_POINT_INDEX[i][1]
            val x1 = UNIT_POINTS[p1][0] * w
            val y1 = UNIT_POINTS[p1][1] * h
            val x2 = UNIT_POINTS[p2][0] * w
            val y2 = UNIT_POINTS[p2][1] * h
            val sx1 = dx + x1 + (1 - y1 / h) * slant
            val sx2 = dx + x2 + (1 - y2 / h) * slant
            canvas.drawLine(sx1, dy + y1, sx2, dy + y2, segStroke)
        }
    }
}
