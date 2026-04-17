package com.shower.voicectrl.bus

import org.junit.Assert.assertEquals
import org.junit.Test

class CommandTest {
    @Test
    fun `Command enum has exactly the four expected values`() {
        val names = Command.entries.map { it.name }.toSet()
        assertEquals(setOf("NEXT", "PREV", "PAUSE", "UNMATCHED"), names)
    }

    @Test
    fun `fromKeyword maps known Chinese phrases`() {
        assertEquals(Command.NEXT, Command.fromKeyword("下一条"))
        assertEquals(Command.PREV, Command.fromKeyword("上一条"))
        assertEquals(Command.PAUSE, Command.fromKeyword("暂停"))
    }

    @Test
    fun `fromKeyword returns null for unknown`() {
        assertEquals(null, Command.fromKeyword("随便说一句"))
    }
}
