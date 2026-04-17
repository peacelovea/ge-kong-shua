package com.shower.voicectrl.bus

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class CommandBus {
    private val _events = MutableSharedFlow<Command>(
        replay = 0,
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<Command> = _events.asSharedFlow()

    fun emit(command: Command) {
        _events.tryEmit(command)
    }

    companion object {
        val INSTANCE: CommandBus by lazy { CommandBus() }
    }
}
