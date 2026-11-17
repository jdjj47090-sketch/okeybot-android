package com.okeybot.vision

import android.content.Context
import android.graphics.RectF
import android.graphics.BitmapFactory
import com.okeybot.brain.Tile
import com.okeybot.brain.TileColor
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.Rect
import org.opencv.core.Scalar
import org.opencv.imgproc.Imgproc
import kotlin.math.max
import kotlin.math.min

/** On-device tile reading using local glyph templates sampled from the user's game. No network/API. */
object TileRecognizer {
    data class Reading(val tile: Tile?, val confidence: Float, val colorConfidence: Float)
    private val templates = HashMap<Int, Mat>()
    private var loaded = false

    @Synchronized private fun loadTemplates(context: Context) {
        if (loaded) return
        for (n in 1..13) {
            runCatching {
                context.assets.open("number-templates/$n.png").use { stream ->
                    val bitmap = BitmapFactory.decodeStream(stream)
                    val mat = Mat(); Utils.bitmapToMat(bitmap, mat); bitmap.recycle()
                    Imgproc.cvtColor(mat, mat, Imgproc.COLOR_RGBA2GRAY)
                    templates[n] = mat
                }
            }
        }
        loaded = true
    }

    fun read(context: Context, rgba: Mat, bounds: RectF): Reading {
        loadTemplates(context)
        val x = bounds.left.toInt().coerceIn(0, rgba.cols() - 1)
        val y = bounds.top.toInt().coerceIn(0, rgba.rows() - 1)
        val right = bounds.right.toInt().coerceIn(x + 1, rgba.cols())
        val bottom = bounds.bottom.toInt().coerceIn(y + 1, rgba.rows())
        val tile = rgba.submat(Rect(x, y, right - x, bottom - y))
        val hsv = Mat(); val numberColorMask = Mat(); val blackMask = Mat(); val numberMask = Mat()
        try {
            Imgproc.cvtColor(tile, hsv, Imgproc.COLOR_RGBA2RGB)
            Imgproc.cvtColor(hsv, hsv, Imgproc.COLOR_RGB2HSV)
            val left = (tile.cols() * 0.09f).toInt().coerceIn(0, tile.cols() - 1)
            val top = (tile.rows() * 0.08f).toInt().coerceIn(0, tile.rows() - 1)
            val width = (tile.cols() * 0.82f).toInt().coerceAtLeast(1).coerceAtMost(tile.cols() - left)
            val height = (tile.rows() * 0.53f).toInt().coerceAtLeast(1).coerceAtMost(tile.rows() - top)
            val hsvNumbers = hsv.submat(Rect(left, top, width, height))
            try {
                Core.inRange(hsvNumbers, Scalar(0.0, 76.0, 60.0), Scalar(180.0, 255.0, 255.0), numberColorMask)
                Core.inRange(hsvNumbers, Scalar(0.0, 0.0, 0.0), Scalar(180.0, 85.0, 92.0), blackMask)
                val colorPixels = Core.countNonZero(numberColorMask).toFloat()
                val blackPixels = Core.countNonZero(blackMask).toFloat()
                val ink = if (colorPixels > blackPixels * 0.75f) numberColorMask else blackMask
                ink.copyTo(numberMask)
                val colorResult = detectColor(hsvNumbers, colorPixels, blackPixels)
                val numberResult = matchNumber(numberMask)
                val number = numberResult.first
                val tileColor = colorResult.first
                val tileValue = if (number != null && tileColor != null && colorResult.second >= MIN_COLOR_CONFIDENCE) Tile(tileColor, number) else null
                return Reading(tileValue, numberResult.second, colorResult.second)
            } finally { hsvNumbers.release() }
        } finally { tile.release(); hsv.release(); numberColorMask.release(); blackMask.release(); numberMask.release() }
    }

    private fun detectColor(hsv: Mat, colorPixels: Float, blackPixels: Float): Pair<TileColor?, Float> {
        val total = max(1, hsv.rows() * hsv.cols()).toFloat()
        if (colorPixels < total * 0.012f) {
            return if (blackPixels >= total * 0.004f) TileColor.BLACK to (blackPixels / (total * 0.08f)).coerceIn(0f, 0.95f) else null to 0f
        }
        fun count(lowHue: Double, highHue: Double): Int {
            val mask = Mat()
            return try {
                Core.inRange(hsv, Scalar(lowHue, 76.0, 60.0), Scalar(highHue, 255.0, 255.0), mask)
                Core.countNonZero(mask)
            } finally { mask.release() }
        }
        val red = count(0.0, 8.0) + count(171.0, 180.0)
        val yellow = count(9.0, 38.0)
        val blue = count(88.0, 135.0)
        val ranked = listOf(TileColor.RED to red, TileColor.YELLOW to yellow, TileColor.BLUE to blue).sortedByDescending { it.second }
        val first = ranked[0]; val second = ranked[1]
        val confidence = (first.second - second.second).toFloat() / max(1, first.second + second.second)
        return first.first to (0.55f + confidence * 0.45f).coerceAtMost(0.99f)
    }

    private fun matchNumber(mask: Mat): Pair<Int?, Float> {
        val contours = ArrayList<MatOfPoint>(); val hierarchy = Mat()
        val binary = Mat(); val digitCrop = Mat(); val resized = Mat(); val normalized = Mat()
        try {
            val contourInput = mask.clone()
            Imgproc.findContours(contourInput, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
            contourInput.release()
            val boxes = contours.map { Imgproc.boundingRect(it) }.filter { it.height >= mask.rows() * 0.24 && it.width >= mask.cols() * 0.025 && it.area() >= 8 }
            if (boxes.isEmpty()) return null to 0f
            val x0 = boxes.minOf { it.x }; val y0 = boxes.minOf { it.y }
            val x1 = boxes.maxOf { it.x + it.width }; val y1 = boxes.maxOf { it.y + it.height }
            val digitView = mask.submat(Rect(x0, y0, x1 - x0, y1 - y0))
            digitView.copyTo(digitCrop)
            digitView.release()
            Imgproc.threshold(digitCrop, binary, 127.0, 255.0, Imgproc.THRESH_BINARY)
            val scale = min(58.0 / binary.cols(), 58.0 / binary.rows())
            Imgproc.resize(binary, resized, org.opencv.core.Size(binary.cols() * scale, binary.rows() * scale), 0.0, 0.0, Imgproc.INTER_AREA)
            normalized.create(64, 64, org.opencv.core.CvType.CV_8UC1)
            normalized.setTo(Scalar(0.0))
            val dx = (64 - resized.cols()) / 2; val dy = (64 - resized.rows()) / 2
            val target = normalized.submat(Rect(dx, dy, resized.cols(), resized.rows()))
            resized.copyTo(target)
            target.release()
            var bestNumber: Int? = null; var best = Double.MAX_VALUE; var second = Double.MAX_VALUE
            templates.forEach { (number, template) ->
                val diff = Mat(); Core.absdiff(normalized, template, diff)
                val score = Core.sumElems(diff).`val`[0] / (64.0 * 64.0 * 255.0)
                diff.release()
                if (score < best) { second = best; best = score; bestNumber = number } else if (score < second) second = score
            }
            val confidence = (1.0 - best * 1.8).coerceIn(0.0, 1.0).toFloat()
            return if (confidence >= MIN_NUMBER_CONFIDENCE && (second - best >= MIN_MARGIN || confidence >= VERY_CLEAR_CONFIDENCE)) bestNumber to confidence else null to confidence
        } finally { contours.forEach { it.release() }; hierarchy.release(); binary.release(); digitCrop.release(); resized.release(); normalized.release() }
    }

    private const val MIN_NUMBER_CONFIDENCE = 0.52f
    private const val MIN_COLOR_CONFIDENCE = 0.54f
    private const val MIN_MARGIN = 0.025
    private const val VERY_CLEAR_CONFIDENCE = 0.88f
}
