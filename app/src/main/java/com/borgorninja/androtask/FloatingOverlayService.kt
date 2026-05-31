package com.borgorninja.androtask

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.*
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.*
import androidx.core.app.NotificationCompat
import com.borgorninja.androtask.data.MacroStep
import com.borgorninja.androtask.data.StepType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.math.hypot

class FloatingOverlayService : Service() {

    private lateinit var wm: WindowManager
    private var bubbleView:      View? = null
    private var overlayView:     View? = null
    private var coordPickerView: View? = null
    private var isRecording = false

    // Raw down coords for gesture delta calc
    private var downRawX = 0f; private var downRawY = 0f; private var downTime = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        // Fix: use mediaPlayback type (works Android 10+, no extra permission needed)
        val notif = buildNotif()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIF_ID, notif)
        }
        if (Settings.canDrawOverlays(this)) showBubble()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.getStringExtra(CMD)) {
            CMD_START       -> startRecording()
            CMD_STOP        -> stopRecordingAndHide()
            CMD_TOGGLE      -> if (isRecording) stopRecordingAndHide() else startRecording()
            CMD_DISMISS     -> { stopRecordingAndHide(); stopSelf() }
            CMD_PICK_START  -> showCoordPicker(intent.getStringExtra(EXTRA_PICK_FIELD) ?: FIELD_XY)
            CMD_PICK_CANCEL -> hideCoordPicker()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        MacroAccessibilityService.isRecording = false
        removeBubble(); removeOverlay(); hideCoordPicker()
        super.onDestroy()
    }

    // ── Notification ──────────────────────────────────────────────────────────
    private fun buildNotif(): Notification {
        val togglePi = PendingIntent.getService(this, 0,
            Intent(this, FloatingOverlayService::class.java).putExtra(CMD, CMD_TOGGLE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        // "Dismiss bubble" action — always visible
        val dismissPi = PendingIntent.getService(this, 1,
            Intent(this, FloatingOverlayService::class.java).putExtra(CMD, CMD_DISMISS),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val builder = NotificationCompat.Builder(this, MainActivity.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(if (isRecording) "Recording…" else "AndroTask ready")
            .setContentText(if (isRecording)
                "Tap the bubble to stop  •  ${MacroAccessibilityService.recordedSteps.size} steps"
                else "Tap the bubble to start recording")
            .setContentIntent(togglePi)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_delete, "Dismiss bubble", dismissPi)

        if (isRecording) {
            val stopPi = PendingIntent.getService(this, 2,
                Intent(this, FloatingOverlayService::class.java).putExtra(CMD, CMD_STOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            builder.addAction(android.R.drawable.ic_media_pause, "Stop recording", stopPi)
        }
        return builder.build()
    }

    private fun refreshNotif() =
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(NOTIF_ID, buildNotif())

    // ── Bubble ────────────────────────────────────────────────────────────────
    private fun showBubble() {
        if (bubbleView != null) return
        val sizePx = (56 * resources.displayMetrics.density).toInt()
        val lp = baseLp(sizePx, sizePx).apply { gravity = Gravity.TOP or Gravity.START; x = 16; y = 320 }
        val view = BubbleView(this)
        setupDrag(view, lp) { if (isRecording) stopRecordingAndHide() else startRecording() }
        wm.addView(view, lp); bubbleView = view
    }

    private fun removeBubble() {
        bubbleView?.let { runCatching { wm.removeView(it) }; bubbleView = null }
    }

    private fun bringBubbleToFront() {
        val old = bubbleView ?: return
        val oldLp = old.layoutParams as WindowManager.LayoutParams
        runCatching { wm.removeView(old) }; bubbleView = null
        val sizePx = (56 * resources.displayMetrics.density).toInt()
        val lp = baseLp(sizePx, sizePx).apply {
            gravity = oldLp.gravity; x = oldLp.x; y = oldLp.y
        }
        val fresh = BubbleView(this)
        setupDrag(fresh, lp) { if (isRecording) stopRecordingAndHide() else startRecording() }
        wm.addView(fresh, lp); bubbleView = fresh
    }

    // ── Recording ─────────────────────────────────────────────────────────────
    private fun startRecording() {
        if (isRecording) return
        isRecording = true
        MacroAccessibilityService.isRecording = true
        MacroAccessibilityService.recordedSteps.clear()
        bubbleView?.invalidate()
        refreshNotif()
        showOverlay()
        bringBubbleToFront()
    }

    /** Stop recording AND hide the bubble — service stays alive only if coord picker needs it */
    private fun stopRecordingAndHide() {
        if (!isRecording && bubbleView == null) return
        isRecording = false
        MacroAccessibilityService.isRecording = false
        removeOverlay()
        removeBubble()           // Bubble disappears after recording stops
        hideCoordPicker()
        refreshNotif()
        // Stop the foreground service — user returns to the app to save
        stopSelf()
    }

    // ── Capture overlay ───────────────────────────────────────────────────────
    private fun showOverlay() {
        if (overlayView != null) return
        val v = object : View(this) {
            private val tint   = Paint().apply { color = Color.argb(15, 220, 0, 0) }
            private val border = Paint().apply {
                color = Color.argb(120, 220, 0, 0)
                style = Paint.Style.STROKE; strokeWidth = 6f
            }
            override fun onDraw(c: Canvas) {
                c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), tint)
                c.drawRect(3f, 3f, width - 3f, height - 3f, border)
            }
            override fun onTouchEvent(ev: MotionEvent): Boolean {
                handleRecordTouch(ev); return true
            }
        }
        // FLAG_LAYOUT_IN_SCREEN makes the overlay start at true (0,0) including status bar
        val lp = baseLp(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            flags = flags or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        }
        wm.addView(v, lp); overlayView = v
    }

    private fun removeOverlay() {
        overlayView?.let { runCatching { wm.removeView(it) }; overlayView = null }
    }

    private fun handleRecordTouch(ev: MotionEvent) {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // Fix: use rawX/rawY — screen-absolute coords that match dispatchGesture
                downRawX = ev.rawX; downRawY = ev.rawY
                downTime = System.currentTimeMillis()
            }
            MotionEvent.ACTION_UP -> {
                val dur  = (System.currentTimeMillis() - downTime).coerceIn(50L, 5000L)
                val dist = hypot(ev.rawX - downRawX, ev.rawY - downRawY)
                val idx  = MacroAccessibilityService.recordedSteps.size
                val step = when {
                    dist > 30f -> MacroStep(
                        macroId = 0, stepIndex = idx, type = StepType.SWIPE,
                        x = downRawX, y = downRawY,
                        x2 = ev.rawX, y2 = ev.rawY,
                        duration = dur.coerceIn(100L, 3000L)
                    )
                    dur >= 500L -> MacroStep(
                        macroId = 0, stepIndex = idx, type = StepType.LONG_PRESS,
                        x = downRawX, y = downRawY,
                        duration = dur.coerceIn(600L, 5000L)
                    )
                    else -> MacroStep(
                        macroId = 0, stepIndex = idx, type = StepType.TAP,
                        x = downRawX, y = downRawY, duration = 100L
                    )
                }
                MacroAccessibilityService.recordedSteps.add(step)
                // Replay the touch through the accessibility service so the underlying app receives it
                MacroAccessibilityService.instance?.replayStep(step)
                bubbleView?.invalidate()
                refreshNotif()
            }
        }
    }

    // ── Coord picker (for step edit dialog) ───────────────────────────────────
    private fun showCoordPicker(field: String) {
        if (coordPickerView != null) return
        val dm = resources.displayMetrics
        var pickX = dm.widthPixels / 2f; var pickY = dm.heightPixels / 2f
        var lastRawX = 0f; var lastRawY = 0f

        val view = object : View(this) {
            val bg     = Paint().apply { color = Color.argb(80, 0, 0, 0) }
            val grid   = Paint().apply { color = Color.argb(30, 255,255,255); strokeWidth = 1f }
            val line   = Paint().apply { color = Color.argb(200, 0, 200, 255); strokeWidth = 2f }
            val dot    = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(60,0,200,255) }
            val dotEdge= Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color=Color.argb(255,0,200,255); style=Paint.Style.STROKE; strokeWidth=3f }
            val label  = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color=Color.WHITE; textSize=44f; textAlign=Paint.Align.CENTER
                setShadowLayer(4f,2f,2f,Color.BLACK) }
            val btn    = Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.argb(220,30,30,30) }
            val btnSet = Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.argb(220,0,140,200) }
            val btnTxt = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color=Color.WHITE; textSize=40f; textAlign=Paint.Align.CENTER
                setShadowLayer(3f,1f,1f,Color.BLACK) }

            override fun onDraw(c: Canvas) {
                val w=width.toFloat(); val h=height.toFloat()
                c.drawRect(0f,0f,w,h,bg)
                var gx=0f; while(gx<w){c.drawLine(gx,0f,gx,h,grid);gx+=100f}
                var gy=0f; while(gy<h){c.drawLine(0f,gy,w,gy,grid);gy+=100f}
                c.drawLine(pickX,0f,pickX,h,line); c.drawLine(0f,pickY,w,pickY,line)
                c.drawCircle(pickX,pickY,36f,dot); c.drawCircle(pickX,pickY,36f,dotEdge)
                val ly = if(pickY>h*0.8f) pickY-55f else pickY+70f
                c.drawText("(${pickX.toInt()}, ${pickY.toInt()})",pickX,ly,label)
                val bw=260f; val bh=90f; val m=36f; val by=h-bh-m
                c.drawRoundRect(m,by,m+bw,by+bh,18f,18f,btn)
                c.drawText("Cancel",m+bw/2,by+bh*0.65f,btnTxt)
                val sx=w-m-bw
                c.drawRoundRect(sx,by,sx+bw,by+bh,18f,18f,btnSet)
                c.drawText("Set",sx+bw/2,by+bh*0.65f,btnTxt)
            }

            override fun onTouchEvent(ev: MotionEvent): Boolean {
                val w=width.toFloat(); val h=height.toFloat()
                val bw=260f; val bh=90f; val m=36f; val by=h-bh-m
                when(ev.action){
                    MotionEvent.ACTION_DOWN -> { lastRawX=ev.rawX; lastRawY=ev.rawY }
                    MotionEvent.ACTION_MOVE -> {
                        pickX=(pickX+ev.rawX-lastRawX).coerceIn(0f,w)
                        pickY=(pickY+ev.rawY-lastRawY).coerceIn(0f,h)
                        lastRawX=ev.rawX; lastRawY=ev.rawY; invalidate()
                    }
                    MotionEvent.ACTION_UP -> {
                        val tx=ev.x; val ty=ev.y
                        if(tx in m..(m+bw) && ty in by..(by+bh)){
                            coordPickResult.value=null; hideCoordPicker()
                        }
                        val sx=w-m-bw
                        if(tx in sx..(sx+bw) && ty in by..(by+bh)){
                            coordPickResult.value=Pair(field,Pair(pickX,pickY)); hideCoordPicker()
                        }
                    }
                }; return true
            }
        }
        val lp = baseLp(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            flags = flags or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        }
        wm.addView(view, lp); coordPickerView = view
        bringBubbleToFront()
    }

    private fun hideCoordPicker() {
        coordPickerView?.let { runCatching { wm.removeView(it) }; coordPickerView = null }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private fun setupDrag(view: View, lp: WindowManager.LayoutParams, onClick: () -> Unit) {
        var sx=0f; var sy=0f; var dragging=false
        view.setOnTouchListener { _, ev ->
            when(ev.action){
                MotionEvent.ACTION_DOWN -> { sx=ev.rawX; sy=ev.rawY; dragging=false; true }
                MotionEvent.ACTION_MOVE -> {
                    if(!dragging && hypot(ev.rawX-sx,ev.rawY-sy)>12f) dragging=true
                    if(dragging){ lp.x+=(ev.rawX-sx).toInt(); lp.y+=(ev.rawY-sy).toInt()
                        wm.updateViewLayout(view,lp); sx=ev.rawX; sy=ev.rawY }
                    true
                }
                MotionEvent.ACTION_UP -> { if(!dragging) onClick(); true }
                else -> false
            }
        }
    }

    private fun baseLp(w: Int, h: Int) = WindowManager.LayoutParams(
        w, h,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
        PixelFormat.TRANSLUCENT
    )

    // ── Bubble view ───────────────────────────────────────────────────────────
    private inner class BubbleView(ctx: Context) : View(ctx) {
        private val fill   = Paint(Paint.ANTI_ALIAS_FLAG)
        private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color=Color.WHITE; style=Paint.Style.STROKE; strokeWidth=4f }
        private val txt    = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color=Color.WHITE; textAlign=Paint.Align.CENTER
            textSize=28f; typeface=Typeface.DEFAULT_BOLD }

        override fun onDraw(c: Canvas) {
            val cx=width/2f; val cy=height/2f; val r=minOf(cx,cy)-4f
            fill.color = if(isRecording) Color.argb(230,210,45,45) else Color.argb(230,33,150,83)
            c.drawCircle(cx,cy,r,fill); c.drawCircle(cx,cy,r,stroke)
            val count = MacroAccessibilityService.recordedSteps.size
            val label = if(isRecording && count>0) "$count" else if(isRecording) "●" else "⏺"
            c.drawText(label, cx, cy+10f, txt)
        }
    }

    companion object {
        private const val NOTIF_ID = 1337

        const val CMD             = "command"
        const val CMD_START       = "start_record"
        const val CMD_STOP        = "stop_record"
        const val CMD_TOGGLE      = "toggle"
        const val CMD_DISMISS     = "dismiss"
        const val CMD_PICK_START  = "pick_start"
        const val CMD_PICK_CANCEL = "pick_cancel"
        const val EXTRA_PICK_FIELD = "pick_field"
        const val FIELD_XY        = "xy"
        const val FIELD_XY2       = "xy2"

        val coordPickResult = MutableStateFlow<Pair<String, Pair<Float, Float>>?>(null)
    }
}
