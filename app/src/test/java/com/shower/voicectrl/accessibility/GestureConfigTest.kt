package com.shower.voicectrl.accessibility

import com.shower.voicectrl.bus.Command
import org.junit.Assert.assertEquals
import org.junit.Test

class GestureConfigTest {

    @Test
    fun `NEXT swipes from lower to upper center`() {
        val g = GestureConfig.default().toGesture(Command.NEXT, widthPx = 1260, heightPx = 2800)
        assertEquals(GestureType.SWIPE, g.type)
        assertEquals(630, g.startX)
        assertEquals(2100, g.startY)
        assertEquals(630, g.endX)
        assertEquals(700, g.endY)
        assertEquals(150L, g.durationMs)
    }

    @Test
    fun `PREV swipes from upper to lower center`() {
        val g = GestureConfig.default().toGesture(Command.PREV, widthPx = 1260, heightPx = 2800)
        assertEquals(GestureType.SWIPE, g.type)
        assertEquals(630, g.startX)
        assertEquals(700, g.startY)
        assertEquals(630, g.endX)
        assertEquals(2100, g.endY)
    }

    @Test
    fun `PAUSE taps center`() {
        val g = GestureConfig.default().toGesture(Command.PAUSE, widthPx = 1260, heightPx = 2800)
        assertEquals(GestureType.TAP, g.type)
        assertEquals(630, g.startX)
        assertEquals(1400, g.startY)
    }

    @Test
    fun `UNMATCHED has no gesture`() {
        val g = GestureConfig.default().toGesture(Command.UNMATCHED, widthPx = 1260, heightPx = 2800)
        assertEquals(GestureType.NONE, g.type)
    }
}
