package com.example.kpkn.services.diagnostics

import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.ViewConfiguration

/** Two-finger, six-second report gesture state machine. */
class ReportGestureDetector(
    private val touchSlop: Int,
    private val onCancelUnderlying: (MotionEvent) -> Unit,
    private val onConfirmed: () -> Unit,
    private val onReleased: () -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())
    private var firstPointerId = MotionEvent.INVALID_POINTER_ID
    private var secondPointerId = MotionEvent.INVALID_POINTER_ID
    private var firstDownAt = 0L
    private var firstX = 0f
    private var firstY = 0f
    private var secondX = 0f
    private var secondY = 0f
    private var arming = false
    private var confirmed = false
    private var released = false
    private var pendingCancelEvent: MotionEvent? = null

    fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                reset()
                firstPointerId = event.getPointerId(0)
                firstDownAt = event.eventTime
                firstX = event.x
                firstY = event.y
                return false
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.pointerCount != 2 || firstPointerId == MotionEvent.INVALID_POINTER_ID) return false
                val elapsed = event.eventTime - firstDownAt
                if (elapsed !in 0..SECOND_POINTER_WINDOW_MS) {
                    reset()
                    return false
                }
                secondPointerId = event.getPointerId(event.actionIndex)
                secondX = event.getX(event.actionIndex)
                secondY = event.getY(event.actionIndex)
                pendingCancelEvent = MotionEvent.obtain(event)
                arming = true
                released = false
                handler.postDelayed({
                    if (arming && !released) {
                        confirmed = true
                        pendingCancelEvent?.let { source ->
                            val cancel = MotionEvent.obtain(source)
                            try {
                                cancel.action = MotionEvent.ACTION_CANCEL
                                onCancelUnderlying(cancel)
                            } finally {
                                cancel.recycle()
                                source.recycle()
                                pendingCancelEvent = null
                            }
                        }
                        onConfirmed()
                    }
                }, HOLD_DURATION_MS)
                // The candidate must not interfere with the underlying control
                // until the six-second hold is actually confirmed.
                return false
            }

            MotionEvent.ACTION_MOVE -> {
                if (!arming) return false
                val firstIndex = event.findPointerIndex(firstPointerId)
                val secondIndex = event.findPointerIndex(secondPointerId)
                val firstMoved = firstIndex >= 0 &&
                    distance(event.getX(firstIndex), event.getY(firstIndex), firstX, firstY) > touchSlop
                val secondMoved = secondIndex >= 0 &&
                    distance(event.getX(secondIndex), event.getY(secondIndex), secondX, secondY) > touchSlop
                if ((firstMoved || secondMoved) && !confirmed) {
                    reset()
                    return false
                }
                if (!confirmed) return false
                return true
            }

            MotionEvent.ACTION_POINTER_UP -> {
                if (!arming || !confirmed) {
                    if (arming) reset()
                    return false
                }
                // After confirmation the first lifted finger is not the end of
                // the gesture; wait for the final ACTION_UP.
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!arming || !confirmed) {
                    if (arming) reset()
                    return false
                }
                released = true
                handler.removeCallbacksAndMessages(null)
                val shouldOpen = confirmed && event.actionMasked != MotionEvent.ACTION_CANCEL
                reset()
                if (shouldOpen) onReleased()
                return true
            }
        }
        return confirmed
    }

    fun cancel() {
        reset()
    }

    private fun reset() {
        handler.removeCallbacksAndMessages(null)
        pendingCancelEvent?.recycle()
        pendingCancelEvent = null
        firstPointerId = MotionEvent.INVALID_POINTER_ID
        secondPointerId = MotionEvent.INVALID_POINTER_ID
        firstDownAt = 0L
        firstX = 0f
        firstY = 0f
        secondX = 0f
        secondY = 0f
        arming = false
        confirmed = false
        released = false
    }

    private fun distance(x: Float, y: Float, originX: Float, originY: Float): Float {
        val dx = x - originX
        val dy = y - originY
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }

    companion object {
        const val SECOND_POINTER_WINDOW_MS = 400L
        const val HOLD_DURATION_MS = 6_000L

        fun from(view: android.view.View, onCancel: (MotionEvent) -> Unit, onConfirmed: () -> Unit, onReleased: () -> Unit): ReportGestureDetector =
            ReportGestureDetector(ViewConfiguration.get(view.context).scaledTouchSlop, onCancel, onConfirmed, onReleased)
    }
}
