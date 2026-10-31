package com.droiddesk.launcher

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.PointerIcon
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

fun Int.withAlpha(alpha: Int): Int =
    Color.argb(alpha.coerceIn(0, 255), Color.red(this), Color.green(this), Color.blue(this))

fun Context.rounded(color: Int, radiusDp: Int, strokeColor: Int = 0, strokeDp: Int = 0): GradientDrawable {
    val d = GradientDrawable()
    d.setColor(color)
    d.cornerRadius = dp(radiusDp).toFloat()
    if (strokeDp > 0) d.setStroke(dp(strokeDp), strokeColor)
    return d
}

fun Context.label(value: String, size: Int, color: Int, bold: Boolean = false): TextView {
    val t = TextView(this)
    t.text = value
    t.textSize = size.toFloat()
    t.setTextColor(color)
    if (bold) t.typeface = Typeface.create("sans-serif", Typeface.BOLD)
    t.gravity = Gravity.CENTER_VERTICAL
    return t
}

fun Context.iconView(res: Int, color: Int): ImageView {
    val v = ImageView(this)
    v.scaleType = ImageView.ScaleType.FIT_CENTER
    v.setImageResource(res)
    v.setColorFilter(color)
    return v
}

/** Round icon button. The inner ImageView is kept in the tag so [setCircle] can recolour it. */
fun Context.circleIcon(res: Int, bg: Int, tint: Int, innerDp: Int): FrameLayout {
    val f = FrameLayout(this)
    val d = GradientDrawable()
    d.shape = GradientDrawable.OVAL
    d.setColor(bg)
    f.background = d
    val i = iconView(res, tint)
    f.addView(i, FrameLayout.LayoutParams(dp(innerDp), dp(innerDp), Gravity.CENTER))
    f.tag = i
    f.isClickable = true
    f.isFocusable = true
    return f
}

fun setCircle(f: FrameLayout, bg: Int, tint: Int) {
    (f.background as? GradientDrawable)?.setColor(bg)
    (f.tag as? ImageView)?.setColorFilter(tint)
}

fun llp(w: Int, h: Int, left: Int = 0): LinearLayout.LayoutParams {
    val p = LinearLayout.LayoutParams(w, h)
    p.leftMargin = left
    return p
}

/** Hover for mouse users: highlight, optional grow effect, hand cursor and a focus ring for keyboard users. */
fun Context.hoverable(v: View, radiusDp: Int = 12, scale: Float = 1f) {
    if (Build.VERSION.SDK_INT >= 24) v.pointerIcon = PointerIcon.getSystemIcon(this, PointerIcon.TYPE_HAND)
    v.setOnHoverListener { view, e ->
        when (e.action) {
            MotionEvent.ACTION_HOVER_ENTER -> {
                view.background = rounded(Theme.accentColor.withAlpha(45), radiusDp)
                if (scale > 1f) view.animate().scaleX(scale).scaleY(scale).setDuration(120).start()
            }
            MotionEvent.ACTION_HOVER_EXIT -> {
                view.background = null
                if (scale > 1f) view.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
            }
        }
        false
    }
    v.setOnFocusChangeListener { view, has ->
        view.background = if (has) rounded(Theme.accentColor.withAlpha(35), radiusDp, Theme.accentColor, 2) else null
    }
}

/** Grow-on-hover only, for views whose background must stay untouched (round tray buttons). */
fun Context.hoverScale(v: View, scale: Float = 1.12f) {
    if (Build.VERSION.SDK_INT >= 24) v.pointerIcon = PointerIcon.getSystemIcon(this, PointerIcon.TYPE_HAND)
    v.setOnHoverListener { view, e ->
        when (e.action) {
            MotionEvent.ACTION_HOVER_ENTER -> view.animate().scaleX(scale).scaleY(scale).setDuration(120).start()
            MotionEvent.ACTION_HOVER_EXIT -> view.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
        }
        false
    }
}

/** Tooltips exist from Android 8; older versions simply skip them. */
fun View.tip(text: CharSequence) {
    if (Build.VERSION.SDK_INT >= 26) tooltipText = text
}

fun isOverView(v: View?, x: Float, y: Float): Boolean {
    if (v == null || v.visibility != View.VISIBLE || v.width == 0) return false
    val loc = IntArray(2)
    v.getLocationOnScreen(loc)
    return x >= loc[0] && x <= loc[0] + v.width && y >= loc[1] && y <= loc[1] + v.height
}

class MenuEntry(val iconRes: Int, val label: String, val sub: Boolean, val action: () -> Unit)

class SimpleTextWatcher(private val changed: (String) -> Unit) : TextWatcher {
    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { changed(s?.toString() ?: "") }
    override fun afterTextChanged(s: Editable?) {}
}

/** ScrollView that wraps its content but never grows beyond [maxHeightPx]. */
class MaxHeightScrollView(context: Context) : ScrollView(context) {
    var maxHeightPx: Int = Int.MAX_VALUE

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        val size = View.MeasureSpec.getSize(heightSpec)
        val limit = if (size > 0) minOf(maxHeightPx, size) else maxHeightPx
        super.onMeasure(widthSpec, View.MeasureSpec.makeMeasureSpec(limit, View.MeasureSpec.AT_MOST))
    }
}
