package com.shower.voicectrl.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import com.shower.voicectrl.R
import com.shower.voicectrl.bus.Command
import com.shower.voicectrl.bus.CommandBus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class ShowerAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val main = Handler(Looper.getMainLooper())
    private var collectJob: Job? = null
    private var overlay: View? = null
    private val hideOverlayRunnable = Runnable { fadeOutAndRemoveOverlay() }

    override fun onServiceConnected() {
        super.onServiceConnected()
        collectJob = scope.launch {
            CommandBus.INSTANCE.events.onEach(::handleCommand).collect()
        }
        Log.i(TAG, "connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) { /* unused */ }
    override fun onInterrupt() { /* unused */ }

    override fun onDestroy() {
        collectJob?.cancel()
        main.removeCallbacks(hideOverlayRunnable)
        removeOverlay()
        super.onDestroy()
    }

    private fun handleCommand(command: Command) {
        val fgPkg = rootInActiveWindow?.packageName?.toString()
        Log.i(TAG, "handleCommand cmd=$command fg=$fgPkg")
        if (command == Command.UNMATCHED) {
            showBanner(command)
            return
        }
        if (fgPkg != DOUYIN_PACKAGE) {
            showBanner(Command.UNMATCHED, subtitle = "非抖音前台")
            return
        }
        showBanner(command)
        val metrics = resources.displayMetrics
        val gesture = GestureConfig.default().toGesture(command, metrics.widthPixels, metrics.heightPixels)
        if (gesture.type == GestureType.NONE) return

        val path = Path().apply {
            moveTo(gesture.startX.toFloat(), gesture.startY.toFloat())
            if (gesture.type == GestureType.SWIPE) {
                lineTo(gesture.endX.toFloat(), gesture.endY.toFloat())
            }
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, gesture.durationMs)
        val desc = GestureDescription.Builder().addStroke(stroke).build()

        dispatchGesture(desc, object : GestureResultCallback() {
            override fun onCompleted(g: GestureDescription?) { /* visual-only feedback */ }
            override fun onCancelled(g: GestureDescription?) {
                Log.w(TAG, "gesture cancelled for $command")
            }
        }, Handler(Looper.getMainLooper()))
    }

    private fun showBanner(command: Command, subtitle: String? = null) {
        main.post { drawBanner(command, subtitle) }
    }

    private fun drawBanner(command: Command, subtitle: String?) {
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        removeOverlay()

        // iOS HIG-inspired banner: glass-dark rounded card, icon + label,
        // pinned near top of the screen, subtle blur when available (API 31+).
        val bg = GradientDrawable().apply {
            cornerRadius = dp(22).toFloat()
            setColor(Color.parseColor("#E60A0A0A")) // near-black, ~90% opaque
            setStroke(dp(1), Color.parseColor("#33FFFFFF")) // faint hairline
        }

        // 带浅色圆底的彩色图标（iOS 通知常见样式）
        val iconSize = dp(34)
        val iconBg = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(colorOf(command))
        }
        val iconImg = ImageView(this).apply {
            val d = ContextCompat.getDrawable(this@ShowerAccessibilityService, iconResOf(command))!!
                .mutate()
            DrawableCompat.setTint(d, Color.WHITE)
            setImageDrawable(d)
            background = iconBg
            val padIn = dp(6)
            setPadding(padIn, padIn, padIn, padIn)
        }
        val iconWrap = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            addView(
                iconImg,
                LinearLayout.LayoutParams(iconSize, iconSize)
            )
            setPadding(0, 0, dp(12), 0)
        }

        val labelCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        val title = TextView(this).apply {
            text = labelOf(command)
            setTextColor(Color.WHITE)
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            letterSpacing = 0.01f
        }
        labelCol.addView(title)
        if (subtitle != null) {
            val sub = TextView(this).apply {
                text = subtitle
                setTextColor(Color.parseColor("#B3FFFFFF"))
                setTypeface(Typeface.DEFAULT)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            }
            labelCol.addView(sub)
        }

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(10), dp(20), dp(10))
            background = bg
            addView(iconWrap)
            addView(labelCol)
            alpha = 0f
        }

        // Optional Android 12+ background blur for true iOS-like frosted glass
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                row.setRenderEffect(
                    android.graphics.RenderEffect.createBlurEffect(
                        0f, 0f, android.graphics.Shader.TileMode.CLAMP
                    )
                )
            } catch (_: Throwable) { /* not supported on this device */ }
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dp(56) // clear the status bar / notch
        }

        try {
            wm.addView(row, params)
            overlay = row
            row.animate().alpha(1f).translationY(0f).setStartDelay(0).setDuration(180).start()
            row.translationY = dp(-8).toFloat()
            row.animate().translationY(0f).setDuration(180).start()
            main.removeCallbacks(hideOverlayRunnable)
            main.postDelayed(hideOverlayRunnable, OVERLAY_MS)
        } catch (t: Throwable) {
            Log.e(TAG, "showBanner failed", t)
        }
    }

    private fun fadeOutAndRemoveOverlay() {
        val v = overlay ?: return
        v.animate().alpha(0f).setDuration(140).withEndAction { removeOverlay() }.start()
    }

    private fun removeOverlay() {
        val v = overlay ?: return
        overlay = null
        try {
            (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(v)
        } catch (_: Throwable) { /* already detached */ }
    }

    private fun dp(v: Int): Int =
        (v * resources.displayMetrics.density).toInt()

    private fun labelOf(command: Command): String = when (command) {
        Command.NEXT -> "下一条"
        Command.PREV -> "上一条"
        Command.PAUSE -> "暂停"
        Command.UNMATCHED -> "未匹配"
    }

    private fun iconResOf(command: Command): Int = when (command) {
        Command.NEXT -> R.drawable.ic_chevron_down
        Command.PREV -> R.drawable.ic_chevron_up
        Command.PAUSE -> R.drawable.ic_pause
        Command.UNMATCHED -> R.drawable.ic_unmatched
    }

    private fun colorOf(command: Command): Int = when (command) {
        Command.NEXT -> Color.parseColor("#34C759")      // iOS green
        Command.PREV -> Color.parseColor("#0A84FF")      // iOS blue
        Command.PAUSE -> Color.parseColor("#FF9F0A")     // iOS orange
        Command.UNMATCHED -> Color.parseColor("#8E8E93") // iOS gray
    }

    companion object {
        private const val TAG = "ShowerAccess"
        private const val DOUYIN_PACKAGE = "com.ss.android.ugc.aweme"
        private const val OVERLAY_MS = 1200L
    }
}
