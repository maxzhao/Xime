package com.kingzcheung.xime.settings

import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.Reader
import java.io.Writer

internal data class ExtensionDictionaryConversionStats(
    val accepted: Long,
    val skipped: Long,
)

internal object ExtensionDictionaryConverter {
    private const val DEFAULT_WEIGHT = 100L
    private const val MAX_WEIGHT = 1_000_000L
    private const val MAX_WORD_CODE_POINTS = 32

    fun convert(
        reader: Reader,
        writer: Writer,
        format: ExtensionDictionaryFormat,
    ): ExtensionDictionaryConversionStats {
        val input = reader as? BufferedReader ?: reader.buffered()
        val output = writer as? BufferedWriter ?: writer.buffered()
        var accepted = 0L
        var skipped = 0L
        var inRimeBody = format != ExtensionDictionaryFormat.RIME_DICTIONARY

        input.forEachLine { rawLine ->
            val line = rawLine.removePrefix("\uFEFF").trim()
            if (format == ExtensionDictionaryFormat.RIME_DICTIONARY && !inRimeBody) {
                if (line == "...") inRimeBody = true
                return@forEachLine
            }
            if (line.isEmpty() || line.startsWith('#')) return@forEachLine

            val parsed = parseEntry(line, format)
            if (parsed == null || !isPureHanWord(parsed.first)) {
                skipped++
                return@forEachLine
            }
            output.append(parsed.first)
                .append('\t')
                .append(parsed.second.toString())
                .append('\n')
            accepted++
        }
        output.flush()
        return ExtensionDictionaryConversionStats(accepted, skipped)
    }

    internal fun parseEntry(
        line: String,
        format: ExtensionDictionaryFormat,
    ): Pair<String, Long>? {
        val fields = when (format) {
            ExtensionDictionaryFormat.RIME_DICTIONARY,
            ExtensionDictionaryFormat.TAB_FREQUENCY,
            -> line.split('\t')

            ExtensionDictionaryFormat.SPACE_FREQUENCY -> line.split(Regex("\\s+"))
            ExtensionDictionaryFormat.WORD_ONLY -> listOf(line)
        }
        val word = fields.firstOrNull()?.trim().orEmpty()
        if (word.isEmpty()) return null
        val weight = fields.drop(1)
            .asReversed()
            .asSequence()
            .mapNotNull { it.trim().toLongOrNull() }
            .firstOrNull()
            ?.coerceIn(1L, MAX_WEIGHT)
            ?: DEFAULT_WEIGHT
        return word to weight
    }

    internal fun isPureHanWord(word: String): Boolean {
        val codePoints = word.codePoints().toArray()
        if (codePoints.size !in 2..MAX_WORD_CODE_POINTS) return false
        return codePoints.all { Character.UnicodeScript.of(it) == Character.UnicodeScript.HAN }
    }
}
