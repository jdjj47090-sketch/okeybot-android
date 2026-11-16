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

    override fun onStart() { super.onStart(); ScreenVision.activityStarted() }
    override fun onStop() { ScreenVision.activityStopped(); super.onStop() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = CalibrationStore(this)
        val ar = arabicUi()
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; UiKit.rtl(this, ar); setPadding(UiKit.dp(this@CalibrationActivity, 18), UiKit.dp(this@CalibrationActivity, 16), UiKit.dp(this@CalibrationActivity, 18), UiKit.dp(this@CalibrationActivity, 14)); setBackgroundColor(UiKit.BG) }
        root.addView(UiKit.text(this, if (ar) "معايرة الطاولة" else "Table calibration", 25f, UiKit.INK, true))
        root.addView(UiKit.text(this, if (ar) "عاين الاقتراحات على لقطة اللعبة، ثم احفظها أو حدّد المناطق يدويًا." else "Review the suggestions over your game frame, then save them or mark regions manually.", 14f, UiKit.MUTED).apply { setPadding(0, UiKit.dp(this@CalibrationActivity, 4), 0, UiKit.dp(this@CalibrationActivity, 10)) })
        canvasView = Preview()
        val previewFrame = LinearLayout(this).apply { background = UiKit.panel(this@CalibrationActivity, 0xFF202A26.toInt(), 20); setPadding(UiKit.dp(this@CalibrationActivity, 3), UiKit.dp(this@CalibrationActivity, 3), UiKit.dp(this@CalibrationActivity, 3), UiKit.dp(this@CalibrationActivity, 3)) }
        previewFrame.addView(canvasView, LinearLayout.LayoutParams(-1, -1))
        root.addView(previewFrame, LinearLayout.LayoutParams(-1, 0, 1f).apply { bottomMargin = UiKit.dp(this@CalibrationActivity, 12) })
        val autoRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; UiKit.rtl(this, ar) }
        autoRow.addView(UiKit.action(this, if (ar) "حفظ الرف" else "Save rack", true) { saveAuto(Region.RACK) }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = UiKit.dp(this@CalibrationActivity, 6) })
        autoRow.addView(UiKit.action(this, if (ar) "حفظ السحب" else "Save draw", false) { saveAuto(Region.DRAW_PILE) }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = UiKit.dp(this@CalibrationActivity, 6) })
        root.addView(autoRow, UiKit.margin(this, 8))
        root.addView(UiKit.action(this, if (ar) "مسح المعايرة" else "Reset calibration", false) { getSharedPreferences("calibration", 0).edit().clear().apply(); rackFirst = null; canvasView.invalidate() }, UiKit.margin(this, 2))
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
                canvas.drawText(if (arabicUi()) "لا توجد لقطة بعد — افتح اللعبة وشغّل الالتقاط" else "No game frame yet — start capture with the game open", 16f, 32f, paint)
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
            val label = missing?.let { if (!arabicUi()) it.name else when (it) { Region.RACK -> "الرف"; Region.DRAW_PILE -> "السحب"; Region.DISCARD -> "الرمي"; Region.INDICATOR -> "المؤشر" } } ?: if (arabicUi()) "مكتملة" else "complete"
            canvas.drawText(if (arabicUi()) "التالي: $label · بلاطات ${ScreenVision.latestLayout.rackTiles.size} · ثقة السحب ${"%.2f".format(ScreenVision.latestLayout.drawConfidence)}" else "Next: $label | rack ${ScreenVision.latestLayout.rackTiles.size} | draw confidence ${"%.2f".format(ScreenVision.latestLayout.drawConfidence)}", 12f, height - 18f, paint)
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
