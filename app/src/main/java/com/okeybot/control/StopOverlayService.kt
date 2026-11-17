package com.okeybot.control

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.okeybot.capture.ProjectionService
import com.okeybot.ui.CalibrationActivity
import com.okeybot.ui.LogActivity
import com.okeybot.ui.MainActivity

/** Draggable in-game control panel. It only pauses/stops screen capture; gestures stay explicit. */
class StopOverlayService : Service() {
    private lateinit var windowManager: WindowManager
    private var overlay: View? = null
    private lateinit var params: WindowManager.LayoutParams
    private var expanded = false
    private var statusLabel: TextView? = null
    private var pauseButton: Button? = null
    private var startButton: Button? = null
    private val arabic get() = getSharedPreferences("ui", MODE_PRIVATE).getString("language", "ar") != "en"
    private val handler = Handler(Looper.getMainLooper())
    private val refreshLoop = object : Runnable { override fun run() { refreshState(); handler.postDelayed(this, 500) } }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return }
        windowManager = getSystemService(WindowManager::class.java)
        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.END; x = dp(14); y = dp(170) }
        showCollapsed()
        handler.postDelayed(refreshLoop, 500)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int { refreshState(); return START_STICKY }

    private fun showCollapsed() {
        expanded = false
        val bubble = TextView(this).apply {
            text = if (arabic) "أوكي" else "OK"
            textSize = 14f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            background = rounded(0xFF1D5948.toInt(), 28)
            elevation = dp(8).toFloat(); contentDescription = if (arabic) "فتح لوحة تحكم أوكي بوت" else "Open OkeyBot controls"
            setPadding(dp(14), dp(9), dp(14), dp(9))
        }
        makeDraggable(bubble) { expandPanel() }
        replaceView(bubble)
        refreshState()
    }

    private fun expandPanel() {
        expanded = true
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = if (arabic) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
            setPadding(dp(12), dp(10), dp(12), dp(12))
            background = rounded(0xF21C302A.toInt(), 22)
            elevation = dp(12).toFloat()
        }
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val title = TextView(this).apply {
            text = if (arabic) "أوكي بوت · تحكم سريع" else "OkeyBot · Quick controls"
            textSize = 14f; setTextColor(Color.WHITE); setTypeface(typeface, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, dp(44), 1f); gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(title)
        val collapse = TextView(this).apply { text = "×"; textSize = 25f; gravity = Gravity.CENTER; setTextColor(Color.WHITE); minWidth = dp(42); minHeight = dp(42); setOnClickListener { showCollapsed() } }
        header.addView(collapse)
        panel.addView(header)

        statusLabel = TextView(this).apply {
            textSize = 12f; setTextColor(0xFFBFD7C8.toInt()); setPadding(dp(5), 0, dp(5), dp(7))
        }
        panel.addView(statusLabel)
        startButton = panelButton(if (arabic) "بدء الالتقاط" else "Start capture") { requestCapture() }
        panel.addView(startButton, buttonLayout())
        pauseButton = panelButton("") { togglePause() }
        panel.addView(pauseButton, buttonLayout())
        panel.addView(panelButton(if (arabic) "معايرة المناطق" else "Calibrate areas") { open(CalibrationActivity::class.java) }, buttonLayout())
        panel.addView(panelButton(if (arabic) "سجل التحليل" else "Analysis log") { open(LogActivity::class.java) }, buttonLayout())
        panel.addView(panelButton(if (arabic) "إيقاف الالتقاط فورًا" else "Stop capture now", danger = true) {
            ProjectionService.stop(this)
            Toast.makeText(this, if (arabic) "تم إرسال أمر الإيقاف" else "Capture stop requested", Toast.LENGTH_SHORT).show()
            refreshState()
        }, buttonLayout(0))
        makeDraggable(title) { }
        replaceView(panel)
        refreshState()
    }

    private fun panelButton(label: String, danger: Boolean = false, action: () -> Unit) = Button(this).apply {
        text = label; textSize = 13f; isAllCaps = false; setTextColor(Color.WHITE); gravity = Gravity.CENTER
        background = rounded(if (danger) 0xFF873B38.toInt() else 0xFF315747.toInt(), 14)
        stateListAnimator = null; minHeight = dp(43); setPadding(dp(8), dp(4), dp(8), dp(4)); setOnClickListener { action() }
    }

    private fun requestCapture() {
        val intent = Intent(this, MainActivity::class.java)
            .setAction(MainActivity.ACTION_CAPTURE_FROM_OVERLAY)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        startActivity(intent)
    }

    private fun togglePause() {
        if (ProjectionService.isPaused) ProjectionService.resume(this) else ProjectionService.pause(this)
        refreshState()
    }

    private fun refreshState() {
        if (expanded) {
            val running = ProjectionService.isRunning
            statusLabel?.text = when {
                !running -> if (arabic) "الحالة · متوقف" else "Status · stopped"
                ProjectionService.isPaused -> if (arabic) "الحالة · الالتقاط متوقف مؤقتًا" else "Status · capture paused"
                else -> if (arabic) "الحالة · الالتقاط يعمل" else "Status · capturing"
            }
            startButton?.visibility = if (running) View.GONE else View.VISIBLE
            pauseButton?.visibility = if (running) View.VISIBLE else View.GONE
            pauseButton?.text = if (ProjectionService.isPaused) { if (arabic) "استئناف الالتقاط" else "Resume capture" } else { if (arabic) "إيقاف مؤقت" else "Pause capture" }
        }
    }

    private fun open(activity: Class<*>) {
        startActivity(Intent(this, activity).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun makeDraggable(handle: View, click: () -> Unit) {
        handle.setOnTouchListener(dragListener(click))
    }

    private fun dragListener(click: () -> Unit) = object : View.OnTouchListener {
        private var downX = 0f; private var downY = 0f; private var startX = 0; private var startY = 0; private var moved = false
        override fun onTouch(view: View, event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> { downX = event.rawX; downY = event.rawY; startX = params.x; startY = params.y; moved = false; return true }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - downX).toInt(); val dy = (event.rawY - downY).toInt()
                    if (kotlin.math.abs(dx) + kotlin.math.abs(dy) > dp(5)) moved = true
                    if (moved) { params.x = (startX - dx).coerceAtLeast(0); params.y = (startY + dy).coerceAtLeast(0); overlay?.let { runCatching { windowManager.updateViewLayout(it, params) } } }
                    return true
                }
                MotionEvent.ACTION_UP -> { if (!moved) click(); return true }
            }
            return true
        }
    }

    private fun replaceView(next: View) {
        overlay?.let { runCatching { windowManager.removeView(it) } }
        overlay = next
        runCatching { windowManager.addView(next, params) }.onFailure { stopSelf() }
    }

    private fun buttonLayout(bottom: Int = 7) = LinearLayout.LayoutParams(dp(236), dp(44)).apply { bottomMargin = dp(bottom) }
    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat(); setStroke(dp(1), 0x667D9A89) }
    private fun dp(value: Int) = (value * resources.displayMetrics.density + .5f).toInt()

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        overlay?.let { runCatching { windowManager.removeView(it) } }
        overlay = null
        super.onDestroy()
    }
}
