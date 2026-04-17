package com.shower.voicectrl.sound

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.shower.voicectrl.R
import com.shower.voicectrl.bus.Command

class SoundFeedback(context: Context) {

    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(2)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        ).build()

    private val ids: Map<Command, Int> = mapOf(
        Command.NEXT to pool.load(context, R.raw.fb_next, 1),
        Command.PREV to pool.load(context, R.raw.fb_prev, 1),
        Command.PAUSE to pool.load(context, R.raw.fb_pause, 1),
        Command.UNMATCHED to pool.load(context, R.raw.fb_unmatched, 1),
    )

    fun play(command: Command) {
        val id = ids[command] ?: return
        pool.play(id, 1f, 1f, 1, 0, 1f)
    }

    fun release() {
        pool.release()
    }
}
