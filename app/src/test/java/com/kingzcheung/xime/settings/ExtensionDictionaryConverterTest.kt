package com.kingzcheung.xime.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.StringReader
import java.io.StringWriter

class ExtensionDictionaryConverterTest {
    @Test
    fun `rime conversion keeps only multi-character Han words and weights`() {
        val input = listOf(
            "# comment",
            "---",
            "name: sample",
            "version: \"1\"",
            "...",
            "人工智能\tren gong zhi neng\t998",
            "〇〇八\t100",
            "一\tyi\t50",
            "C语言\tc yu yan\t20",
            "中文 词\tzhong wen\t10",
        ).joinToString("\n")
        val output = StringWriter()

        val stats = ExtensionDictionaryConverter.convert(
            StringReader(input),
            output,
            ExtensionDictionaryFormat.RIME_DICTIONARY,
        )

        assertEquals(2L, stats.accepted)
        assertEquals(3L, stats.skipped)
        assertEquals("人工智能\t998\n〇〇八\t100\n", output.toString())
    }

    @Test
    fun `space-frequency conversion accepts Jieba rows`() {
        val output = StringWriter()
        val stats = ExtensionDictionaryConverter.convert(
            StringReader("自然语言 1234 n\nC++ 30 nz\n"),
            output,
            ExtensionDictionaryFormat.SPACE_FREQUENCY,
        )

        assertEquals(1L, stats.accepted)
        assertEquals(1L, stats.skipped)
        assertEquals("自然语言\t1234\n", output.toString())
    }

    @Test
    fun `Han validator supports supplementary Han and rejects single characters`() {
        assertTrue(ExtensionDictionaryConverter.isPureHanWord("𠀀中文"))
        assertTrue(ExtensionDictionaryConverter.isPureHanWord("〇一"))
        assertFalse(ExtensionDictionaryConverter.isPureHanWord("中"))
        assertFalse(ExtensionDictionaryConverter.isPureHanWord("中文A"))
    }
}
