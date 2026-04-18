package com.shower.voicectrl.sound

import android.media.AudioManager
import android.media.ToneGenerator
import android.util.Log
import com.shower.voicectrl.bus.Command

/**
 * 即时反馈音。用 [ToneGenerator] 而不是 SoundPool：
 *   - 无需加载 ogg/wav 资源，构造即可用
 *   - 各命令用不同的系统 tone 区分
 *   - 通过 STREAM_MUSIC 走媒体音量，便于音量调节
 */
class SoundFeedback {

    private val tone = ToneGenerator(AudioManager.STREAM_MUSIC, VOLUME)

    fun play(command: Command) {
        val type = when (command) {
            Command.NEXT -> ToneGenerator.TONE_PROP_BEEP2        // 上扬双音
            Command.PREV -> ToneGenerator.TONE_PROP_BEEP         // 下行双音
            Command.PAUSE -> ToneGenerator.TONE_PROP_ACK         // 平稳中音
            Command.UNMATCHED -> ToneGenerator.TONE_PROP_NACK    // 闷音
        }
        val ok = tone.startTone(type, DURATION_MS)
        if (!ok) Log.w(TAG, "startTone returned false for $command")
    }

    fun release() {
        tone.release()
    }

    companion object {
        private const val TAG = "SoundFeedback"
        private const val VOLUME = 80            // 0..100
        private const val DURATION_MS = 150
    }
}
