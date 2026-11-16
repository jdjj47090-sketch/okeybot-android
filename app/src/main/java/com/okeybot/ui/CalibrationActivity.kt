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

class CalibrationActivity : Activity() {
    private lateinit var store: CalibrationStore
    private lateinit var canvasView: Preview
    private val sequence=Region.values().toList(); private var regionIndex=0; private var rackFirst: PointF?=null
    override fun onCreate(savedInstanceState:Bundle?) { super.onCreate(savedInstanceState); store=CalibrationStore(this)
        val root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        root.addView(TextView(this).apply { text="Tap rack left and right edges, then draw pile, discard pile and indicator centers. Saved as screen fractions."; textSize=16f; setPadding(16,12,16,12) })
        canvasView=Preview(); root.addView(canvasView,LinearLayout.LayoutParams(-1,0,1f))
        root.addView(Button(this).apply { text="Reset calibration"; setOnClickListener { getSharedPreferences("calibration",0).edit().clear().apply(); regionIndex=0; rackFirst=null; canvasView.invalidate() } })
        setContentView(root)
    }
    private inner class Preview:View(this@CalibrationActivity) {
        private val paint=Paint(3)
        override fun onDraw(c:Canvas) { super.onDraw(c); c.drawColor(Color.rgb(30,35,34)); ScreenVision.latestFrame?.let { b -> val scale=minOf(width.toFloat()/b.width,height.toFloat()/b.height); val w=b.width*scale; val h=b.height*scale; c.drawBitmap(b,null,RectF((width-w)/2,(height-h)/2,(width+w)/2,(height+h)/2),paint) }; val colors=listOf(Color.CYAN,Color.GREEN,Color.YELLOW,Color.MAGENTA); store.all().values.forEachIndexed { i,r -> paint.color=colors[i%colors.size]; paint.style=Paint.Style.STROKE; c.drawRect(r.left*width,r.top*height,r.right*width,r.bottom*height,paint) }; paint.style=Paint.Style.FILL; paint.color=Color.WHITE; c.drawText("Mark: ${sequence.getOrNull(regionIndex) ?: "complete"}",16f,28f,paint) }
        override fun onTouchEvent(e:MotionEvent):Boolean { if(e.action!=MotionEvent.ACTION_UP || regionIndex>=sequence.size) return true; val x=(e.x/width).coerceIn(0f,1f); val y=(e.y/height).coerceIn(0f,1f); val region=sequence[regionIndex]
            val rect=if(region==Region.RACK) { if(rackFirst==null) { rackFirst=PointF(x,y); invalidate(); return true }; val a=rackFirst!!; rackFirst=null; RectF(minOf(x,a.x),minOf(y,a.y),maxOf(x,a.x),maxOf(y,a.y)) } else RectF((x-.04f).coerceAtLeast(0f),(y-.08f).coerceAtLeast(0f),(x+.04f).coerceAtMost(1f),(y+.08f).coerceAtMost(1f))
            store.put(region,rect); regionIndex++; invalidate(); return true }
    }
}
