package com.okeybot.ui

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.okeybot.vision.ScreenVision

class LogActivity : Activity() {
    private lateinit var log: TextView
    private val ar get() = arabicUi()
    override fun onStart() { super.onStart(); ScreenVision.activityStarted() }
    override fun onStop() { ScreenVision.activityStopped(); super.onStop() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; UiKit.rtl(this, ar); setPadding(UiKit.dp(this@LogActivity, 20), UiKit.dp(this@LogActivity, 18), UiKit.dp(this@LogActivity, 20), UiKit.dp(this@LogActivity, 16)); setBackgroundColor(UiKit.BG) }
        root.addView(UiKit.text(this, if (ar) "سجل الرؤية" else "Vision log", 26f, UiKit.INK, true))
        root.addView(UiKit.text(this, if (ar) "قراءات الشاشة والاقتراحات الحالية" else "Current screen readings and suggestions", 14f, UiKit.MUTED).apply { setPadding(0, 0, 0, UiKit.dp(this@LogActivity, 14)) })
        log = UiKit.text(this, "", 15f, UiKit.INK).apply { setPadding(UiKit.dp(this@LogActivity, 16), UiKit.dp(this@LogActivity, 16), UiKit.dp(this@LogActivity, 16), UiKit.dp(this@LogActivity, 16)); background = UiKit.panel(this@LogActivity); gravity = if (ar) Gravity.RIGHT else Gravity.LEFT }
        root.addView(log, LinearLayout.LayoutParams(-1, -1))
        setContentView(ScrollView(this).apply { addView(root) })
        update()
        ScreenVision.listener = { runOnUiThread { update() } }
    }
    private fun update() {
        val state = ScreenVision.latest
        val layout = ScreenVision.latestLayout
        log.text = if (ar) "الحالة\n${state.state}\n\nالتفاصيل\n${state.reason}\n\nالبلاطات المكتشفة\n${layout.rackTiles.size}\n\nاقتراح موضع السحب\n${layout.drawPile?.let { "موجود · الثقة ${"%.2f".format(layout.drawConfidence)}" } ?: "غير موجود"}\n\nلا ينفّذ التطبيق حركة قبل اكتمال المعايرة والتعرّف على الشاشة." else "STATE\n${state.state}\n\nDETAILS\n${state.reason}\n\nDETECTED RACK TILES\n${layout.rackTiles.size}\n\nDRAW LOCATION\n${layout.drawPile?.let { "Found · confidence ${"%.2f".format(layout.drawConfidence)}" } ?: "Not found"}\n\nNo move is issued until calibration and screen recognition are complete."
    }
    override fun onDestroy() { if (ScreenVision.listener != null) ScreenVision.listener = null; super.onDestroy() }
}
