package com.kingzcheung.xime.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtensionDictionaryCatalogTest {
    @Test
    fun `catalog exposes every approved dictionary with immutable sources`() {
        val dictionaries = ExtensionDictionaryCatalog.dictionaries

        assertEquals(29, dictionaries.size)
        assertEquals(dictionaries.size, dictionaries.map { it.id }.toSet().size)
        assertEquals(6, dictionaries.count { it.group == "通用词库" })
        assertEquals(9, dictionaries.count { it.group == "万象分类词库" })
        assertEquals(11, dictionaries.count { it.group == "THUOCL 分类词库" })
        assertEquals(3, dictionaries.count { it.group == "中文人名与成语语料" })
        dictionaries.forEach { definition ->
            assertTrue(definition.sourceUrl.startsWith("https://raw.githubusercontent.com/"))
            assertTrue(definition.sourceUrl.contains(Regex("/[0-9a-f]{40}/")))
            assertTrue(definition.license.isNotBlank())
            assertTrue(definition.licenseUrl.startsWith("https://"))
        }
    }

    @Test
    fun `download candidates prefer pinned jsDelivr mirror and retain upstream fallback`() {
        val upstream = "https://raw.githubusercontent.com/example/lexicon/0123456789abcdef0123456789abcdef01234567/data/words.txt"
        val candidates = ExtensionDictionaryManager.downloadCandidates(upstream)

        assertEquals(
            "https://cdn.jsdelivr.net/gh/example/lexicon@0123456789abcdef0123456789abcdef01234567/data/words.txt",
            candidates.first(),
        )
        assertEquals(upstream, candidates.last())
    }

    @Test
    fun `managed patch preserves existing schema customizations`() {
        val original = """
            patch:
              menu/page_size: 9
              "translator/dictionary": old_dictionary
            ...
        """.trimIndent() + "\n"

        val replaced = ExtensionDictionaryManager.applyManagedPatchValue(
            original,
            "translator/dictionary",
            "xime_pinyin",
        )
        val inserted = ExtensionDictionaryManager.applyManagedPatchValue(
            replaced,
            "translator/user_dict",
            "pinyin_simp",
        )

        assertTrue(inserted.contains("menu/page_size: 9"))
        assertTrue(inserted.contains("\"translator/dictionary\": xime_pinyin"))
        assertTrue(inserted.contains("\"translator/user_dict\": pinyin_simp"))
        assertEquals(1, Regex("translator/dictionary").findAll(inserted).count())
    }

    @Test
    fun `aggregate sources include base dictionaries and managed words`() {
        val pinyin = ExtensionDictionaryManager.pinyinAggregateSource()
        val wubi = ExtensionDictionaryManager.wubiAggregateSource()

        assertTrue(pinyin.contains("- pinyin_simp"))
        assertTrue(pinyin.contains("- pinyin_simp_ext"))
        assertTrue(pinyin.contains("- xime_extension_words"))
        assertTrue(wubi.contains("- wubi86"))
        assertTrue(wubi.contains("- wubi86_extra"))
        assertTrue(wubi.contains("formula: \"AaAbBaBb\""))
        assertTrue(wubi.contains("- xime_extension_words"))
    }
}
