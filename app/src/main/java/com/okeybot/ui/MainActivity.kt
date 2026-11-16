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
        ScreenVision.listener = { observation -> runOnUiThread { status.text = "Vision: ${observation.state} — ${observation.reason}" } }
    }

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
        status.text = "Capture active; waiting for calibrated screen recognition."
    }

    private fun render() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(24, 32, 24, 24)
            setBackgroundColor(0xFFF3F1EA.toInt())
        }
        root.addView(TextView(this).apply { text = "OkeyBot"; textSize = 30f; setTextColor(0xFF173A32.toInt()) })
        status = TextView(this).apply { text = "Checking required permissions…"; textSize = 16f; setPadding(8, 24, 8, 24) }
        root.addView(status)
        button(root, "Start screen capture") { requestProjectionPermission() }
        button(root, "Stop") { ProjectionService.stop(this); stopService(Intent(this, StopOverlayService::class.java)); status.text = "Stopped" }
        button(root, "Calibrate regions") { startActivity(Intent(this, CalibrationActivity::class.java)) }
        button(root, "Accessibility setup") { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        button(root, "Allow floating stop button") {
            if (!Settings.canDrawOverlays(this)) startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            else startService(Intent(this, StopOverlayService::class.java))
        }
        button(root, "Show analysis log") { startActivity(Intent(this, LogActivity::class.java)) }
        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun button(parent: LinearLayout, title: String, action: () -> Unit) {
        parent.addView(Button(this).apply { text = title; setOnClickListener { action() } }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 8 })
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
