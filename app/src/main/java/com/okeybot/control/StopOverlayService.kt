package com.okeybot.control

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.*
import android.widget.TextView
import com.okeybot.capture.ProjectionService

class StopOverlayService : Service() {
    private var view: View? = null
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onCreate() {
        super.onCreate(); if (!Settings.canDrawOverlays(this)) { stopSelf(); return }
        val label = TextView(this).apply { text = "STOP BOT"; setTextColor(-1); setBackgroundColor(0xDD9D2424.toInt()); setPadding(24, 16, 24, 16); setOnClickListener { ProjectionService.stop(this@StopOverlayService); stopSelf() } }
        val type = if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
        val p = WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT, type, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP or Gravity.END; x = 20; y = 180 }
        getSystemService(WindowManager::class.java).addView(label, p); view = label
        label.setOnTouchListener(object : View.OnTouchListener { var sx=0f; var sy=0f; var ox=0; var oy=0; override fun onTouch(v: View, e: MotionEvent): Boolean { if (e.action == MotionEvent.ACTION_DOWN) { sx=e.rawX; sy=e.rawY; ox=p.x; oy=p.y } else if (e.action == MotionEvent.ACTION_MOVE) { p.x=(ox + sx-e.rawX).toInt(); p.y=(oy+e.rawY-sy).toInt(); getSystemService(WindowManager::class.java).updateViewLayout(v,p) }; return e.action != MotionEvent.ACTION_UP } })
    }
    override fun onDestroy() { view?.let { getSystemService(WindowManager::class.java).removeView(it) }; super.onDestroy() }
}
