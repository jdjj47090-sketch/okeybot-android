package com.okeybot.ui

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.*
import androidx.core.app.ActivityCompat
import com.okeybot.capture.ProjectionService
import com.okeybot.control.GestureService
import com.okeybot.control.StopOverlayService
import com.okeybot.vision.ScreenVision

/** Requests each required permission at launch and exits if the user declines any of them. */
class MainActivity : Activity() {
    private lateinit var status: TextView
    private val projectionRequest = 71
    private var permissionFlowStarted = false
    private var permissionFlowComplete = false
    private var pendingSettingsPermission: String? = null
    private var awaitingNotificationResult = false
    private var awaitingProjectionResult = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        render()
        ScreenVision.listener = { observation -> runOnUiThread { status.text = if (arabicUi()) "الحالة · ${observation.state} — ${observation.reason}" else "STATUS · ${observation.state} — ${observation.reason}" } }
    }

    override fun onStart() { super.onStart(); ScreenVision.activityStarted() }
    override fun onStop() { ScreenVision.activityStopped(); super.onStop() }

    override fun onResume() {
        super.onResume()
        val returnedPermission = pendingSettingsPermission
        if (returnedPermission != null) {
            pendingSettingsPermission = null
            if (!isGranted(returnedPermission)) { exitForMissingPermission(); return }
            continuePermissionFlow()
            return
        }
        if (permissionFlowComplete && !preProjectionPermissionsGranted()) {
            exitForMissingPermission()
            return
        }
        if (!permissionFlowStarted) {
            permissionFlowStarted = true
            window.decorView.post { continuePermissionFlow() }
        }
    }

    private fun continuePermissionFlow() {
        if (isFinishing || isDestroyed || awaitingNotificationResult || awaitingProjectionResult) return
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            awaitingNotificationResult = true
            ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
            return
        }
        if (!Settings.canDrawOverlays(this)) {
            pendingSettingsPermission = PERMISSION_OVERLAY
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            return
        }
        if (!isAccessibilityEnabled()) {
            pendingSettingsPermission = PERMISSION_ACCESSIBILITY
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            return
        }
        permissionFlowComplete = true
        requestProjectionPermission()
    }

    private fun preProjectionPermissionsGranted(): Boolean =
        (Build.VERSION.SDK_INT < 33 || checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED) &&
            Settings.canDrawOverlays(this) && isAccessibilityEnabled()

    private fun isGranted(permission: String): Boolean = when (permission) {
        PERMISSION_OVERLAY -> Settings.canDrawOverlays(this)
        PERMISSION_ACCESSIBILITY -> isAccessibilityEnabled()
        else -> false
    }

    private fun isAccessibilityEnabled(): Boolean {
        if (Settings.Secure.getInt(contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0) != 1) return false
        val expected = ComponentName(this, GestureService::class.java).flattenToString()
        return Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?.split(':')?.any { it.equals(expected, ignoreCase = true) } == true
    }

    private fun requestProjectionPermission() {
        if (awaitingProjectionResult) return
        awaitingProjectionResult = true
        val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(manager.createScreenCaptureIntent(), projectionRequest)
    }

    private fun exitForMissingPermission() {
        Toast.makeText(this, "كل الصلاحيات مطلوبة لتشغيل OkeyBot", Toast.LENGTH_LONG).show()
        finishAffinity()
    }

    private fun startCapture(resultCode: Int, data: Intent) {
        val serviceIntent = Intent(this, ProjectionService::class.java)
            .putExtra(ProjectionService.EXTRA_RESULT, resultCode)
            .putExtra(ProjectionService.EXTRA_DATA, data)
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(serviceIntent) else startService(serviceIntent)
        startService(Intent(this, StopOverlayService::class.java))
        status.text = if (arabicUi()) "الحالة · الالتقاط يعمل، بانتظار معايرة الشاشة" else "STATUS · CAPTURE ACTIVE · WAITING FOR CALIBRATION"
    }

    private fun render() {
        val ar = arabicUi()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; UiKit.rtl(this, ar)
            setPadding(UiKit.dp(this@MainActivity, 22), UiKit.dp(this@MainActivity, 18), UiKit.dp(this@MainActivity, 22), UiKit.dp(this@MainActivity, 26))
            setBackgroundColor(UiKit.BG)
        }
        val scroll = ScrollView(this).apply { clipToPadding = false; addView(root) }
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; UiKit.rtl(this, ar) }
        val brand = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, -2, 1f) }
        brand.addView(UiKit.text(this, if (ar) "أوكي بوت" else "OkeyBot", 28f, UiKit.INK, true))
        brand.addView(UiKit.text(this, if (ar) "مساعد اللعب الذكي" else "Your tabletop companion", 13f, UiKit.MUTED))
        top.addView(brand)
        val language = UiKit.action(this, if (ar) "EN" else "عربي", false) {
            getSharedPreferences("ui", 0).edit().putString("language", if (ar) "en" else "ar").apply(); render()
        }.apply { minWidth = UiKit.dp(this@MainActivity, 64); minHeight = UiKit.dp(this@MainActivity, 44) }
        top.addView(language)
        root.addView(top, UiKit.margin(this, 22))

        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; UiKit.rtl(this, ar); setPadding(UiKit.dp(this@MainActivity, 20), UiKit.dp(this@MainActivity, 20), UiKit.dp(this@MainActivity, 20), UiKit.dp(this@MainActivity, 20))
            background = UiKit.panel(this@MainActivity, 0xFF173A32.toInt(), 24)
        }
        val badge = TextView(this).apply { text = if (ar) "●  جاهز للاتصال" else "●  READY TO CONNECT"; textSize = 12f; setTextColor(0xFFB5D8A8.toInt()); setPadding(0, 0, 0, UiKit.dp(this@MainActivity, 12)) }
        hero.addView(badge)
        hero.addView(UiKit.text(this, if (ar) "خلّ اللعب يمشي." else "Let the game flow.", 23f, 0xFFFFFEF9.toInt(), true))
        hero.addView(UiKit.text(this, if (ar) "عاير الشاشة أولًا، ثم ابدأ الالتقاط. لن ينفّذ التطبيق حركة على شاشة غير معروفة." else "Calibrate your table, then start capture. Unknown screens are never tapped.", 14f, 0xFFD1DED4.toInt()).apply { setPadding(0, UiKit.dp(this@MainActivity, 5), 0, UiKit.dp(this@MainActivity, 4)) })
        status = UiKit.text(this, if (ar) "الحالة · بانتظار الصلاحيات" else "STATUS · WAITING FOR PERMISSIONS", 12f, 0xFFAED2A2.toInt(), true).apply { setPadding(0, UiKit.dp(this@MainActivity, 12), 0, 0) }
        hero.addView(status)
        root.addView(hero, UiKit.margin(this, 20))

        root.addView(UiKit.text(this, if (ar) "لوحة التحكم" else "CONTROL DESK", 12f, UiKit.MUTED, true), UiKit.margin(this, 8))
        root.addView(UiKit.action(this, if (ar) "بدء التقاط الشاشة" else "Start screen capture", true) { requestProjectionPermission() }, UiKit.margin(this, 10))
        root.addView(UiKit.action(this, if (ar) "معايرة مناطق اللعب" else "Calibrate play areas", false) { startActivity(Intent(this, CalibrationActivity::class.java)) }, UiKit.margin(this, 18))

        root.addView(UiKit.text(this, if (ar) "الأدوات" else "TOOLS", 12f, UiKit.MUTED, true), UiKit.margin(this, 8))
        val tools = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; UiKit.rtl(this, ar); setPadding(UiKit.dp(this@MainActivity, 14), UiKit.dp(this@MainActivity, 8), UiKit.dp(this@MainActivity, 14), UiKit.dp(this@MainActivity, 2)); background = UiKit.panel(this@MainActivity) }
        fun tool(label: String, action: () -> Unit) {
            val row = TextView(this).apply { text = label; textSize = 15f; setTextColor(UiKit.INK); gravity = if (ar) Gravity.RIGHT or Gravity.CENTER_VERTICAL else Gravity.LEFT or Gravity.CENTER_VERTICAL; setPadding(UiKit.dp(this@MainActivity, 7), 0, UiKit.dp(this@MainActivity, 7), 0); minHeight = UiKit.dp(this@MainActivity, 48); isClickable = true; setOnClickListener { action() } }
            tools.addView(row, UiKit.margin(this, 2))
        }
        tool(if (ar) "سجل التحليل" else "Analysis log") { startActivity(Intent(this, LogActivity::class.java)) }
        tool(if (ar) "خدمات إمكانية الوصول" else "Accessibility settings") { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        tool(if (ar) "إظهار زر الإيقاف العائم" else "Show floating stop button") {
            if (!Settings.canDrawOverlays(this)) startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            else startService(Intent(this, StopOverlayService::class.java))
        }
        root.addView(tools, UiKit.margin(this, 12))
        root.addView(UiKit.action(this, if (ar) "إيقاف الالتقاط" else "Stop capture", false) { ProjectionService.stop(this); stopService(Intent(this, StopOverlayService::class.java)); status.text = if (ar) "الحالة · متوقف" else "STATUS · STOPPED" }, UiKit.margin(this, 8))
        setContentView(scroll)
    }

    @Deprecated("Permission callback for the required launch gate")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_NOTIFICATIONS) {
            awaitingNotificationResult = false
            if (grantResults.firstOrNull() != android.content.pm.PackageManager.PERMISSION_GRANTED) exitForMissingPermission()
            else continuePermissionFlow()
        }
    }

    @Deprecated("MediaProjection activity result")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != projectionRequest) return
        awaitingProjectionResult = false
        if (resultCode == RESULT_OK && data != null && preProjectionPermissionsGranted()) startCapture(resultCode, data)
        else exitForMissingPermission()
    }

    companion object {
        private const val REQUEST_NOTIFICATIONS = 72
        private const val PERMISSION_OVERLAY = "overlay"
        private const val PERMISSION_ACCESSIBILITY = "accessibility"
    }
}
