package com.honjimaku.subrep

/**
 * Lines that Whisper invents over silence or noise: the credits of the subtitles it learned
 * from. The list is that of the desktop app.
 */
object Hallucinations {

    private val patterns = listOf(
        // Finnish
        "^tekstitys",
        "^tekstityksen tuotti",
        "^k[aä]ä?nn[oö]s",
        "^kiitos( kun katsoit| paljon| katsomisesta)?[.!]?$",
        "^suomennos",
        // Japanese, which Whisper emits over silence all the time
        "ご(視聴|清聴)(いただき)?ありがとうございま",
        "チャンネル登録",
        "^字幕",
        "^おわり[。]?$",
        "^(お|ご)?しまい[。]?$",
        "^ありがとうございま(した|す)[。]?$",
        "^(えー|あー|ん|う)+[ー。]?$",
        // Russian
        "^субтитры",
        "^спасибо за просмотр",
        "^продолжение следует",
        "^редактор субтитров",
        // English and generic
        "^subtitles? by",
        "^amara\\.org",
        "^thanks? for watching",
        "^thank you[.!]?$",
        "^please subscribe",
        // Marks, symbols and spaces only: no word in any script. (Java's \W is ASCII-only.)
        "^[\\p{P}\\p{S}\\p{Z}\\s]*$",
    ).map { Regex(it, RegexOption.IGNORE_CASE) }

    /** A short piece repeated many times over: "о-о-о-о-о-о", "はいはいはいはいはい". */
    private val loop = Regex("(.{1,6}?)\\1{4,}")

    /** True when [text] is one of the invented lines, or has no word at all. */
    fun isHallucination(text: String): Boolean {
        val line = text.trim()
        return patterns.any { it.containsMatchIn(line) }
    }

    /**
     * [text] with each loop cut to three of its piece. Whisper falls into a loop on singing or
     * noise. Three repeats keep a real "no, no, no"; the rest is noise.
     */
    fun collapse(text: String): String = loop.replace(text) { it.groupValues[1].repeat(3) }
}
