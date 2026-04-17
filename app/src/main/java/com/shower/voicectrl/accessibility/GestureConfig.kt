package com.shower.voicectrl.accessibility

import com.shower.voicectrl.bus.Command

enum class GestureType { SWIPE, TAP, NONE }

data class Gesture(
    val type: GestureType,
    val startX: Int,
    val startY: Int,
    val endX: Int,
    val endY: Int,
    val durationMs: Long,
)

data class GestureConfig(
    val centerXPct: Float,
    val swipeTopYPct: Float,
    val swipeBottomYPct: Float,
    val tapYPct: Float,
    val swipeDurationMs: Long,
    val tapDurationMs: Long,
) {
    fun toGesture(command: Command, widthPx: Int, heightPx: Int): Gesture {
        val cx = (centerXPct * widthPx).toInt()
        val topY = (swipeTopYPct * heightPx).toInt()
        val botY = (swipeBottomYPct * heightPx).toInt()
        val tapY = (tapYPct * heightPx).toInt()
        return when (command) {
            Command.NEXT -> Gesture(GestureType.SWIPE, cx, botY, cx, topY, swipeDurationMs)
            Command.PREV -> Gesture(GestureType.SWIPE, cx, topY, cx, botY, swipeDurationMs)
            Command.PAUSE -> Gesture(GestureType.TAP, cx, tapY, cx, tapY, tapDurationMs)
            Command.UNMATCHED -> Gesture(GestureType.NONE, 0, 0, 0, 0, 0)
        }
    }

    companion object {
        fun default() = GestureConfig(
            centerXPct = 0.50f,
            swipeTopYPct = 0.25f,
            swipeBottomYPct = 0.75f,
            tapYPct = 0.50f,
            swipeDurationMs = 150L,
            tapDurationMs = 40L,
        )
    }
}
