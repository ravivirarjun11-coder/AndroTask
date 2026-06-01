package com.borgorninja.androtask

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Path
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.util.DisplayMetrics
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.borgorninja.androtask.data.MacroStep
import com.borgorninja.androtask.data.StepType
import kotlinx.coroutines.*
import kotlin.coroutines.resume

class MacroAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile var instance: MacroAccessibilityService? = null
            private set
        @Volatile var isRecording: Boolean = false
        val recordedSteps = mutableListOf<MacroStep>()
        @Volatile var isPlaying: Boolean = false
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onServiceConnected() {
        instance = this
        AppLogger.i("A11y", "Accessibility service connected")
    }
    override fun onInterrupt() { AppLogger.w("A11y", "Service interrupted") }
    override fun onDestroy() {
        instance = null; releaseWakeLock(); scope.cancel()
        AppLogger.i("A11y", "Service destroyed")
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) { /* passive recording unused */ }

    // ── Playback ──────────────────────────────────────────────────────────────
    fun playSteps(
        steps: List<MacroStep>,
        loopCount: Int,
        loopDelay: Long,
        speedMultiplier: Float = 1f,
        recordedWidth: Int    = 0,
        recordedHeight: Int   = 0,
        startFromIndex: Int   = 0
    ) {
        if (steps.isEmpty()) {
            AppLogger.w("Play", "playSteps called with empty list — nothing to do")
            return
        }
        scope.launch {
            acquireWakeLock()
            isPlaying = true
            AppLogger.i("Play", "Playback started — ${steps.size} steps, loops=$loopCount, speed=${speedMultiplier}x")
            try {
                val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
                val (sw, sh) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val b = wm.currentWindowMetrics.bounds
                    Pair(b.width().toFloat(), b.height().toFloat())
                } else {
                    @Suppress("DEPRECATION")
                    val dm = DisplayMetrics().also { wm.defaultDisplay.getRealMetrics(it) }
                    Pair(dm.widthPixels.toFloat(), dm.heightPixels.toFloat())
                }
                val scaleX = if (recordedWidth  > 0) sw / recordedWidth  else 1f
                val scaleY = if (recordedHeight > 0) sh / recordedHeight else 1f
                AppLogger.d("Play", "Screen ${sw.toInt()}x${sh.toInt()}  recorded ${recordedWidth}x${recordedHeight}  scale=(${scaleX},${scaleY})")

                val ordered = steps.sortedBy { it.stepIndex }.drop(startFromIndex)
                val loops   = if (loopCount <= 0) 1 else loopCount

                repeat(loops) { iter ->
                    AppLogger.d("Play", "Loop ${iter+1}/$loops")
                    for ((i, step) in ordered.withIndex()) {
                        if (!isPlaying) {
                            AppLogger.i("Play", "Playback stopped at step ${i+1}")
                            return@launch
                        }
                        val delayMs = step.delayBefore.coerceAtLeast(0L)
                        if (delayMs > 0) { AppLogger.d("Play", "Delay ${delayMs}ms"); delay(delayMs) }
                        dispatchStep(step, scaleX, scaleY, speedMultiplier)
                    }
                    if (iter < loops - 1 && loopDelay > 0) delay(loopDelay)
                }
                AppLogger.i("Play", "Playback finished normally")
            } catch (e: Exception) {
                AppLogger.e("Play", "Playback exception: ${e.javaClass.simpleName}: ${e.message}")
            } finally {
                isPlaying = false; releaseWakeLock()
            }
        }
    }

    fun stopPlayback() { isPlaying = false; AppLogger.i("Play", "stopPlayback() called") }
    fun replayStep(step: MacroStep) { scope.launch { dispatchStep(step, 1f, 1f, 1f) } }

    // ── Gesture dispatch ──────────────────────────────────────────────────────
    private suspend fun dispatchStep(step: MacroStep, sx: Float, sy: Float, sp: Float) {
        val dur   = (step.duration / sp).toLong().coerceAtLeast(50L)
        val x     = step.x  * sx; val y  = step.y  * sy
        val x2    = step.x2 * sx; val y2 = step.y2 * sy

        AppLogger.d("Dispatch", "${step.type}  (${x.toInt()},${y.toInt()})" +
            (if (step.type==StepType.SWIPE||step.type==StepType.PINCH) "→(${x2.toInt()},${y2.toInt()})" else "") +
            "  dur=${dur}ms  scale=(${sx},${sy})")

        when (step.type) {
            StepType.WAIT -> { AppLogger.d("Dispatch","Wait ${dur}ms"); delay(dur) }

            StepType.TAP, StepType.LONG_PRESS -> {
                val realDur = if (step.type == StepType.LONG_PRESS) maxOf(dur, 600L) else dur
                val path = Path().apply { moveTo(x, y) }
                val result = dispatchGestureAwait(
                    GestureDescription.Builder()
                        .addStroke(GestureDescription.StrokeDescription(path, 0, realDur)).build()
                )
                AppLogger.d("Dispatch", "→ ${step.type} result=$result")
                delay(80)
            }

            StepType.SWIPE, StepType.PINCH,
            StepType.SCROLL_UP, StepType.SCROLL_DOWN -> {
                val path = Path().apply { moveTo(x, y); lineTo(x2, y2) }
                val result = dispatchGestureAwait(
                    GestureDescription.Builder()
                        .addStroke(GestureDescription.StrokeDescription(path, 0, dur)).build()
                )
                AppLogger.d("Dispatch", "→ SWIPE result=$result")
                delay(50)
            }

            StepType.BACK -> {
                val ok = performGlobalAction(GLOBAL_ACTION_BACK)
                AppLogger.d("Dispatch","BACK ok=$ok"); delay(200)
            }
            StepType.HOME -> {
                val ok = performGlobalAction(GLOBAL_ACTION_HOME)
                AppLogger.d("Dispatch","HOME ok=$ok"); delay(300)
            }
            StepType.RECENTS -> {
                val ok = performGlobalAction(GLOBAL_ACTION_RECENTS)
                AppLogger.d("Dispatch","RECENTS ok=$ok"); delay(300)
            }
            StepType.NOTIFICATIONS -> {
                val ok = performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
                AppLogger.d("Dispatch","NOTIFICATIONS ok=$ok"); delay(300)
            }

            StepType.VOLUME_UP, StepType.VOLUME_DOWN -> {
                val am  = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                val dir = if (step.type==StepType.VOLUME_UP) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
                am.adjustVolume(dir, AudioManager.FLAG_SHOW_UI)
                AppLogger.d("Dispatch","VOLUME ${step.type}"); delay(100)
            }

            StepType.TYPE_TEXT -> {
                val node = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
                if (node != null) {
                    val args = Bundle().apply {
                        putString(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, step.text)
                    }
                    val ok = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                    node.recycle()
                    AppLogger.d("Dispatch","TYPE_TEXT ok=$ok text=\"${step.text.take(30)}\"")
                } else {
                    AppLogger.w("Dispatch","TYPE_TEXT — no focused input node found")
                }
                delay(150)
            }
        }
    }

    /**
     * Dispatches a gesture and waits for the result callback.
     * Returns a string describing the outcome for logging.
     * Timeout of 3s prevents hanging on OEM devices where the callback never fires.
     */
    private suspend fun dispatchGestureAwait(gesture: GestureDescription): String {
        val result = withTimeoutOrNull(3_000L) {
            suspendCancellableCoroutine { cont ->
                val dispatched = dispatchGesture(gesture, object : GestureResultCallback() {
                    override fun onCompleted(g: GestureDescription?) {
                        if (cont.isActive) cont.resume("completed")
                    }
                    override fun onCancelled(g: GestureDescription?) {
                        if (cont.isActive) cont.resume("cancelled")
                    }
                }, null)
                if (!dispatched && cont.isActive) {
                    AppLogger.w("Dispatch", "dispatchGesture() returned false — service may not be ready")
                    cont.resume("dispatch_returned_false")
                }
            }
        } ?: "timeout_3s"
        if (result != "completed")
            AppLogger.w("Dispatch", "Gesture not completed: $result")
        return result
    }

    private fun acquireWakeLock() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AndroTask::Playback")
                .apply { acquire(30 * 60 * 1000L) }
            AppLogger.d("Play", "Wake lock acquired")
        } catch (e: Exception) {
            AppLogger.w("Play", "Wake lock failed: ${e.message}")
        }
    }

    private fun releaseWakeLock() {
        try { wakeLock?.takeIf { it.isHeld }?.release(); AppLogger.d("Play","Wake lock released") }
        catch (e: Exception) { AppLogger.w("Play","Wake lock release failed: ${e.message}") }
        wakeLock = null
    }
}
