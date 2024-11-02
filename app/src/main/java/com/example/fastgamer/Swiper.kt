package com.example.fastgamer

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class Swiper: AccessibilityService() {
    override fun onAccessibilityEvent(p0: AccessibilityEvent?) {
        if (p0 != null) {
            // You can add your logic here. For example:
            when (p0.eventType) {
                AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                    // Handle view click events
                }
                AccessibilityEvent.TYPE_VIEW_FOCUSED -> {
                    // Handle view focus events
                }
                // Handle other event types as needed
            }
        }
    }

    override fun onInterrupt() {
        Log.i("FastGamer", "On interrupt")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        (application as App).swiperService = this
        Log.i("FastGamer", "Swiper service connected")
    }

    private val handler = Handler(Looper.getMainLooper())
    private var isSwiping = false

    fun swiping(startX: Float = 350F, to: String) {
        Log.i("FastGamer", "onSwiping")
        var endY = 1200F
        val endX: Float
        if (to == "left") {
            endX =  startX - startX/2
        } else if (to == "right") {
            endX =  startX + startX / 2
        } else if(to == "top") {
            endX = startX
            endY = 800f
        } else return
        performSwipe(startX, 1200F, endX, endY, 100)
        Log.i("FastGamer", "Swiping end")
    }

    fun stopSwiping() {
        isSwiping = false
        handler.removeCallbacksAndMessages(null)
    }
    fun click(x: Float, y: Float) {
        // Create a path for the tap
        val path = Path().apply {
            moveTo(x, y)
        }

        // Create a gesture for a single tap with a short duration (e.g., 50ms)
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 50))
            .build()
        dispatchGesture(gesture, null, null)
    }

        private fun performSwipe(startX: Float, startY: Float, endX: Float, endY: Float, duration: Long) {
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
            .build()

        dispatchGesture(gesture, null, null)
        Log.d("AutoClickService", "Swiped from ($startX, $startY) to ($endX, $endY)")
    }
}