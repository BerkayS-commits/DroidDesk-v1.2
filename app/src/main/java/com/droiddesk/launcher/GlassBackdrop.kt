package com.droiddesk.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.view.View

/**
 * Draws a blurred slice of the desktop background (gradient / wallpaper) behind a panel, aligned to
 * where the panel sits on screen. Real backdrop blur needs Android 12+ (RenderEffect); older versions
 * fall back to a translucent panel.
 */
class GlassBackdrop(
    context: Context,
    private val rootView: () -> View,
    private val source: () -> Bitmap?
) : View(context) {

    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)

    init {
        if (Build.VERSION.SDK_INT >= 31) {
            setRenderEffect(RenderEffect.createBlurEffect(22f, 22f, Shader.TileMode.CLAMP))
        }
    }

    override fun onDraw(canvas: Canvas) {
        val bmp = source() ?: return
        val r = rootView()
        if (r.width == 0) return
        val mine = IntArray(2)
        val theirs = IntArray(2)
        getLocationInWindow(mine)
        r.getLocationInWindow(theirs)
        val scale = r.width.toFloat() / bmp.width
        canvas.save()
        canvas.translate((theirs[0] - mine[0]).toFloat(), (theirs[1] - mine[1]).toFloat())
        canvas.scale(scale, scale)
        canvas.drawBitmap(bmp, 0f, 0f, paint)
        canvas.restore()
    }
}
