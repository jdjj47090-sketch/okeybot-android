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
    @Volatile var listener: ((ScreenObservation) -> Unit)? = null
    private val openCvReady by lazy { runCatching { OpenCVLoader.initLocal() }.getOrDefault(false) }
    fun onFrame(context: Context, bitmap: Bitmap) {
        val previous = latestFrame
        latestFrame = bitmap.copy(Bitmap.Config.ARGB_8888, false)
        if (previous !== bitmap) previous?.recycle()
        if (!openCvReady) { latest = ScreenObservation(GameState.UNKNOWN, reason = "OpenCV native runtime could not load"); listener?.invoke(latest); return }
        val mat = Mat()
        try {
            Utils.bitmapToMat(bitmap, mat)
            latest = ScreenObservation(GameState.UNKNOWN, reason = "Game templates are not configured; actions disabled")
            if (debugImages) saveDebug(context, bitmap)
            listener?.invoke(latest)
        } catch (e: Exception) { latest = ScreenObservation(GameState.UNKNOWN, reason = "Vision error: ${e.message}"); Log.e("OkeyBotVision", latest.reason, e) }
        finally { mat.release() }
    }
    private fun saveDebug(context: Context, source: Bitmap) {
        val dir = File(context.getExternalFilesDir(null), "debug").apply { mkdirs() }
        FileOutputStream(File(dir, "frame-${System.currentTimeMillis()}.png")).use { source.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
