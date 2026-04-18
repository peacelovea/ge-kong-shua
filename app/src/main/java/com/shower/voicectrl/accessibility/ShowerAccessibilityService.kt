package com.shower.voicectrl.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.shower.voicectrl.bus.Command
import com.shower.voicectrl.bus.CommandBus
import com.shower.voicectrl.sound.SoundFeedback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class ShowerAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var collectJob: Job? = null
    private lateinit var sound: SoundFeedback

    override fun onServiceConnected() {
        super.onServiceConnected()
        sound = SoundFeedback(applicationContext)
        collectJob = scope.launch {
            CommandBus.INSTANCE.events.onEach(::handleCommand).collect()
        }
        Log.i(TAG, "connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) { /* unused */ }
    override fun onInterrupt() { /* unused */ }

    override fun onDestroy() {
        collectJob?.cancel()
        if (this::sound.isInitialized) sound.release()
        super.onDestroy()
    }

    private fun handleCommand(command: Command) {
        if (command == Command.UNMATCHED) {
            sound.play(Command.UNMATCHED)
            return
        }
        if (!isDouyinForeground()) {
            sound.play(Command.UNMATCHED)
            return
        }
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
            override fun onCompleted(g: GestureDescription?) {
                sound.play(command)
            }

            override fun onCancelled(g: GestureDescription?) {
                Log.w(TAG, "gesture cancelled for $command")
            }
        }, Handler(Looper.getMainLooper()))
    }

    private fun isDouyinForeground(): Boolean {
        val pkg = rootInActiveWindow?.packageName?.toString()
        return pkg == DOUYIN_PACKAGE
    }

    companion object {
        private const val TAG = "ShowerAccess"
        private const val DOUYIN_PACKAGE = "com.ss.android.ugc.aweme"
    }
}
