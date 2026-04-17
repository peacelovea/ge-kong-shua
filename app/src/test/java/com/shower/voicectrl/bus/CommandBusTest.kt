package com.shower.voicectrl.bus

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CommandBusTest {

    @Test
    fun `emit reaches a single subscriber`() = runTest {
        val bus = CommandBus()
        val collectJob = launch {
            val received = bus.events.take(2).toList()
            assertEquals(listOf(Command.NEXT, Command.PAUSE), received)
        }
        yield()
        bus.emit(Command.NEXT)
        bus.emit(Command.PAUSE)
        collectJob.join()
    }

    @Test
    fun `emit does not block when no subscribers`() = runTest {
        val bus = CommandBus()
        repeat(8) { bus.emit(Command.NEXT) }
    }
}
