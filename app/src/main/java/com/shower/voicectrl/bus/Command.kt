package com.shower.voicectrl.bus

enum class Command {
    NEXT,
    PREV,
    PAUSE,
    UNMATCHED;

    companion object {
        // 放宽匹配：Vosk 偶尔漏识别最后一个字、或多识别前后噪音，
        // 所以用"包含"而不是"相等"。三者按"唯一性最高"的特征字先判断，
        // 避免 "上一条" 里的 "一" 被误判成 NEXT。
        fun fromKeyword(text: String): Command? {
            // 先把空格和 [unk] 去掉，Vosk 会把噪音段输出成 "[unk]" 占位符
            val t = text.replace(" ", "").replace("[unk]", "").trim()
            if (t.isEmpty()) return null
            return when {
                // 容忍"一"被漏掉（Vosk 对轻声字常常识别不出）：下一条 / 下条 都算 NEXT
                t.contains("下一") || t.contains("下条") -> NEXT
                t.contains("上一") || t.contains("上条") -> PREV
                t.contains("暂停") -> PAUSE
                else -> null
            }
        }
    }
}
