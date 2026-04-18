package com.shower.voicectrl.voice

import android.content.Context
import com.shower.voicectrl.bus.Command
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.StorageService
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class VoskRecognizer private constructor(
    private val recognizer: Recognizer,
    private val onCommand: (Command) -> Unit,
) {

    fun acceptPcm(buffer: ShortArray, readSize: Int) {
        val hasResult = recognizer.acceptWaveForm(buffer, readSize)
        val json = if (hasResult) recognizer.result else recognizer.partialResult
        val text = JSONObject(json).optString(if (hasResult) "text" else "partial").trim()
        if (text.isEmpty()) return
        val cmd = Command.fromKeyword(text) ?: if (hasResult) Command.UNMATCHED else return
        onCommand(cmd)
    }

    fun close() {
        recognizer.close()
    }

    companion object {
        private const val MODEL_ASSETS_DIR = "vosk-model-small-cn-0.22"
        private const val GRAMMAR_JSON = "[\"下一条\",\"上一条\",\"暂停\",\"[unk]\"]"

        suspend fun create(
            context: Context,
            sampleRate: Int = 16_000,
            onCommand: (Command) -> Unit,
        ): VoskRecognizer {
            val model = loadModel(context)
            val recognizer = Recognizer(model, sampleRate.toFloat(), GRAMMAR_JSON)
            return VoskRecognizer(recognizer, onCommand)
        }

        private suspend fun loadModel(context: Context): Model =
            suspendCancellableCoroutine { cont ->
                StorageService.unpack(
                    context, MODEL_ASSETS_DIR, "model",
                    { model -> cont.resume(model) },
                    { e -> cont.resumeWithException(e) },
                )
            }
    }
}
