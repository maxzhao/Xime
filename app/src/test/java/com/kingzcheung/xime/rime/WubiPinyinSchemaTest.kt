package com.kingzcheung.xime.rime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class WubiPinyinSchemaTest {
    private fun asset(path: String): String {
        val url = requireNotNull(javaClass.classLoader?.getResource(path)) { "missing test asset $path" }
        return File(url.toURI()).readText()
    }

    @Test
    fun `mixed schema exposes ascii mode without global code length`() {
        val schema = asset("rime/wubi86_pinyin.schema.yaml")
        assertTrue(schema.contains("- name: ascii_mode"))
        assertTrue(schema.contains("reset: 1"))
        assertFalse(schema.contains("max_code_length"))
    }

    @Test
    fun `accepted fuzzy relations are shared in dependency and long finals come first`() {
        val schema = asset("rime/pinyin_simp.schema.yaml")
        val expected = listOf(
            "derive/^([zcs])h/$1/", "derive/^([zcs])/$1h/",
            "derive/^n/l/", "derive/^l/n/",
            "derive/ian$/iang/", "derive/iang$/ian/",
            "derive/uan$/uang/", "derive/uang$/uan/",
            "derive/an$/ang/", "derive/ang$/an/",
            "derive/en$/eng/", "derive/eng$/en/",
            "derive/in$/ing/", "derive/ing$/in/",
            "derive/eng$/ong/", "derive/ong$/eng/",
        )
        expected.forEach { assertTrue("missing $it", schema.contains(it)) }
        assertTrue(schema.indexOf("derive/ian$/iang/") < schema.indexOf("derive/an$/ang/"))
        assertTrue(schema.indexOf("derive/uan$/uang/") < schema.indexOf("derive/an$/ang/"))
    }

    @Test
    fun `full Wubi source order selects longest and first equal length code`() {
        val sourceEntries = asset("rime/wubi86.dict.yaml").lineSequence()
            .filterNot { it.startsWith("#") }
            .mapNotNull { line ->
                val fields = line.split('\t')
                fields.getOrNull(1)?.takeIf { it.isNotEmpty() && it.all(Char::isLetter) }
                    ?.let { fields[0] to it.lowercase() }
            }
            .toList()

        fun fullCode(text: String): String? = sourceEntries.asSequence()
            .filter { it.first == text }
            .map { it.second }
            .fold<String, String?>(null) { selected, code ->
                if (selected == null || code.length > selected.length) code else selected
            }

        assertEquals("ntcy", fullCode("发"))
        assertEquals("lhng", fullCode("囗"))
    }
}
