package com.okeybot.ui

import android.app.Activity
import android.graphics.*
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.*
import com.okeybot.calibration.CalibrationStore
import com.okeybot.calibration.Region
import com.okeybot.vision.ScreenVision

/** Shows auto-detected rack/draw proposals over the last captured game frame, with manual fallback. */
class CalibrationActivity : Activity() {
    private lateinit var store: CalibrationStore
    private lateinit var canvasView: Preview
    private val sequence = Region.values().toList()
    private var rackFirst: PointF? = null

    override fun onResume() { super.onResume(); ScreenVision.appUiVisible = true }
    override fun onPause() { ScreenVision.appUiVisible = false; super.onPause() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = CalibrationStore(this)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(TextView(this).apply {
            text = "Keep the game open while capture runs, then return here. Green boxes are detected rack tiles; cyan is a suggested draw area. Review before saving. Tap to set remaining regions manually."
            textSize = 15f; setPadding(16, 12, 16, 12)
        })
        canvasView = Preview()
        root.addView(canvasView, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(Button(this@CalibrationActivity).apply { text = "Use detected rack"; setOnClickListener { saveAuto(Region.RACK) } }, LinearLayout.LayoutParams(0, -2, 1f))
            addView(Button(this@CalibrationActivity).apply { text = "Use draw suggestion"; setOnClickListener { saveAuto(Region.DRAW_PILE) } }, LinearLayout.LayoutParams(0, -2, 1f))
        })
        root.addView(Button(this).apply {
            text = "Reset calibration"
            setOnClickListener { getSharedPreferences("calibration", 0).edit().clear().apply(); rackFirst = null; canvasView.invalidate() }
        })
        setContentView(root)
    }

    private fun saveAuto(region: Region) {
        val frame = ScreenVision.latestFrame
        val rect = when (region) {
            Region.RACK -> ScreenVision.latestLayout.rackBounds
            Region.DRAW_PILE -> ScreenVision.latestLayout.drawPile
            else -> null
        }
        if (frame == null || rect == null) {
            Toast.makeText(this, "لم يُعثر على ${if (region == Region.RACK) "الرف" else "مكان السحب"}؛ استخدم التحديد اليدوي", Toast.LENGTH_LONG).show()
            return
        }
        store.put(region, normalized(rect, frame.width, frame.height))
        canvasView.invalidate()
        Toast.makeText(this, "تم حفظ اقتراح ${if (region == Region.RACK) "الرف" else "السحب"}; راجع الإطار", Toast.LENGTH_SHORT).show()
    }

    private fun normalized(rect: RectF, width: Int, height: Int) = RectF(
        (rect.left / width).coerceIn(0f, 1f), (rect.top / height).coerceIn(0f, 1f),
        (rect.right / width).coerceIn(0f, 1f), (rect.bottom / height).coerceIn(0f, 1f)
    )

    private inner class Preview : View(this@CalibrationActivity) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var imageRect = RectF()
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            canvas.drawColor(Color.rgb(30, 35, 34))
            val frame = ScreenVision.latestFrame
            if (frame == null) {
                paint.color = Color.WHITE; paint.style = Paint.Style.FILL
                canvas.drawText("No game frame yet — show the game with capture running first", 16f, 32f, paint)
                return
            }
            val scale = minOf(width.toFloat() / frame.width, height.toFloat() / frame.height)
            val imageW = frame.width * scale; val imageH = frame.height * scale
            imageRect.set((width - imageW) / 2f, (height - imageH) / 2f, (width + imageW) / 2f, (height + imageH) / 2f)
            canvas.drawBitmap(frame, null, imageRect, paint)
            val sx = imageRect.width() / frame.width; val sy = imageRect.height() / frame.height
            fun mapped(r: RectF) = RectF(imageRect.left + r.left * sx, imageRect.top + r.top * sy, imageRect.left + r.right * sx, imageRect.top + r.bottom * sy)
            paint.style = Paint.Style.STROKE; paint.strokeWidth = 3f
            ScreenVision.latestLayout.rackTiles.forEach { paint.color = Color.GREEN; canvas.drawRect(mapped(it), paint) }
            ScreenVision.latestLayout.rackBounds?.let { paint.color = Color.YELLOW; canvas.drawRect(mapped(it), paint) }
            ScreenVision.latestLayout.drawPile?.let { paint.color = Color.CYAN; canvas.drawRect(mapped(it), paint) }
            val palette = mapOf(Region.RACK to Color.YELLOW, Region.DRAW_PILE to Color.CYAN, Region.DISCARD to Color.MAGENTA, Region.INDICATOR to Color.RED)
            store.all().forEach { (region, r) ->
                paint.color = palette.getValue(region)
                canvas.drawRect(mapped(RectF(r.left * frame.width, r.top * frame.height, r.right * frame.width, r.bottom * frame.height)), paint)
            }
            paint.style = Paint.Style.FILL; paint.color = Color.WHITE
            val missing = sequence.firstOrNull { store.get(it) == null }
            val label = missing?.name ?: "complete"
            canvas.drawText("Next: $label | rack ${ScreenVision.latestLayout.rackTiles.size} | draw confidence ${"%.2f".format(ScreenVision.latestLayout.drawConfidence)}", 12f, height - 18f, paint)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (event.action != MotionEvent.ACTION_UP) return true
            val region = sequence.firstOrNull { store.get(it) == null } ?: return true
            val frame = ScreenVision.latestFrame ?: return true
            val x = ((event.x - imageRect.left) / imageRect.width()).coerceIn(0f, 1f)
            val y = ((event.y - imageRect.top) / imageRect.height()).coerceIn(0f, 1f)
            val point = PointF(x, y)
            if (region == Region.RACK) {
                if (rackFirst == null) { rackFirst = point; invalidate(); return true }
                val first = rackFirst!!; rackFirst = null
                store.put(region, RectF(minOf(first.x, x), minOf(first.y, y), maxOf(first.x, x), maxOf(first.y, y)))
            } else {
                val halfW = 0.04f; val halfH = 0.06f
                store.put(region, RectF((x-halfW).coerceAtLeast(0f), (y-halfH).coerceAtLeast(0f), (x+halfW).coerceAtMost(1f), (y+halfH).coerceAtMost(1f)))
            }
            invalidate()
            return true
        }
    }
}
