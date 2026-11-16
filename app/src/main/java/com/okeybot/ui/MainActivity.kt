package com.okeybot.ui

import android.app.Activity
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
import com.okeybot.control.StopOverlayService
import com.okeybot.vision.ScreenVision

class MainActivity : Activity() {
    private lateinit var status: TextView
    private val projectionRequest = 71
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); render(); ScreenVision.listener = { runOnUiThread { status.text = "Vision: ${it.state} — ${it.reason}" } } }
    private fun render() {
        val root = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; gravity=Gravity.CENTER_HORIZONTAL; setPadding(24,32,24,24); setBackgroundColor(0xFFF3F1EA.toInt()) }
        root.addView(TextView(this).apply { text="OkeyBot"; textSize=30f; setTextColor(0xFF173A32.toInt()) })
        status = TextView(this).apply { text="Stopped. Start screen capture to inspect a frame."; textSize=16f; setPadding(8,24,8,24) }; root.addView(status)
        button(root,"Start screen capture") { (getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager).createScreenCaptureIntent().also { startActivityForResult(it, projectionRequest) } }
        button(root,"Stop") { ProjectionService.stop(this); stopService(Intent(this, StopOverlayService::class.java)); status.text="Stopped" }
        button(root,"Calibrate regions") { startActivity(Intent(this, CalibrationActivity::class.java)) }
        button(root,"Accessibility setup") { startActivity(Intent("android.settings.ACCESSIBILITY_SETTINGS")) }
        button(root,"Allow floating stop button") { if (!Settings.canDrawOverlays(this)) startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))) else startService(Intent(this, StopOverlayService::class.java)) }
        button(root,"Show analysis log") { startActivity(Intent(this, LogActivity::class.java)) }
        if (Build.VERSION.SDK_INT >= 33) ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 72)
        setContentView(ScrollView(this).apply { addView(root) })
    }
    private fun button(parent: LinearLayout, title: String, action: () -> Unit) { parent.addView(Button(this).apply { text=title; setOnClickListener { action() } }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin=8 }) }
    @Deprecated("MediaProjection activity result") override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) { super.onActivityResult(requestCode,resultCode,data); if(requestCode==projectionRequest && resultCode==RESULT_OK && data!=null) { val intent=Intent(this,ProjectionService::class.java).putExtra(ProjectionService.EXTRA_RESULT,resultCode).putExtra(ProjectionService.EXTRA_DATA,data); if(Build.VERSION.SDK_INT>=26) startForegroundService(intent) else startService(intent); if(Settings.canDrawOverlays(this)) startService(Intent(this,StopOverlayService::class.java)); status.text="Capture active; waiting for calibrated screen recognition." } }
}
