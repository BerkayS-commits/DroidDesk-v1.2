package com.droiddesk.launcher

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.TextWatcher

fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

fun Context.rounded(color: Int, radiusDp: Int, strokeColor: Int = 0, strokeDp: Int = 0): GradientDrawable {
    val d = GradientDrawable()
    d.setColor(color)
    d.cornerRadius = dp(radiusDp).toFloat()
    if (strokeDp > 0) d.setStroke(dp(strokeDp), strokeColor)
    return d
}

class SimpleTextWatcher(private val changed: (String) -> Unit) : TextWatcher {
    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { changed(s?.toString() ?: "") }
    override fun afterTextChanged(s: Editable?) {}
}
