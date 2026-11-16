package com.okeybot.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

internal object UiKit {
    const val BG = 0xFFF1EFE5.toInt()
    const val INK = 0xFF173A32.toInt()
    const val MUTED = 0xFF66736D.toInt()
    const val GREEN = 0xFF1D5948.toInt()
    const val SURFACE = 0xFFFFFEF9.toInt()
    const val LINE = 0xFFD9DED4.toInt()
    fun dp(c: Context, v: Int) = (v * c.resources.displayMetrics.density + .5f).toInt()
    fun rtl(v: View, ar: Boolean) { v.layoutDirection = if (ar) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR }
    fun panel(c: Context, color: Int = SURFACE, radius: Int = 22) = GradientDrawable().apply { setColor(color); cornerRadius = dp(c, radius).toFloat(); setStroke(dp(c, 1), LINE) }
    fun text(c: Context, value: String, size: Float, color: Int = INK, bold: Boolean = false) = TextView(c).apply { text = value; textSize = size; setTextColor(color); if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD) }
    fun action(c: Context, label: String, primary: Boolean, onClick: () -> Unit) = Button(c).apply {
        text = label; textSize = 15f; isAllCaps = false; setTextColor(if (primary) Color.WHITE else INK)
        background = GradientDrawable().apply { setColor(if (primary) GREEN else SURFACE); cornerRadius = dp(c, 16).toFloat(); if (!primary) setStroke(dp(c, 1), LINE) }
        minHeight = dp(c, 54); stateListAnimator = null; setPadding(dp(c, 16), dp(c, 8), dp(c, 16), dp(c, 8)); gravity = Gravity.CENTER; setOnClickListener { onClick() }
    }
    fun margin(c: Context, bottom: Int = 10) = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(c, bottom) }
}
internal fun Context.arabicUi(): Boolean = getSharedPreferences("ui", 0).getString("language", "ar") != "en"
