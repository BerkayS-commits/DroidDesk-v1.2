package com.droiddesk.launcher

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import kotlin.math.cos
import kotlin.math.sin

/** Re-draws app icons into a chosen shape (circle, squircle, teardrop, ...). */
object IconStyler {
    const val ORIGINAL = 0
    const val CIRCLE = 1
    const val SQUIRCLE = 2
    const val SQUARE = 3
    const val TEARDROP = 4
    const val LEAF = 5
    const val HEXAGON = 6

    val names = intArrayOf(
        R.string.shape_original, R.string.shape_circle, R.string.shape_squircle, R.string.shape_square,
        R.string.shape_teardrop, R.string.shape_leaf, R.string.shape_hexagon
    )

    fun path(shape: Int, s: Float): Path {
        val p = Path()
        val rect = RectF(0f, 0f, s, s)
        val big = s / 2f
        val small = s * 0.08f
        when (shape) {
            CIRCLE -> p.addCircle(s / 2f, s / 2f, s / 2f, Path.Direction.CW)
            SQUIRCLE -> p.addRoundRect(rect, s * 0.34f, s * 0.34f, Path.Direction.CW)
            SQUARE -> p.addRoundRect(rect, s * 0.14f, s * 0.14f, Path.Direction.CW)
            // radii order: top-left, top-right, bottom-right, bottom-left (x, y each)
            TEARDROP -> p.addRoundRect(rect, floatArrayOf(big, big, small, small, big, big, big, big), Path.Direction.CW)
            LEAF -> p.addRoundRect(rect, floatArrayOf(big, big, small, small, big, big, small, small), Path.Direction.CW)
            HEXAGON -> {
                val r = s / 2f
                for (k in 0..5) {
                    val ang = Math.toRadians((-90 + 60 * k).toDouble())
                    val x = s / 2f + r * cos(ang).toFloat()
                    val y = s / 2f + r * sin(ang).toFloat()
                    if (k == 0) p.moveTo(x, y) else p.lineTo(x, y)
                }
                p.close()
            }
            else -> p.addRect(rect, Path.Direction.CW)
        }
        return p
    }

    fun render(d: Drawable, size: Int, shape: Int): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        if (shape == ORIGINAL) {
            d.setBounds(0, 0, size, size)
            d.draw(c)
            return bmp
        }
        if (d is AdaptiveIconDrawable) {
            // Adaptive icons are 108dp with a 72dp visible area; draw both layers full-bleed under the mask.
            val pad = (size * 0.25f).toInt()
            val bg = d.background
            if (bg != null) {
                bg.setBounds(-pad, -pad, size + pad, size + pad)
                bg.draw(c)
            }
            val fg = d.foreground
            if (fg != null) {
                fg.setBounds(-pad, -pad, size + pad, size + pad)
                fg.draw(c)
            }
        } else {
            c.drawColor(0xFF1E293B.toInt())
            val inset = (size * 0.14f).toInt()
            d.setBounds(inset, inset, size - inset, size - inset)
            d.draw(c)
        }
        val mask = Paint(Paint.ANTI_ALIAS_FLAG)
        mask.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        c.drawPath(path(shape, size.toFloat()), mask)
        return bmp
    }

    /** Small preview of a shape for the settings screen. */
    fun preview(shape: Int, size: Int, color: Int): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = color
        c.drawPath(path(if (shape == ORIGINAL) SQUARE else shape, size.toFloat()), p)
        p.color = 0xFFFFFFFF.toInt()
        c.drawCircle(size / 2f, size / 2f, size * 0.14f, p)
        return bmp
    }
}
