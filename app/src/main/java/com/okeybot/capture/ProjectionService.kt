package com.okeybot.capture

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import com.okeybot.vision.ScreenVision

/** Captures at most 3 frames/s; unfamiliar screens never cause a gesture. */
class ProjectionService : Service() {
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var lastFrame = 0L
    override fun onBind(intent: Intent?) = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stopSelf(); return START_NOT_STICKY }
        startForeground(NOTIFICATION_ID, notification())
        val result = intent?.getIntExtra(EXTRA_RESULT, Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
        @Suppress("DEPRECATION") val data = intent?.getParcelableExtra<Intent>(EXTRA_DATA)
        if (result != Activity.RESULT_OK || data == null) { stopSelf(); return START_NOT_STICKY }
        val mgr = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection = mgr.getMediaProjection(result, data)
        projection?.registerCallback(object : MediaProjection.Callback() { override fun onStop() { stopSelf() } }, Handler(Looper.getMainLooper()))
        val metrics = resources.displayMetrics
        reader = ImageReader.newInstance(metrics.widthPixels, metrics.heightPixels, PixelFormat.RGBA_8888, 2)
        reader?.setOnImageAvailableListener({ source ->
            val image = source.acquireLatestImage() ?: return@setOnImageAvailableListener
            try {
                val now = SystemClock.elapsedRealtime()
                if (now - lastFrame < 333) return@setOnImageAvailableListener
                lastFrame = now
                val plane = image.planes[0]; val bitmap = Bitmap.createBitmap(plane.rowStride / plane.pixelStride, image.height, Bitmap.Config.ARGB_8888)
                bitmap.copyPixelsFromBuffer(plane.buffer)
                val frame = Bitmap.createBitmap(bitmap, 0, 0, image.width, image.height); bitmap.recycle()
                ScreenVision.onFrame(applicationContext, frame); frame.recycle()
            } finally { image.close() }
        }, Handler(Looper.getMainLooper()))
        display = projection?.createVirtualDisplay("OkeyBotCapture", metrics.widthPixels, metrics.heightPixels, metrics.densityDpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader?.surface, null, null)
        return START_NOT_STICKY
    }
    private fun notification(): Notification {
        val channel = "capture"
        if (Build.VERSION.SDK_INT >= 26) getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(channel, "Screen capture", NotificationManager.IMPORTANCE_LOW))
        val stop = PendingIntent.getService(this, 1, Intent(this, ProjectionService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return Notification.Builder(this, channel).setSmallIcon(android.R.drawable.ic_menu_view).setContentTitle("OkeyBot capture active").setContentText("Screen analysis running").addAction(Notification.Action.Builder(null, "Stop", stop).build()).setOngoing(true).build()
    }
    override fun onDestroy() { display?.release(); reader?.close(); projection?.stop(); super.onDestroy() }
    companion object {
        const val EXTRA_RESULT = "projection_result"; const val EXTRA_DATA = "projection_data"; const val ACTION_STOP = "com.okeybot.STOP_CAPTURE"; private const val NOTIFICATION_ID = 61
        fun stop(context: Context) { context.startService(Intent(context, ProjectionService::class.java).setAction(ACTION_STOP)) }
    }
}
