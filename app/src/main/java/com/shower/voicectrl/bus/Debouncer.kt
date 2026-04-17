package com.shower.voicectrl.bus

class Debouncer(
    private val windowMs: Long = 800,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    private val lastEmitAt = mutableMapOf<Command, Long>()

    @Synchronized
    fun shouldEmit(command: Command): Boolean {
        val now = clock()
        val last = lastEmitAt[command]
        if (last != null && now - last < windowMs) return false
        lastEmitAt[command] = now
        return true
    }
}
