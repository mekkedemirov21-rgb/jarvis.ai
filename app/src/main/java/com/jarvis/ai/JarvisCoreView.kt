package com.jarvis.ai

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.view.View
import kotlin.math.min
import kotlin.math.sin

/**
 * Lightweight animated JARVIS HUD core. Drawn locally; no images or network needed.
 */
class JarvisCoreView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val density = resources.displayMetrics.density

    private fun dp(v: Float) = v * density

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        if (cx <= 0f || cy <= 0f) return

        val t = SystemClock.uptimeMillis() / 1000f
        val phase = (t * 36f) % 360f
        val pulse = (sin(t * 2.1f) + 1f) / 2f
        val r = min(width * 0.22f, height * 0.39f)

        // Soft cyan halo around the core.
        paint.style = Paint.Style.FILL
        paint.color = Color.argb((13 + pulse * 12).toInt(), 40, 160, 255)
        canvas.drawCircle(cx, cy, r * 1.65f, paint)
        paint.color = Color.argb((12 + pulse * 12).toInt(), 30, 115, 215)
        canvas.drawCircle(cx, cy, r * 1.28f, paint)

        // Concentric rings.
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(1f)
        paint.color = Color.argb(105, 69, 180, 255)
        canvas.drawCircle(cx, cy, r * 1.42f, paint)
        paint.color = Color.argb(135, 90, 207, 255)
        canvas.drawCircle(cx, cy, r * 1.13f, paint)
        paint.color = Color.argb(85, 130, 225, 255)
        canvas.drawCircle(cx, cy, r * 0.88f, paint)

        // Segmented rotating scan rings.
        val outer = RectF(cx - r * 1.42f, cy - r * 1.42f, cx + r * 1.42f, cy + r * 1.42f)
        paint.strokeWidth = dp(2f)
        paint.color = Color.argb(210, 101, 208, 255)
        canvas.drawArc(outer, phase, 64f, false, paint)
        canvas.drawArc(outer, phase + 180f, 26f, false, paint)

        val inner = RectF(cx - r * 0.99f, cy - r * 0.99f, cx + r * 0.99f, cy + r * 0.99f)
        paint.strokeWidth = dp(1.5f)
        paint.color = Color.argb(190, 135, 231, 255)
        canvas.drawArc(inner, -phase * 1.7f, 94f, false, paint)
        canvas.drawArc(inner, 180f - phase * 1.7f, 38f, false, paint)

        // HUD tick marks rotate slowly around the perimeter.
        canvas.save()
        canvas.rotate(-phase * 0.35f, cx, cy)
        paint.strokeWidth = dp(1f)
        paint.color = Color.argb(145, 98, 196, 245)
        for (i in 0 until 48) {
            canvas.save()
            canvas.rotate(i * 7.5f, cx, cy)
            val major = i % 4 == 0
            val start = r * 1.21f
            val end = start + if (major) dp(7f) else dp(3f)
            canvas.drawLine(cx, cy - start, cx, cy - end, paint)
            canvas.restore()
        }
        canvas.restore()

        // Center glow and glass rings.
        paint.style = Paint.Style.FILL
        paint.color = Color.argb((30 + pulse * 22).toInt(), 42, 161, 255)
        canvas.drawCircle(cx, cy, r * 0.66f, paint)
        paint.color = Color.argb(35, 143, 229, 255)
        canvas.drawCircle(cx, cy, r * 0.49f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(1f)
        paint.color = Color.argb(220, 157, 231, 255)
        canvas.drawCircle(cx, cy, r * 0.40f, paint)
        paint.color = Color.argb(150, 92, 190, 255)
        canvas.drawCircle(cx, cy, r * 0.57f, paint)

        // Central luminous lens.
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(65, 76, 190, 255)
        canvas.drawCircle(cx, cy, r * 0.29f, paint)
        paint.color = Color.rgb(174, 239, 255)
        canvas.drawCircle(cx, cy, r * 0.085f, paint)
        paint.color = Color.WHITE
        canvas.drawCircle(cx - r * 0.025f, cy - r * 0.03f, r * 0.032f, paint)

        // Small orbit points.
        paint.color = Color.rgb(155, 230, 255)
        for (i in 0 until 4) {
            val a = Math.toRadians((phase * if (i % 2 == 0) 1.0 else -0.7) + i * 90.0)
            val rr = r * if (i % 2 == 0) 1.13f else 0.88f
            canvas.drawCircle(cx + (kotlin.math.cos(a) * rr).toFloat(), cy + (kotlin.math.sin(a) * rr).toFloat(), dp(2f), paint)
        }

        // Low-profile system readout.
        paint.style = Paint.Style.FILL
        paint.typeface = android.graphics.Typeface.create("monospace", android.graphics.Typeface.BOLD)
        paint.textSize = dp(8.5f)
        paint.textAlign = Paint.Align.CENTER
        paint.color = Color.rgb(105, 181, 219)
        canvas.drawText("J.A.R.V.I.S.  •  CORE ONLINE", cx, height - dp(7f), paint)

        postInvalidateDelayed(32L)
    }
}
