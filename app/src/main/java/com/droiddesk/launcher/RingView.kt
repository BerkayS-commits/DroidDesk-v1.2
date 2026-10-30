package com.droiddesk.launcher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.view.View

/** Circular progress ring with a small icon in the middle (used by the System status widget). */
class RingView(context: Context) : View(context) {
    private var progress = 0f
    private var icon: Drawable? = null
    private val track = Paint(Paint.ANTI_ALIAS_FLAG)
    private val arc = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    init {
        track.style = Paint.Style.STROKE
        arc.style = Paint.Style.STROKE
        arc.strokeCap = Paint.Cap.ROUND
    }

    fun configure(color: Int, trackColor: Int, iconRes: Int) {
        arc.color = color
        track.color = trackColor
        val d = context.getDrawable(iconRes)
        icon = d?.mutate()
        icon?.setTint(color)
        invalidate()
    }

    fun setProgress(p: Float) {
        progress = p.coerceIn(0f, 1f)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val stroke = width * 0.09f
        track.strokeWidth = stroke
        arc.strokeWidth = stroke
        val inset = stroke / 2f + 1f
        rect.set(inset, inset, width - inset, height - inset)
        canvas.drawArc(rect, 0f, 360f, false, track)
        if (progress > 0f) canvas.drawArc(rect, -90f, 360f * progress, false, arc)
        val d = icon
        if (d != null) {
            val s = (width * 0.34f).toInt()
            val l = (width - s) / 2
            val t = (height - s) / 2
            d.setBounds(l, t, l + s, t + s)
            d.draw(canvas)
        }
    }
}
