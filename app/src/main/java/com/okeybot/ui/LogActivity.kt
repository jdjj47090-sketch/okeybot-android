package com.okeybot.ui

import android.app.Activity
import android.os.Bundle
import android.widget.ScrollView
import android.widget.TextView
import com.okeybot.vision.ScreenVision

class LogActivity:Activity() {
    override fun onStart() { super.onStart(); ScreenVision.activityStarted() }
    override fun onStop() { ScreenVision.activityStopped(); super.onStop() }

    override fun onCreate(savedInstanceState:Bundle?) { super.onCreate(savedInstanceState); val log=TextView(this).apply { textSize=15f; setPadding(20,20,20,20) }; setContentView(ScrollView(this).apply { addView(log) }); fun update() { log.text="OkeyBot analysis log\n\nState: ${ScreenVision.latest.state}\n${ScreenVision.latest.reason}\nRack tiles detected: ${ScreenVision.latestLayout.rackTiles.size}\nDraw candidate: ${ScreenVision.latestLayout.drawPile?.let { "%.2f confidence".format(ScreenVision.latestLayout.drawConfidence) } ?: "not found"}\n\nNo move is issued until the screen is recognized and all regions are calibrated." }; update(); ScreenVision.listener={runOnUiThread { update() }} }
}
