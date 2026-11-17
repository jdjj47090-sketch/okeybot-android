package com.okeybot.vision

import android.graphics.RectF
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

data class LayoutDetection(
    val rackBounds: RectF? = null,
    val rackTiles: List<RectF> = emptyList(),
    val drawPile: RectF? = null,
    val drawConfidence: Float = 0f,
    val note: String = "No layout detected"
)

/** Game-independent visual proposals. Suggestions are never used to issue gestures by themselves. */
object LayoutDetector {
    fun detect(rgba: Mat): LayoutDetection {
        if (rgba.empty()) return LayoutDetection()
        val rgb = Mat(); val hsv = Mat(); val whiteMask = Mat(); val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0))
        try {
            Imgproc.cvtColor(rgba, rgb, Imgproc.COLOR_RGBA2RGB)
            Imgproc.cvtColor(rgb, hsv, Imgproc.COLOR_RGB2HSV)
            Core.inRange(hsv, Scalar(0.0, 0.0, 158.0), Scalar(180.0, 108.0, 255.0), whiteMask)
            Imgproc.morphologyEx(whiteMask, whiteMask, Imgproc.MORPH_CLOSE, kernel)
            val w = rgba.cols().toFloat(); val h = rgba.rows().toFloat()
            val tileCandidates = rectangles(whiteMask).filter { r ->
                val rw = r.width() / w; val rh = r.height() / h; val aspect = r.width() / r.height()
                r.top >= h * 0.52f && rw in 0.018f..0.16f && rh in 0.024f..0.18f && aspect in 0.22f..1.30f
            }
            val rackRow = bestBottomRow(tileCandidates, h)
            val rackRows = rackRow?.let { bottom ->
                val bottomY = bottom.map(RectF::centerY).average().toFloat()
                val medianTileHeight = bottom.map(RectF::height).sorted()[bottom.size / 2]
                val upper = tileCandidates.filter { it.centerY() < bottomY && bottomY - it.centerY() <= max(h * 0.18f, medianTileHeight * 1.35f) }
                (bottom + upper).distinctBy { listOf(it.left.toInt(), it.top.toInt(), it.right.toInt(), it.bottom.toInt()) }
            }
            val rackBoxes = rackRows?.sortedWith(compareBy<RectF> { it.centerY() }.thenBy { it.left }).orEmpty()
            val rackBounds = rackBoxes.takeIf { it.size >= MIN_RACK_TILES }?.let(::union)
            // In this table layout the stock is a tall stack of ivory tile backs, while the indicator is one face-up tile.
            val draw = findDrawCandidate(whiteMask, w, h, rackBounds)
            return LayoutDetection(
                rackBounds = rackBounds,
                rackTiles = rackBoxes,
                drawPile = draw?.first,
                drawConfidence = draw?.second ?: 0f,
                note = when {
                    rackBounds != null && draw != null -> "Auto rack ${rackBoxes.size} tiles; draw candidate confidence ${"%.2f".format(draw.second)}"
                    rackBounds != null -> "Auto rack ${rackBoxes.size} tiles; draw pile needs calibration"
                    draw != null -> "Draw candidate found; rack needs calibration"
                    else -> "No confident layout; manual calibration is available"
                }
            )
        } finally { rgb.release(); hsv.release(); whiteMask.release(); kernel.release() }
    }

    private fun rectangles(mask: Mat): List<RectF> {
        val contours = ArrayList<MatOfPoint>(); val hierarchy = Mat()
        try {
            Imgproc.findContours(mask, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
            val rects = contours.mapNotNull { contour ->
                val box = Imgproc.boundingRect(contour); val boxArea = box.width.toDouble() * box.height.toDouble(); val area = Imgproc.contourArea(contour)
                if (boxArea == 0.0 || area / boxArea < 0.18) null
                else RectF(box.x.toFloat(), box.y.toFloat(), (box.x + box.width).toFloat(), (box.y + box.height).toFloat())
            }.sortedByDescending { it.width() * it.height() }
            return nonMaximumSuppression(rects)
        } finally { contours.forEach { it.release() }; hierarchy.release() }
    }

    private fun bestBottomRow(candidates: List<RectF>, height: Float): List<RectF>? {
        val rows = mutableListOf<MutableList<RectF>>(); val tolerance = max(18f, height * 0.018f)
        candidates.sortedBy { it.centerY() }.forEach { box ->
            val row = rows.firstOrNull { abs(it.map(RectF::centerY).average().toFloat() - box.centerY()) <= tolerance }
            if (row == null) rows += mutableListOf(box) else row += box
        }
        return rows.filter { it.size >= MIN_RACK_TILES }
            .maxByOrNull { row -> row.size * 100f + row.map(RectF::centerY).average().toFloat() / height }
    }

    private fun findDrawCandidate(mask: Mat, width: Float, height: Float, rack: RectF?): Pair<RectF, Float>? {
        val candidates = rectangles(mask).mapNotNull { r ->
            val rw = r.width() / width; val rh = r.height() / height; val aspect = r.width() / r.height()
            if (rw !in 0.025f..0.15f || rh !in 0.075f..0.34f || aspect !in 0.20f..0.82f) return@mapNotNull null
            if (r.centerY() < height * 0.12f || r.centerY() > height * 0.72f) return@mapNotNull null
            if (rack != null && r.centerY() > rack.top - height * 0.02f) return@mapNotNull null
            val xScore = exp(-abs(r.centerX() / width - 0.63f) * 3.0f)
            val yScore = exp(-abs(r.centerY() / height - 0.34f) * 3.0f)
            val aspectScore = exp(-abs(aspect - 0.36f) * 2.2f)
            val heightScore = exp(-abs(rh - 0.24f) * 4.0f)
            r to (0.42f * xScore + 0.10f * yScore + 0.25f * aspectScore + 0.23f * heightScore)
        }
        return candidates.maxByOrNull { it.second }?.takeIf { it.second >= DRAW_CONFIDENCE_MIN }
    }

    private fun nonMaximumSuppression(rects: List<RectF>): List<RectF> {
        val kept = mutableListOf<RectF>()
        for (candidate in rects) if (kept.none { intersectionOverUnion(candidate, it) > 0.72f }) kept += candidate
        return kept
    }
    private fun intersectionOverUnion(a: RectF, b: RectF): Float {
        val intersection = max(0f, min(a.right,b.right)-max(a.left,b.left)) * max(0f, min(a.bottom,b.bottom)-max(a.top,b.top))
        val union = a.width()*a.height()+b.width()*b.height()-intersection
        return if (union <= 0f) 0f else intersection/union
    }
    private fun union(rects: List<RectF>) = RectF(rects.minOf { it.left }, rects.minOf { it.top }, rects.maxOf { it.right }, rects.maxOf { it.bottom })
    private const val MIN_RACK_TILES = 6
    private const val DRAW_CONFIDENCE_MIN = 0.48f
}
