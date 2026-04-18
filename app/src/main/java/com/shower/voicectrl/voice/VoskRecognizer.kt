package com.shower.voicectrl.voice

import android.content.Context
import android.util.Log
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
        // 只处理完整识别（end-of-speech 后的 final result）。
        // partial 结果会在用户说话过程中多次触发同一关键词，导致命令重复执行。
        val hasResult = recognizer.acceptWaveForm(buffer, readSize)
        if (!hasResult) return
        val raw = JSONObject(recognizer.result).optString("text").trim()
        if (raw.isEmpty()) return
        // Vosk 中文模型按单字索引，识别结果里单字之间带空格
        val text = raw.replace(" ", "")
        Log.d("VoskRecognizer", "final raw=[$raw] text=[$text]")
        val cmd = Command.fromKeyword(text) ?: Command.UNMATCHED
        onCommand(cmd)
    }

    fun close() {
        recognizer.close()
    }

    companion object {
        private const val MODEL_ASSETS_DIR = "vosk-model-small-cn-0.22"
        // 词典按单字索引，多字词需空格分隔
        private const val GRAMMAR_JSON = "[\"下 一 条\",\"上 一 条\",\"暂 停\",\"[unk]\"]"

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
