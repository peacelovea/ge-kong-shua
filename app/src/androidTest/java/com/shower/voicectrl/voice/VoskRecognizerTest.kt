package com.shower.voicectrl.voice

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.shower.voicectrl.bus.Command
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.DataInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

@RunWith(AndroidJUnit4::class)
class VoskRecognizerTest {

    private fun readWavPcm(name: String): ShortArray {
        val ctx = InstrumentationRegistry.getInstrumentation().context
        val stream = ctx.assets.open("test_audio/$name")
        DataInputStream(stream).use { dis ->
            dis.skipBytes(44)
            val bytes = dis.readBytes()
            val shorts = ShortArray(bytes.size / 2)
            ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
                .asShortBuffer().get(shorts)
            return shorts
        }
    }

    private fun recognizeFile(fileName: String): List<Command> = runBlocking {
        val received = mutableListOf<Command>()
        val recognizer = VoskRecognizer.create(
            InstrumentationRegistry.getInstrumentation().targetContext,
        ) { received += it }
        val pcm = readWavPcm(fileName)
        var i = 0
        while (i < pcm.size) {
            val size = minOf(1024, pcm.size - i)
            val chunk = pcm.copyOfRange(i, i + size)
            recognizer.acceptPcm(chunk, size)
            i += size
        }
        recognizer.close()
        Log.d("VoskTest", "recognized: $received")
        received
    }

    @Test
    fun recognizes_next() {
        assertTrue(recognizeFile("next_clean.wav").contains(Command.NEXT))
    }

    @Test
    fun recognizes_prev() {
        assertTrue(recognizeFile("prev_clean.wav").contains(Command.PREV))
    }

    @Test
    fun recognizes_pause() {
        assertTrue(recognizeFile("pause_clean.wav").contains(Command.PAUSE))
    }

    @Test
    fun unmatched_stays_unmatched_or_empty() {
        val cmds = recognizeFile("unmatched_clean.wav")
        assertTrue(cmds.isEmpty() || cmds.all { it == Command.UNMATCHED })
    }
}
