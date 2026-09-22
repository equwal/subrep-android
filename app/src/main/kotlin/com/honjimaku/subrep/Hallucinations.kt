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

    /**
     * A piece repeated four times or more: "о-о-о-о-о-о", "はいはいはいはいはい", or a phrase
     * of up to [LOOP_PIECE_MAX] characters. The lazy group finds the shortest piece.
     */
    private val loop = Regex("(.{1,$LOOP_PIECE_MAX}?)\\1{4,}")

    /** True when [text] is one of the invented lines, or has no word at all. */
    fun isHallucination(text: String): Boolean {
        val line = text.trim()
        return patterns.any { it.containsMatchIn(line) }
    }

    /**
     * [text] with each loop cut. Whisper falls into a loop on singing or noise. A short piece
     * (up to [SHORT_PIECE_MAX] characters) keeps three repeats, so that a real "no, no, no" stays.
     * A phrase keeps one. The cut runs again until nothing changes, so a loop that a cut uncovers
     * is cut too.
     */
    fun collapse(text: String): String {
        var line = text
        while (true) {
            val cut = loop.replace(line) { match ->
                val piece = match.groupValues[1]
                if (piece.length <= SHORT_PIECE_MAX) piece.repeat(3) else piece
            }
            if (cut == line) return line
            line = cut
        }
    }

    private const val LOOP_PIECE_MAX = 40
    private const val SHORT_PIECE_MAX = 6
}
