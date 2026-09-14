package com.kingzcheung.xime.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WubiPinyinInputConfigTest {
    @Test
    fun `input config parses built-in English default`() {
        val input = KeysConfigHelper.parseInputConfigYamlText(
            "input:\n  wubi86_pinyin:\n    default_ascii_mode: true\n"
        )
        assertTrue(input?.wubi86Pinyin?.defaultAsciiMode == true)
    }

    @Test
    fun `custom Chinese default overrides built-in English`() {
        val builtIn = InputConfig(WubiPinyinInputConfig(defaultAsciiMode = true))
        val custom = InputConfig(WubiPinyinInputConfig(defaultAsciiMode = false))
        val merged = KeysConfigHelper.mergeInputForTest(builtIn, custom)
        assertFalse(merged?.wubi86Pinyin?.defaultAsciiMode ?: true)
    }

    @Test
    fun `missing custom field preserves built-in default`() {
        val builtIn = InputConfig(WubiPinyinInputConfig(defaultAsciiMode = true))
        val merged = KeysConfigHelper.mergeInputForTest(builtIn, InputConfig())
        assertEquals(true, merged?.wubi86Pinyin?.defaultAsciiMode)
    }
}
