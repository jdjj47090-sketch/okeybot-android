package com.okeybot.control

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import kotlin.random.Random

/** Dispatches gestures only when an explicitly supplied action is requested by the loop. */
class GestureService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit
    fun tap(x: Float, y: Float, done: (() -> Unit)? = null) = afterDelay { dispatch(Path().apply { moveTo(x, y) }, 90, done) }
    fun drag(fromX: Float, fromY: Float, toX: Float, toY: Float, done: (() -> Unit)? = null) = afterDelay {
        val path = Path().apply { moveTo(fromX, fromY); quadTo((fromX + toX) / 2f + Random.nextFloat() * 8f - 4f, (fromY + toY) / 2f + Random.nextFloat() * 8f - 4f, toX, toY) }
        dispatch(path, Random.nextLong(250, 440), done)
    }
    private fun afterDelay(block: () -> Unit) { Handler(Looper.getMainLooper()).postDelayed(block, Random.nextLong(150, 601)) }
    private fun dispatch(path: Path, duration: Long, done: (() -> Unit)?) {
        val gesture = GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, duration)).build()
        dispatchGesture(gesture, object : GestureResultCallback() { override fun onCompleted(gestureDescription: GestureDescription?) { done?.invoke() } }, null)
    }
}
