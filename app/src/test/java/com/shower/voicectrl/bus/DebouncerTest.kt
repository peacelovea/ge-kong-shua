package com.shower.voicectrl.bus

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DebouncerTest {
    private var now = 0L
    private val clock: () -> Long = { now }

    @Test
    fun `first event of a key passes`() {
        val d = Debouncer(windowMs = 800, clock = clock)
        assertTrue(d.shouldEmit(Command.NEXT))
    }

    @Test
    fun `same key within window is dropped`() {
        val d = Debouncer(windowMs = 800, clock = clock)
        now = 0L; d.shouldEmit(Command.NEXT)
        now = 799L
        assertFalse(d.shouldEmit(Command.NEXT))
    }

    @Test
    fun `same key after window passes again`() {
        val d = Debouncer(windowMs = 800, clock = clock)
        now = 0L; d.shouldEmit(Command.NEXT)
        now = 801L
        assertTrue(d.shouldEmit(Command.NEXT))
    }

    @Test
    fun `different keys are independent`() {
        val d = Debouncer(windowMs = 800, clock = clock)
        now = 0L; assertTrue(d.shouldEmit(Command.NEXT))
        now = 10L; assertTrue(d.shouldEmit(Command.PREV))
        now = 20L; assertTrue(d.shouldEmit(Command.PAUSE))
    }
}
