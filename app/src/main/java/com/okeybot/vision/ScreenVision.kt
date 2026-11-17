package com.okeybot.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import com.okeybot.brain.Tile
import com.okeybot.calibration.CalibrationStore
import com.okeybot.calibration.Region
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Mat
import java.io.File
import java.io.FileOutputStream

data class ObservedTile(val bounds: RectF, val color: String?, val number: Int?, val confidence: Float)
enum class GameState { MY_TURN, OPPONENT_TURN, POPUP, ROUND_END, NEW_ROUND, UNKNOWN }
data class ScreenObservation(
    val state: GameState,
    val rack: List<ObservedTile> = emptyList(),
    val reason: String,
    val indicator: Tile? = null,
    val discardTop: Tile? = null
)
/** Vision never guesses a turn until turn-state templates are available and confidently matched. */
object ScreenVision {
    @Volatile var latest = ScreenObservation(GameState.UNKNOWN, reason = "Waiting for a frame")
    @Volatile var latestFrame: Bitmap? = null
    @Volatile var debugImages = false
    @Volatile var latestLayout = LayoutDetection()
    @Volatile var appUiVisible = false
    private val visibleActivities = java.util.concurrent.atomic.AtomicInteger(0)
    @Volatile var listener: ((ScreenObservation) -> Unit)? = null
    private val openCvReady by lazy { runCatching { OpenCVLoader.initLocal() }.getOrDefault(false) }
    fun activityStarted() { appUiVisible = visibleActivities.incrementAndGet() > 0 }
    fun activityStopped() { appUiVisible = visibleActivities.updateAndGet { (it - 1).coerceAtLeast(0) } > 0 }

    fun onFrame(context: Context, bitmap: Bitmap) {
        if (appUiVisible) return
        val previous = latestFrame
        latestFrame = bitmap.copy(Bitmap.Config.ARGB_8888, false)
        if (previous !== bitmap) previous?.recycle()
        if (!openCvReady) { publish(ScreenObservation(GameState.UNKNOWN, reason = "OpenCV native runtime could not load")); return }
        val mat = Mat()
        try {
            Utils.bitmapToMat(bitmap, mat)
            val calibration = CalibrationStore(context)
            latestLayout = LayoutDetector.detect(mat, calibration.get(Region.RACK), calibration.get(Region.DRAW_PILE))
            val rack = latestLayout.rackTiles.map { rect ->
                val read = TileRecognizer.read(context, mat, rect)
                ObservedTile(rect, read.tile?.color?.name, read.tile?.number, minOf(read.confidence, read.colorConfidence))
            }
            val indicator = calibration.get(Region.INDICATOR)?.let { rect -> TileRecognizer.read(context, mat, toPixels(rect, mat.cols(), mat.rows())).tile }
            val discard = calibration.get(Region.DISCARD)?.let { rect -> TileRecognizer.read(context, mat, toPixels(rect, mat.cols(), mat.rows())).tile }
            val recognized = rack.count { it.number != null && it.color != null }
            val reason = when {
                calibration.get(Region.RACK) == null -> "Select the two rack corners in Calibration first"
                recognized < 14 -> "Recognized $recognized/${rack.size} rack tiles; template confidence is insufficient"
                indicator == null -> "Rack read; set the indicator region to identify the Okey tile"
                else -> "Rack and indicator read. Waiting for a verified turn-state cue before any play gesture."
            }
            publish(ScreenObservation(GameState.UNKNOWN, rack, reason, indicator, discard))
            if (debugImages) saveDebug(context, bitmap, latestLayout, rack)
        } catch (e: Exception) {
            val error = "Vision error: ${e.message}"
            publish(ScreenObservation(GameState.UNKNOWN, reason = error)); Log.e("OkeyBotVision", error, e)
        } finally { mat.release() }
    }

    private fun publish(observation: ScreenObservation) { latest = observation; listener?.invoke(observation) }
    private fun toPixels(rect: RectF, width: Int, height: Int) = RectF(rect.left * width, rect.top * height, rect.right * width, rect.bottom * height)

    private fun saveDebug(context: Context, source: Bitmap, layout: LayoutDetection, tiles: List<ObservedTile>) {
        val annotated = source.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = android.graphics.Canvas(annotated)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { style = android.graphics.Paint.Style.STROKE; strokeWidth = maxOf(3f, source.width / 360f) }
        tiles.forEach { t ->
            paint.color = if (t.number == null) android.graphics.Color.RED else android.graphics.Color.GREEN
            canvas.drawRect(t.bounds, paint)
            paint.style = android.graphics.Paint.Style.FILL; paint.textSize = maxOf(24f, source.width / 42f)
            canvas.drawText(t.number?.toString() ?: "?", t.bounds.left, t.bounds.top - 4, paint); paint.style = android.graphics.Paint.Style.STROKE
        }
        layout.rackBounds?.let { paint.color = android.graphics.Color.YELLOW; canvas.drawRect(it, paint) }
        layout.drawPile?.let { paint.color = android.graphics.Color.CYAN; canvas.drawRect(it, paint) }
        val dir = File(context.getExternalFilesDir(null), "debug").apply { mkdirs() }
        FileOutputStream(File(dir, "frame-${System.currentTimeMillis()}.png")).use { annotated.compress(Bitmap.CompressFormat.PNG, 100, it) }
        annotated.recycle()
    }
}
