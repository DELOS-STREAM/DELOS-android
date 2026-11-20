package com.limelight.shell.screens

import com.limelight.shell.ui.FocusGap
import com.limelight.shell.ui.FocusLook
import com.limelight.shell.ui.FocusRing
import com.limelight.shell.ui.FocusSpacing
import com.limelight.shell.ui.SETTING_ROWS
import java.text.Normalizer
import java.util.Locale

/**
 * How the settings search compares text, so that the letter picker of every language can find every title
 * (the keys of `settings_screen_search_letters`):
 *
 * * **Latin, Cyrillic**: lower-cased substring, as before.
 * * **Japanese**: the picker has the 46 basic hiragana. Both the query and the text are folded to a kana skeleton ([fold]:
 *   katakana become hiragana, dakuten and small kana are dropped, the long-vowel mark is dropped) so that `ひつとれと` finds
 *   ビットレート; the kanji of titles are replaced by their readings ([JapaneseReadings]) before folding.
 * * **Korean**: the picker has the 14 initial consonants (choseong). A query of them finds the titles whose syllables start
 *   with those consonants in that order ([initials]): `ㅎㅅㄷ` finds 해상도.
 * * **Chinese**: the picker has A-Z; besides matching Latin text, a letter sequence also finds the titles whose characters
 *   have these pinyin initials ([PinyinInitials]): `sz` finds 设置.
 */
internal object SearchText {
    /** A test of one word of the query against one row; see [matcher]. */
    fun interface Matcher { fun matches(word: String): Boolean }

    /**
     * The matcher of one row. [text] is everything the row is searched by, [titles] the short names (label, section, tab,
     * extra search terms) that readings and initials are made of, [language] the language of the texts (`ja`, `zh`, ...).
     */
    fun matcher(text: String, titles: String, language: String): Matcher {
        val plain = text.lowercase(Locale.ROOT)
        val folded by lazy(LazyThreadSafetyMode.NONE) {
            fold(text) + " " + if (language == "ja") fold(JapaneseReadings.read(titles)) else ""
        }
        val titleInitials by lazy(LazyThreadSafetyMode.NONE) { initials(titles, pinyin = language == "zh") }
        return Matcher { word ->
            plain.contains(word) || folded.contains(fold(word)) ||
                (word.all { it.isLetterOrDigit() } && titleInitials?.contains(word.map { plainJamo(it) }.joinToString("")) == true)
        }
    }

    /** Lower case; kana as hiragana without dakuten, small forms and the long-vowel mark. Text without kana is only lower-cased. */
    fun fold(text: String): String {
        val out = StringBuilder(text.length)
        for (original in text.lowercase(Locale.ROOT)) {
            if (original == '\u30FC' || original == '\u3099' || original == '\u309A') continue
            val ch = if (original in '\u30A1'..'\u30F6') (original.code - 0x60).toChar() else original
            if (ch in '\u3041'..'\u3096') {
                val base = Normalizer.normalize(ch.toString(), Normalizer.Form.NFD)[0]
                out.append(SMALL_KANA[base] ?: base)
            } else {
                out.append(ch)
            }
        }
        return out.toString()
    }

    /**
     * The initials of [text] with the spaces and punctuation removed: the initial consonant of every Hangul syllable, the
     * pinyin initial of every Han character when [pinyin] (a placeholder for one that has none), letters and digits as they
     * are. Null when the text has no Hangul and no pinyin Han, so that other languages never match by initials.
     */
    fun initials(text: String, pinyin: Boolean): String? {
        var any = false
        val out = StringBuilder()
        for (ch in text.lowercase(Locale.ROOT)) {
            when {
                ch in '\uAC00'..'\uD7A3' -> { out.append(plainJamo(CHOSEONG[(ch.code - 0xAC00) / (21 * 28)])); any = true }
                ch in '\u3131'..'\u314E' -> { out.append(plainJamo(ch)); any = true }
                ch in '\u4E00'..'\u9FFF' -> {
                    out.append(if (pinyin) PinyinInitials.of(ch) ?: NO_INITIAL else NO_INITIAL)
                    if (pinyin) any = true
                }
                ch.isLetterOrDigit() -> out.append(ch)
            }
        }
        return if (any) out.toString() else null
    }

    /** A double consonant as its plain one: the picker offers 14, an input method also types ㄲ ㄸ ㅃ ㅆ ㅉ. */
    fun plainJamo(ch: Char): Char = DOUBLE_JAMO[ch] ?: ch

    private const val NO_INITIAL = '\uFFFF'
    private const val CHOSEONG = "ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ"
    private val DOUBLE_JAMO = mapOf('ㄲ' to 'ㄱ', 'ㄸ' to 'ㄷ', 'ㅃ' to 'ㅂ', 'ㅆ' to 'ㅅ', 'ㅉ' to 'ㅈ')
    private val SMALL_KANA = mapOf(
        'ぁ' to 'あ', 'ぃ' to 'い', 'ぅ' to 'う', 'ぇ' to 'え', 'ぉ' to 'お', 'っ' to 'つ', 'ゃ' to 'や', 'ゅ' to 'ゆ', 'ょ' to 'よ',
        'ゎ' to 'わ', 'ゕ' to 'か', 'ゖ' to 'け',
    )
}

/**
 * The geometry of the on-screen letter picker: nine keys to a row, and more columns of slightly smaller keys when six rows are not enough.
 * The compact stage does not squeeze that grid: its keys keep the 64 px target in [COMPACT_ROWS] rows that scroll sideways together.
 */
internal object LetterPicker {
    const val COLUMNS = 9
    const val MAX_ROWS = 6
    const val KEY = 74
    const val GAP = 12
    private const val WIDTH = 820

    const val COMPACT_ROWS = 2
    const val COMPACT_KEY = 64
    const val COMPACT_GAP = 26

    /**
     * The keys are a dense grid (docs/GUI.md, "Focus spacing"): a focused key neither grows nor glows, and its accent ring lies inside its
     * edge, so nothing of its look reaches a neighbour and the gaps need only [FocusSpacing.CLEAR].
     */
    val KEY_RING = FocusRing(width = 4f, gap = -4f)
    val KEY_LOOK = FocusLook(1f, outset = KEY_RING.gap, band = KEY_RING.width)

    /** Between two keys, across and down; the compact keys' rows and the compact Space key too. */
    val KEYS = FocusGap(KEY_LOOK, KEY.toFloat(), GAP.toFloat())
    val COMPACT_KEYS = FocusGap(KEY_LOOK, COMPACT_KEY.toFloat(), COMPACT_GAP.toFloat())

    /** The search field ([com.limelight.shell.ui.TextFieldPill], 84 px high): 1.05 and a 4 px band 4 px outside, inside its scaled layer. */
    private val FIELD_LOOK = FocusLook(1.05f, outset = 4f, band = 4f)
    private const val FIELD_HEIGHT = 84f

    /** Under the field to the keys: 18 + 16 px on the 16:9 stage, the room of the field's growth and ring on the compact one. */
    val FIELD_TO_KEYS = FocusGap(FIELD_LOOK, FIELD_HEIGHT, 34f)
    val COMPACT_FIELD_TO_KEYS = FocusGap(FIELD_LOOK, FIELD_HEIGHT, 29f)

    /** From the last row of keys down to the 76 px Space / Delete / Clear pills (16:9). */
    val KEYS_TO_PILLS = FocusGap(FocusSpacing.PILL, 76f, 32f)

    /** Between the pills (side by side; [com.limelight.shell.ui.FocusRow] widens it for a long label), and between the compact Delete / Clear circles. */
    const val PILL_GAP = 14f
    val COMPACT_CIRCLES = FocusGap(FocusSpacing.PILL, COMPACT_KEY.toFloat(), 31f)

    /** From the compact keys down to the first result row (a [SETTING_ROWS] row): 10 px and the list's own 14 px at its top. */
    val COMPACT_KEYS_TO_RESULTS = FocusGap(SETTING_ROWS.look, SETTING_ROWS.size, 24f)

    /** The faded ends of the compact picker's scrolling layer, and its room above and below the keys (the layer never ends on a key). */
    const val COMPACT_EDGE = 24f
    const val COMPACT_RING_ROOM = 12f

    fun columns(keys: Int): Int = if (keys <= COLUMNS * MAX_ROWS) COLUMNS else (keys + MAX_ROWS - 1) / MAX_ROWS

    /** The key edge in stage px: [KEY] up to nine columns, else what fits the 820 px of the picker. */
    fun keySize(columns: Int): Int = ((WIDTH - GAP * (columns - 1)) / columns).coerceAtMost(KEY)

    /** The keys of one of the compact picker's rows: the alphabet runs on from the first row into the second. */
    fun compactColumns(keys: Int): Int = ((keys + COMPACT_ROWS - 1) / COMPACT_ROWS).coerceAtLeast(1)
}
