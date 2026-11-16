package com.okeybot.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import org.opencv.android.Utils
import org.opencv.android.OpenCVLoader
import org.opencv.core.Mat
import java.io.File
import java.io.FileOutputStream

data class ObservedTile(val bounds: RectF, val color: String?, val number: Int?, val confidence: Float)
enum class GameState { MY_TURN, OPPONENT_TURN, POPUP, ROUND_END, NEW_ROUND, UNKNOWN }
data class ScreenObservation(val state: GameState, val rack: List<ObservedTile> = emptyList(), val reason: String)
/** Current screens deliberately classify as UNKNOWN until the game's templates are calibrated. */
object ScreenVision {
    @Volatile var latest = ScreenObservation(GameState.UNKNOWN, reason = "Waiting for a frame")
    @Volatile var latestFrame: Bitmap? = null
    @Volatile var debugImages = false
    @Volatile var latestLayout = LayoutDetection()
    @Volatile var appUiVisible = false
    @Volatile var listener: ((ScreenObservation) -> Unit)? = null
    private val openCvReady by lazy { runCatching { OpenCVLoader.initLocal() }.getOrDefault(false) }
    fun onFrame(context: Context, bitmap: Bitmap) {
        if (appUiVisible) return // Keep the last game frame while OkeyBot's calibration/log UI is open.
        val previous = latestFrame
        latestFrame = bitmap.copy(Bitmap.Config.ARGB_8888, false)
        if (previous !== bitmap) previous?.recycle()
        if (!openCvReady) { latest = ScreenObservation(GameState.UNKNOWN, reason = "OpenCV native runtime could not load"); listener?.invoke(latest); return }
        val mat = Mat()
        try {
            Utils.bitmapToMat(bitmap, mat)
            latestLayout = LayoutDetector.detect(mat)
            val rackTiles = latestLayout.rackTiles.map { ObservedTile(it, null, null, 0.45f) }
            latest = ScreenObservation(GameState.UNKNOWN, rackTiles, "${latestLayout.note}. Tile reading and turn detection still need screenshots.")
            if (debugImages) saveDebug(context, bitmap, latestLayout)
            listener?.invoke(latest)
        } catch (e: Exception) { latest = ScreenObservation(GameState.UNKNOWN, reason = "Vision error: ${e.message}"); Log.e("OkeyBotVision", latest.reason, e) }
        finally { mat.release() }
    }
    private fun saveDebug(context: Context, source: Bitmap, layout: LayoutDetection) {
        val annotated = source.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = android.graphics.Canvas(annotated)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { style = android.graphics.Paint.Style.STROKE; strokeWidth = maxOf(3f, source.width / 360f) }
        paint.color = android.graphics.Color.GREEN
        layout.rackTiles.forEach { canvas.drawRect(it, paint) }
        layout.rackBounds?.let { paint.color = android.graphics.Color.YELLOW; canvas.drawRect(it, paint) }
        layout.drawPile?.let { paint.color = android.graphics.Color.CYAN; canvas.drawRect(it, paint) }
        val dir = File(context.getExternalFilesDir(null), "debug").apply { mkdirs() }
        FileOutputStream(File(dir, "frame-${System.currentTimeMillis()}.png")).use { annotated.compress(Bitmap.CompressFormat.PNG, 100, it) }
        annotated.recycle()
    }
}
