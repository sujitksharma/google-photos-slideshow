package com.skharma.casioclock

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import java.util.Calendar

/**
 * Draws a face styled after the Casio A-158W digital watch: black resin case with
 * gold trim, a greenish-grey LCD panel, seven-segment time, and a dot-matrix
 * day/date row. Everything is laid out in a fixed 1600x1200 design space and
 * scaled to fit the view so the proportions match the real watch regardless
 * of screen size.
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
        private const val DESIGN_W = 1600f
        private const val DESIGN_H = 1200f

        private val DAY_NAMES = arrayOf("SU", "MO", "TU", "WE", "TH", "FR", "SA")

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

    private val caseColor1 = Color.parseColor("#232323")
    private val caseColor2 = Color.parseColor("#0A0A0A")
    private val caseBorder = Color.parseColor("#3A3A3A")
    private val strapColor = Color.parseColor("#151515")
    private val buttonColor = Color.parseColor("#2C2C2C")
    private val gold = Color.parseColor("#C9A93E")
    private val grayText = Color.parseColor("#9A9A9A")
    private val lcdBorder = Color.parseColor("#333C37")
    private val lcdTop = Color.parseColor("#C5D2CC")
    private val lcdBottom = Color.parseColor("#AAB8B0")
    private val lcdOn = Color.parseColor("#212B2E")

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
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
        canvas.drawColor(Color.BLACK)

        val scale = minOf(width / DESIGN_W, height / DESIGN_H)
        val dx = (width - DESIGN_W * scale) / 2f
        val dy = (height - DESIGN_H * scale) / 2f

        canvas.save()
        canvas.translate(dx, dy)
        canvas.scale(scale, scale)
        drawFace(canvas)
        canvas.restore()
    }

    private fun drawFace(canvas: Canvas) {
        val caseW = 1340f
        val caseH = 940f
        val caseX = (DESIGN_W - caseW) / 2f
        val caseY = (DESIGN_H - caseH) / 2f

        // strap lugs (top & bottom)
        fillPaint.shader = null
        fillPaint.color = strapColor
        drawRoundRect(canvas, caseX + caseW * 0.34f, caseY - 60f, caseW * 0.32f, 80f, 18f)
        drawRoundRect(canvas, caseX + caseW * 0.34f, caseY + caseH - 20f, caseW * 0.32f, 80f, 18f)

        // case body with a subtle metallic-plastic gradient
        fillPaint.shader = LinearGradient(
            caseX, caseY, caseX + caseW, caseY + caseH,
            caseColor1, caseColor2, Shader.TileMode.CLAMP
        )
        val caseRect = RectF(caseX, caseY, caseX + caseW, caseY + caseH)
        canvas.drawRoundRect(caseRect, 70f, 70f, fillPaint)
        fillPaint.shader = null

        strokePaint.color = caseBorder
        strokePaint.strokeWidth = 4f
        canvas.drawRoundRect(
            RectF(caseX + 8f, caseY + 8f, caseX + caseW - 8f, caseY + caseH - 8f),
            62f, 62f, strokePaint
        )

        // side buttons
        fillPaint.color = buttonColor
        val bw = 46f
        val bh = 30f
        drawRoundRect(canvas, caseX - bw + 10f, caseY + caseH * 0.18f, bw, bh, 8f)
        drawRoundRect(canvas, caseX - bw + 10f, caseY + caseH * 0.70f, bw, bh, 8f)
        drawRoundRect(canvas, caseX + caseW - 10f, caseY + caseH * 0.18f, bw, bh, 8f)
        drawRoundRect(canvas, caseX + caseW - 10f, caseY + caseH * 0.70f, bw, bh, 8f)

        // ---- brand text ----
        textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        textPaint.color = gold
        textPaint.textSize = 46f
        canvas.drawText("CASIO", caseX + 60f, caseY + 78f, textPaint)

        textPaint.textSize = 30f
        val alarmChrono = "ALARM CHRONO"
        canvas.drawText(alarmChrono, caseX + caseW - 60f - textPaint.measureText(alarmChrono), caseY + 60f, textPaint)

        textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        textPaint.textSize = 20f
        textPaint.color = Color.WHITE
        val lithium = "Lithium"
        canvas.drawText(lithium, caseX + caseW - 60f - textPaint.measureText(lithium), caseY + 92f, textPaint)

        textPaint.color = grayText
        textPaint.textSize = 16f
        canvas.drawText("LIGHT/LAP-RESET", caseX + 40f, caseY + 112f, textPaint)
        canvas.drawText("MODE", caseX + 40f, caseY + 136f, textPaint)
        val ss12 = "START·STOP/12·24H"
        canvas.drawText(ss12, caseX + caseW - 40f - textPaint.measureText(ss12), caseY + 118f, textPaint)

        // ---- LCD panel ----
        val lcdW = caseW * 0.66f
        val lcdH = caseH * 0.66f
        val lcdX = caseX + (caseW - lcdW) / 2f
        val lcdY = caseY + caseH * 0.16f

        fillPaint.shader = LinearGradient(
            lcdX, lcdY, lcdX + lcdW, lcdY + lcdH, lcdTop, lcdBottom, Shader.TileMode.CLAMP
        )
        val lcdRect = RectF(lcdX, lcdY, lcdX + lcdW, lcdY + lcdH)
        canvas.drawRoundRect(lcdRect, 20f, 20f, fillPaint)
        fillPaint.shader = null

        strokePaint.color = lcdBorder
        strokePaint.strokeWidth = 4f
        canvas.drawRoundRect(lcdRect, 20f, 20f, strokePaint)

        // glass highlight streak
        fillPaint.color = Color.WHITE
        fillPaint.alpha = 38
        val streak = Path().apply {
            moveTo(lcdX + 15f, lcdY + 8f)
            lineTo(lcdX + 70f, lcdY + 8f)
            lineTo(lcdX + 25f, lcdY + lcdH - 8f)
            lineTo(lcdX - 20f, lcdY + lcdH - 8f)
            close()
        }
        canvas.drawPath(streak, fillPaint)
        fillPaint.alpha = 255

        val pad = lcdW * 0.055f
        val dotSize = lcdW * 0.026f

        // ---- top row: chime icon, PM, day, date ----
        val topRowY = lcdY + pad * 0.7f
        val iconH = dotSize * 4.6f
        drawChimeIcon(canvas, lcdX + pad, topRowY + (dotSize * 5f - iconH), iconH)

        val pmX = lcdX + pad + iconH * 2.3f
        val hour24 = calendar.get(Calendar.HOUR_OF_DAY)
        if (!use24Hour && hour24 >= 12) {
            drawDotText(canvas, "PM", pmX, topRowY, dotSize)
        }

        val dateStr = calendar.get(Calendar.DAY_OF_MONTH).toString()
        val dateW = textWidth(dateStr, dotSize)
        val dateX = lcdX + lcdW - pad - dateW
        drawDotText(canvas, dateStr, dateX, topRowY, dotSize)

        val dayStr = DAY_NAMES[calendar.get(Calendar.DAY_OF_WEEK) - 1]
        val dayW = textWidth(dayStr, dotSize)
        val dayX = dateX - dotSize * 4.5f - dayW
        drawDotText(canvas, dayStr, dayX, topRowY, dotSize)

        // ---- main row: HH:MM big + SS smaller ----
        val topRowH = dotSize * 6.2f
        val mainAreaY = lcdY + topRowH + pad * 0.3f
        val mainAreaH = lcdH - topRowH - pad * 1.4f
        val availW = lcdW - pad * 2f

        val thickness = 0.20f
        val secThickness = 0.22f
        val wRatio = 0.50f
        val slantRatio = 0.12f
        val secHRatio = 0.46f
        val gapD = wRatio * 0.30f
        val gapC = wRatio * 0.26f
        val colonWr = wRatio * 0.20f
        val gapS = wRatio * 0.55f
        val gapSs = wRatio * secHRatio * 0.30f

        val unitWidth = 4 * wRatio + 2 * gapD + 2 * gapC + colonWr + gapS + 2 * (wRatio * secHRatio) + gapSs + slantRatio
        val digitH = minOf(availW / unitWidth, mainAreaH / 1.02f)
        val digitW = digitH * wRatio
        val slant = digitH * slantRatio
        val gap = digitW * (gapD / wRatio)
        val totalW = digitH * unitWidth
        var mx = lcdX + pad + (availW - totalW) / 2f
        val mainY = mainAreaY + (mainAreaH - digitH) / 2f

        val hourVal = if (use24Hour) hour24 else {
            val h12 = hour24 % 12
            if (h12 == 0) 12 else h12
        }
        val hourStr = if (use24Hour) {
            "%02d".format(hourVal)
        } else {
            if (hourVal < 10) " ${hourVal}" else "$hourVal"
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
        val secY = mainY + (digitH - secH) * 0.62f
        drawDigit(canvas, secondStr[0], mx, secY, secW, secH, secSlant, secThickness)
        mx += secW + secGap
        drawDigit(canvas, secondStr[1], mx, secY, secW, secH, secSlant, secThickness)

        // ---- lower case text ----
        textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        textPaint.color = gold
        textPaint.textSize = 26f
        canvas.drawText("WATER RESIST", caseX + 60f, caseY + caseH - 55f, textPaint)

        textPaint.typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD_ITALIC)
        textPaint.textSize = 36f
        val wr = "WR"
        canvas.drawText(wr, caseX + caseW - 60f - textPaint.measureText(wr), caseY + caseH - 45f, textPaint)
    }

    private fun drawRoundRect(canvas: Canvas, x: Float, y: Float, w: Float, h: Float, r: Float) {
        canvas.drawRoundRect(RectF(x, y, x + w, y + h), r, r, fillPaint)
    }

    private fun drawChimeIcon(canvas: Canvas, x: Float, y: Float, h: Float) {
        val bw = h * 0.16f
        val gap = h * 0.10f
        val heights = floatArrayOf(0.35f, 0.55f, 0.75f, 1.0f)
        var cx = x
        fillPaint.color = lcdOn
        for (hr in heights) {
            val bh = h * hr
            drawRoundRect(canvas, cx, y + (h - bh), bw, bh, bw * 0.3f)
            cx += bw + gap
        }
    }

    private fun drawDotText(canvas: Canvas, text: String, x: Float, y: Float, dot: Float) {
        var cx = x
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
