package com.shower.voicectrl.bus

enum class Command {
    NEXT,
    PREV,
    PAUSE,
    UNMATCHED;

    companion object {
        fun fromKeyword(text: String): Command? = when (text.trim()) {
            "下一条" -> NEXT
            "上一条" -> PREV
            "暂停" -> PAUSE
            else -> null
        }
    }
}
