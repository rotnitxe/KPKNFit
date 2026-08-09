package com.example.kpkn.services.diagnostics

import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.ViewConfiguration

/** Android adapter for the JVM-pure two-finger report gesture state machine. */
class ReportGestureDetector(
    private val touchSlop: Int,
    private val onCancelUnderlying: (MotionEvent) -> Unit,
    private val onConfirmed: () -> Unit,
    private val onReleased: () -> Unit,
    private val onProgress: (Float) -> Unit = {},
) {
    private val handler = Handler(Looper.getMainLooper())
    private val machine = ReportGestureStateMachine(movementSlopPx = maxOf(touchSlop * 4f, ReportGestureStateMachine.MOVEMENT_SLOP_PX))
    private var tickSource: MotionEvent? = null
    private var pendingCancelEvent: MotionEvent? = null

    fun onTouchEvent(event: MotionEvent): Boolean {
        val input = when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                ReportGestureInput.Down(
                    event.eventTime,
                    ReportGesturePointer(event.getPointerId(0), event.x, event.y),
                )
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.pointerCount != 2) return false
                ReportGestureInput.PointerDown(
                    event.eventTime,
                    ReportGesturePointer(event.getPointerId(event.actionIndex), event.getX(event.actionIndex), event.getY(event.actionIndex)),
                )
            }
            MotionEvent.ACTION_MOVE -> {
                ReportGestureInput.Move(
                    event.eventTime,
                    (0 until event.pointerCount).map { index ->
                        ReportGesturePointer(event.getPointerId(index), event.getX(index), event.getY(index))
                    },
                )
            }
            MotionEvent.ACTION_POINTER_UP -> ReportGestureInput.PointerUp(event.eventTime, event.getPointerId(event.actionIndex))
            MotionEvent.ACTION_UP -> ReportGestureInput.Up(event.eventTime)
            MotionEvent.ACTION_CANCEL -> ReportGestureInput.Cancel(event.eventTime)
            else -> return machine.isConfirmed
        }
        val effects = machine.onInput(input)
        handleEffects(effects, event)
        when (event.actionMasked) {
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (machine.isArming) {
                    pendingCancelEvent?.recycle()
                    pendingCancelEvent = MotionEvent.obtain(event)
                    scheduleTicks()
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacksAndMessages(null)
            }
        }
        return machine.isConfirmed || effects.any { it is ReportGestureEffect.Released }
    }

    fun cancel() {
        handler.removeCallbacksAndMessages(null)
        handleEffects(machine.cancel(), null)
    }

    private fun scheduleTicks() {
        handler.removeCallbacksAndMessages(null)
        val source = pendingCancelEvent ?: return
        tickSource?.recycle()
        tickSource = MotionEvent.obtain(source)
        handler.post(object : Runnable {
            override fun run() {
                if (!machine.isArming) return
                val now = android.os.SystemClock.uptimeMillis()
                handleEffects(machine.onInput(ReportGestureInput.Tick(now)), tickSource)
                if (machine.isArming) handler.postDelayed(this, TICK_INTERVAL_MS)
            }
        })
    }

    private fun handleEffects(effects: List<ReportGestureEffect>, source: MotionEvent?) {
        effects.forEach { effect ->
            when (effect) {
                is ReportGestureEffect.Progress -> onProgress(effect.value)
                ReportGestureEffect.CancelUnderlying -> {
                    val original = source ?: pendingCancelEvent ?: return@forEach
                    val cancel = MotionEvent.obtain(original)
                    try {
                        cancel.action = MotionEvent.ACTION_CANCEL
                        onCancelUnderlying(cancel)
                    } finally {
                        cancel.recycle()
                    }
                }
                ReportGestureEffect.Confirmed -> onConfirmed()
                ReportGestureEffect.Released -> onReleased()
                ReportGestureEffect.Reset -> {
                    pendingCancelEvent?.recycle()
                    pendingCancelEvent = null
                    tickSource?.recycle()
                    tickSource = null
                    onProgress(0f)
                }
            }
        }
    }

    companion object {
        const val SECOND_POINTER_WINDOW_MS = ReportGestureStateMachine.SECOND_POINTER_WINDOW_MS
        const val HOLD_DURATION_MS = ReportGestureStateMachine.HOLD_DURATION_MS
        private const val TICK_INTERVAL_MS = 50L

        fun from(
            view: android.view.View,
            onCancel: (MotionEvent) -> Unit,
            onConfirmed: () -> Unit,
            onReleased: () -> Unit,
            onProgress: (Float) -> Unit = {},
        ): ReportGestureDetector = ReportGestureDetector(
            touchSlop = ViewConfiguration.get(view.context).scaledTouchSlop,
            onCancelUnderlying = onCancel,
            onConfirmed = onConfirmed,
            onReleased = onReleased,
            onProgress = onProgress,
        )
    }
}
