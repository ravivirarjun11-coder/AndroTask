package com.borgorninja.androtask

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Path
import android.graphics.Rect
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

    override fun onServiceConnected() { instance = this }
    override fun onInterrupt() {}

    override fun onDestroy() {
        instance = null
        releaseWakeLock()
        scope.cancel()
        super.onDestroy()
    }

    // ── Passive tap recording (fallback — overlay mode preferred) ─────────────
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!isRecording) return
        val ev  = event ?: return
        val src = ev.source ?: return
        if (ev.eventType != AccessibilityEvent.TYPE_VIEW_CLICKED) { src.recycle(); return }
        val bounds = Rect(); src.getBoundsInScreen(bounds)
        src.recycle()
        if (bounds.isEmpty) return
        val step = MacroStep(
            macroId   = 0,
            stepIndex = recordedSteps.size,
            type      = StepType.TAP,
            x         = bounds.exactCenterX(),
            y         = bounds.exactCenterY(),
            duration  = 100L
        )
        recordedSteps.add(step)
    }

    // ── Playback ──────────────────────────────────────────────────────────────
    fun playSteps(
        steps: List<MacroStep>,
        loopCount: Int,
        loopDelay: Long,
        speedMultiplier: Float = 1.0f,
        recordedWidth: Int    = 0,
        recordedHeight: Int   = 0,
        startFromIndex: Int   = 0
    ) {
        if (steps.isEmpty()) return
        scope.launch {
            // Fix: PARTIAL_WAKE_LOCK is not deprecated and works correctly
            acquireWakeLock()
            isPlaying = true
            try {
                val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
                val (screenW, screenH) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val b = wm.currentWindowMetrics.bounds
                    Pair(b.width().toFloat(), b.height().toFloat())
                } else {
                    @Suppress("DEPRECATION")
                    val dm = DisplayMetrics().also { wm.defaultDisplay.getRealMetrics(it) }
                    Pair(dm.widthPixels.toFloat(), dm.heightPixels.toFloat())
                }

                // If recorded on same device/resolution, scale = 1.0 (no change)
                val scaleX = if (recordedWidth  > 0) screenW / recordedWidth  else 1f
                val scaleY = if (recordedHeight > 0) screenH / recordedHeight else 1f
                val sp     = speedMultiplier.coerceIn(0.1f, 10f)
                val ordered = steps.sortedBy { it.stepIndex }.drop(startFromIndex)
                val loops   = if (loopCount <= 0) 1 else loopCount  // default 1 loop if not set

                repeat(loops) { iter ->
                    for (step in ordered) {
                        if (!isPlaying) return@launch
                        val delayMs = (step.delayBefore.coerceAtLeast(0L) / sp).toLong()
                        if (delayMs > 0) delay(delayMs)
                        dispatchStep(step, scaleX, scaleY, sp)
                    }
                    if (iter < loops - 1 && loopDelay > 0) delay((loopDelay / sp).toLong())
                }
            } finally {
                isPlaying = false
                releaseWakeLock()
            }
        }
    }

    fun stopPlayback() { isPlaying = false }

    fun replayStep(step: MacroStep) { scope.launch { dispatchStep(step, 1f, 1f, 1f) } }

    // ── Gesture dispatch ──────────────────────────────────────────────────────
    private suspend fun dispatchStep(step: MacroStep, sx: Float, sy: Float, sp: Float) {
        val dur = (step.duration / sp).toLong().coerceAtLeast(50L)

        when (step.type) {
            StepType.WAIT -> delay(dur)

            StepType.TAP, StepType.LONG_PRESS -> {
                val realDur = if (step.type == StepType.LONG_PRESS) maxOf(dur, 600L) else dur
                val path = Path().apply { moveTo(step.x * sx, step.y * sy) }
                dispatchGestureAwait(GestureDescription.Builder()
                    .addStroke(GestureDescription.StrokeDescription(path, 0, realDur)).build())
                // Small buffer so the target app registers the touch
                delay(80)
            }

            StepType.SWIPE -> {
                val path = Path().apply {
                    moveTo(step.x * sx, step.y * sy)
                    lineTo(step.x2 * sx, step.y2 * sy)
                }
                dispatchGestureAwait(GestureDescription.Builder()
                    .addStroke(GestureDescription.StrokeDescription(path, 0, dur)).build())
                delay(50)
            }

            StepType.BACK          -> { performGlobalAction(GLOBAL_ACTION_BACK);          delay(200) }
            StepType.HOME          -> { performGlobalAction(GLOBAL_ACTION_HOME);          delay(300) }
            StepType.RECENTS       -> { performGlobalAction(GLOBAL_ACTION_RECENTS);       delay(300) }
            StepType.NOTIFICATIONS -> { performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS); delay(300) }

            StepType.VOLUME_UP, StepType.VOLUME_DOWN -> {
                val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                am.adjustVolume(
                    if (step.type == StepType.VOLUME_UP) AudioManager.ADJUST_RAISE
                    else AudioManager.ADJUST_LOWER,
                    AudioManager.FLAG_SHOW_UI
                )
                delay(100)
            }

            StepType.TYPE_TEXT -> {
                val node = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
                if (node != null) {
                    val args = Bundle().apply {
                        putString(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, step.text)
                    }
                    node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                    node.recycle()
                }
                delay(150)
            }

            StepType.PINCH, StepType.SCROLL_UP, StepType.SCROLL_DOWN -> {
                // Treat as swipe for simplicity on budget devices
                val path = Path().apply {
                    moveTo(step.x * sx, step.y * sy)
                    lineTo(step.x2 * sx, step.y2 * sy)
                }
                dispatchGestureAwait(GestureDescription.Builder()
                    .addStroke(GestureDescription.StrokeDescription(path, 0, dur)).build())
                delay(50)
            }
        }
    }

    /**
     * Fix: added timeout so if the callback never fires on OEM devices
     * (Infinix/Tecno XOS, Xiaomi MIUI, etc.) the coroutine doesn't hang forever.
     * Also handles dispatchGesture() returning false immediately.
     */
    private suspend fun dispatchGestureAwait(gesture: GestureDescription) {
        withTimeoutOrNull(3_000L) {
            suspendCancellableCoroutine { cont ->
                val ok = dispatchGesture(gesture, object : GestureResultCallback() {
                    override fun onCompleted(g: GestureDescription?) {
                        if (cont.isActive) cont.resume(Unit)
                    }
                    override fun onCancelled(g: GestureDescription?) {
                        if (cont.isActive) cont.resume(Unit)
                    }
                }, null)
                // dispatchGesture returned false → service not ready; resume immediately
                if (!ok && cont.isActive) cont.resume(Unit)
            }
        }
        // If timeout hit (null returned), just continue; don't block the sequence
    }

    private fun acquireWakeLock() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            // Fix: PARTIAL_WAKE_LOCK — keeps CPU running without turning on screen
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AndroTask::Playback")
                .apply { acquire(30 * 60 * 1000L) }
        } catch (_: Exception) { /* non-fatal, carry on without wake lock */ }
    }

    private fun releaseWakeLock() {
        try { wakeLock?.takeIf { it.isHeld }?.release() } catch (_: Exception) {}
        wakeLock = null
    }
}
