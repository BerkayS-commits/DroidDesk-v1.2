package com.droiddesk.launcher

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import java.util.Random
import kotlin.math.sin

/** Wallpapers drawn in code, so the app needs no image assets. */
object Wallpapers {
    val names = intArrayOf(
        R.string.wp_mountain, R.string.wp_aurora, R.string.wp_ocean, R.string.wp_dunes,
        R.string.wp_night, R.string.wp_forest, R.string.wp_neon, R.string.wp_pastel
    )

    fun render(index: Int, w: Int, h: Int): Bitmap {
        val bw = maxOf(w, 1)
        val bh = maxOf(h, 1)
        val bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        when (index) {
            0 -> mountains(c, bw, bh)
            1 -> aurora(c, bw, bh)
            2 -> ocean(c, bw, bh)
            3 -> dunes(c, bw, bh)
            4 -> night(c, bw, bh)
            5 -> forest(c, bw, bh)
            6 -> neon(c, bw, bh)
            else -> pastel(c, bw, bh)
        }
        return bmp
    }

    private fun col(hex: Long): Int = hex.toInt()

    private fun fillGradient(c: Canvas, w: Int, h: Int, colors: IntArray, extent: Float = 1f) {
        val p = Paint()
        p.shader = LinearGradient(0f, 0f, 0f, h * extent, colors, null, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
    }

    private fun glow(c: Canvas, cx: Float, cy: Float, r: Float, color: Int, alpha: Int) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = RadialGradient(cx, cy, r, color.withAlpha(alpha), color.withAlpha(0), Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, r, p)
    }

    private fun ridge(c: Canvas, w: Int, h: Int, baseY: Float, amp: Float, color: Int, seed: Long, steps: Int = 14) {
        val r = Random(seed)
        val path = Path()
        path.moveTo(0f, h.toFloat())
        path.lineTo(0f, baseY)
        for (i in 1..steps) {
            path.lineTo(w * i / steps.toFloat(), baseY - amp * (0.25f + 0.75f * r.nextFloat()))
        }
        path.lineTo(w.toFloat(), h.toFloat())
        path.close()
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = color
        c.drawPath(path, p)
    }

    private fun wave(c: Canvas, w: Int, h: Int, baseY: Float, amp: Float, freq: Float, phase: Float, color: Int) {
        val path = Path()
        path.moveTo(0f, h.toFloat())
        var x = 0f
        while (x <= w) {
            path.lineTo(x, baseY + amp * sin((x * freq + phase).toDouble()).toFloat())
            x += 6f
        }
        path.lineTo(w.toFloat(), h.toFloat())
        path.close()
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = color
        c.drawPath(path, p)
    }

    private fun mountains(c: Canvas, w: Int, h: Int) {
        val horizon = h * 0.62f
        fillGradient(c, w, h, intArrayOf(col(0xFF1E1B4B), col(0xFF6D28D9), col(0xFFEC4899), col(0xFFFB923C)), 0.65f)
        glow(c, w * 0.5f, horizon, w * 0.55f, col(0xFFFDBA74), 130)
        ridge(c, w, h, horizon, h * 0.30f, col(0xFF7C3AED).withAlpha(170), 11)
        ridge(c, w, h, horizon, h * 0.22f, col(0xFF5B21B6).withAlpha(215), 23)
        ridge(c, w, h, horizon, h * 0.13f, col(0xFF312E81), 37)
        val lake = Paint()
        lake.shader = LinearGradient(0f, horizon, 0f, h.toFloat(), col(0xFFB45BD6).withAlpha(235), col(0xFF1E1B4B), Shader.TileMode.CLAMP)
        c.drawRect(0f, horizon, w.toFloat(), h.toFloat(), lake)
    }

    private fun aurora(c: Canvas, w: Int, h: Int) {
        fillGradient(c, w, h, intArrayOf(col(0xFF020617), col(0xFF0F172A), col(0xFF1E1B4B)))
        glow(c, w * 0.25f, h * 0.30f, w * 0.45f, col(0xFF34D399), 150)
        glow(c, w * 0.62f, h * 0.22f, w * 0.40f, col(0xFF8B5CF6), 150)
        glow(c, w * 0.85f, h * 0.45f, w * 0.35f, col(0xFF22D3EE), 120)
        glow(c, w * 0.45f, h * 0.70f, w * 0.40f, col(0xFF6366F1), 90)
    }

    private fun ocean(c: Canvas, w: Int, h: Int) {
        fillGradient(c, w, h, intArrayOf(col(0xFF38BDF8), col(0xFF0369A1), col(0xFF082F49)))
        for (k in 0..4) {
            wave(c, w, h, h * (0.45f + k * 0.11f), h * 0.03f, 0.006f + k * 0.0015f, k * 1.3f, col(0xFFFFFFFF).withAlpha(22 + k * 10))
        }
    }

    private fun dunes(c: Canvas, w: Int, h: Int) {
        fillGradient(c, w, h, intArrayOf(col(0xFFFDE68A), col(0xFFFB923C), col(0xFF9A3412)), 0.8f)
        glow(c, w * 0.7f, h * 0.34f, w * 0.30f, col(0xFFFFFBEB), 200)
        val sun = Paint(Paint.ANTI_ALIAS_FLAG)
        sun.color = col(0xFFFFF7ED)
        c.drawCircle(w * 0.7f, h * 0.34f, h * 0.07f, sun)
        wave(c, w, h, h * 0.62f, h * 0.05f, 0.004f, 0.5f, col(0xFFEA580C).withAlpha(200))
        wave(c, w, h, h * 0.72f, h * 0.06f, 0.003f, 2.0f, col(0xFFC2410C).withAlpha(230))
        wave(c, w, h, h * 0.84f, h * 0.05f, 0.005f, 4.0f, col(0xFF7C2D12))
    }

    private fun night(c: Canvas, w: Int, h: Int) {
        fillGradient(c, w, h, intArrayOf(col(0xFF020617), col(0xFF0F172A), col(0xFF1E293B)))
        val r = Random(7)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        for (i in 0 until 170) {
            p.color = col(0xFFFFFFFF).withAlpha(70 + r.nextInt(185))
            c.drawCircle(r.nextFloat() * w, r.nextFloat() * h * 0.85f, 0.6f + r.nextFloat() * 1.6f, p)
        }
        glow(c, w * 0.8f, h * 0.22f, h * 0.25f, col(0xFFE2E8F0), 70)
        p.color = col(0xFFF1F5F9)
        c.drawCircle(w * 0.8f, h * 0.22f, h * 0.06f, p)
        p.color = col(0xFF0F172A)
        c.drawCircle(w * 0.8f + h * 0.025f, h * 0.22f - h * 0.01f, h * 0.055f, p)
        ridge(c, w, h, h * 0.9f, h * 0.10f, col(0xFF0B1220), 5, 20)
    }

    private fun pines(c: Canvas, w: Int, h: Int, baseY: Float, count: Int, size: Float, color: Int, seed: Long) {
        val r = Random(seed)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = color
        for (i in 0 until count) {
            val x = r.nextFloat() * w
            val s = size * (0.7f + 0.6f * r.nextFloat())
            for (t in 0..2) {
                val top = baseY - s * (1.6f - t * 0.45f)
                val half = s * (0.35f + t * 0.17f)
                val path = Path()
                path.moveTo(x, top)
                path.lineTo(x - half, top + s * 0.75f)
                path.lineTo(x + half, top + s * 0.75f)
                path.close()
                c.drawPath(path, p)
            }
        }
    }

    private fun forest(c: Canvas, w: Int, h: Int) {
        fillGradient(c, w, h, intArrayOf(col(0xFF134E4A), col(0xFF065F46), col(0xFF022C22)))
        glow(c, w * 0.3f, h * 0.25f, w * 0.5f, col(0xFF6EE7B7), 60)
        pines(c, w, h, h * 0.70f, 16, h * 0.10f, col(0xFF065F46).withAlpha(200), 3)
        pines(c, w, h, h * 0.84f, 14, h * 0.15f, col(0xFF064E3B), 9)
        pines(c, w, h, h * 1.0f, 10, h * 0.22f, col(0xFF022C22), 15)
    }

    private fun neon(c: Canvas, w: Int, h: Int) {
        fillGradient(c, w, h, intArrayOf(col(0xFF0B0B2B), col(0xFF2E1065), col(0xFF701A75)), 0.7f)
        val horizon = h * 0.58f
        glow(c, w * 0.5f, horizon, w * 0.45f, col(0xFFF472B6), 170)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = col(0xFF0B0B2B)
        c.drawRect(0f, horizon, w.toFloat(), h.toFloat(), p)
        p.color = col(0xFFF0ABFC).withAlpha(150)
        p.strokeWidth = 2f
        for (i in -12..12) c.drawLine(w * 0.5f, horizon, w * 0.5f + i * w * 0.12f, h.toFloat(), p)
        var y = horizon
        var step = h * 0.012f
        while (y < h) {
            c.drawLine(0f, y, w.toFloat(), y, p)
            y += step
            step *= 1.35f
        }
    }

    private fun pastel(c: Canvas, w: Int, h: Int) {
        val p = Paint()
        p.shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), intArrayOf(col(0xFFFBCFE8), col(0xFFBFDBFE), col(0xFFDDD6FE)), null, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        glow(c, w * 0.2f, h * 0.3f, w * 0.35f, col(0xFFFFFFFF), 160)
        glow(c, w * 0.75f, h * 0.25f, w * 0.30f, col(0xFFFDA4AF), 120)
        glow(c, w * 0.6f, h * 0.8f, w * 0.40f, col(0xFF93C5FD), 130)
    }
}
