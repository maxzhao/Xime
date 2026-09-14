package com.kingzcheung.xime.rime

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Native regression coverage for mixed Wubi/Pinyin source identity and four/five-key rules. */
@RunWith(AndroidJUnit4::class)
class WubiPinyinNativeIntegrationTest {
    private val engine = RimeEngine.getInstance()
    private var initialized = false

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val (userDir, sharedDir) = RimeConfigHelper.initializeRimeData(context)
        engine.initialize(userDir, sharedDir)
        initialized = true
        assertTrue("Rime session unavailable", engine.ensureSession())
        assertTrue("wubi86_pinyin schema unavailable", engine.switchSchema("wubi86_pinyin"))
        engine.setOption("ascii_mode", false)
        engine.clearComposition()
    }

    @After
    fun tearDown() {
        if (initialized) engine.destroy()
    }

    @Test
    fun immediateCompositionEditingKeysObserveThePrecedingLetter() {
        val backspace = typeThen('w', 0xff08)
        assertTrue(backspace.processed)
        assertTrue(backspace.inputText.isEmpty())

        val enter = typeThen('w', 0xff0d)
        assertTrue(enter.processed)
        assertEquals("w", enter.committedText)

        val space = typeThen('w', 0x20)
        assertTrue(space.processed)
        assertTrue(space.committedText.isNotEmpty())

        val left = typeThen('w', 0xff51)
        assertTrue(left.processed)
        assertEquals("w", left.inputText)
    }

    @Test
    fun sourceMetadataAndForwardDictionaryOrderArePreserved() {
        val result = type("fa")
        val reverse = result.candidates.firstOrNull { it.sourceType == "reverse_lookup" && it.text == "发" }
        assertEquals("ntcy", reverse?.fullWubiCode)
        assertEquals("lhng", engine.lookupText("囗"))
    }

    @Test
    fun uniqueAndAmbiguousFourCodeUsePreparedActiveMenu() {
        val unique = type("fcol")
        assertEquals("云烟", unique.committedText)
        assertTrue(unique.inputText.isEmpty())

        engine.clearComposition()
        val ambiguous = type("wgku")
        assertTrue(ambiguous.committedText.isEmpty())
        assertEquals("wgku", ambiguous.inputText)
        assertTrue(ambiguous.candidates.count { it.sourceType == "table" || it.sourceType == "user_table" } >= 2)
    }

    @Test
    fun fifthKeySplitsExactWubiButLongPinyinContinues() {
        type("wgku")
        val split = engine.dispatchKey('a'.code, 0)
        val splitResult = requireNotNull(split.result)
        assertTrue(splitResult.committedText.isNotEmpty())
        assertEquals("a", splitResult.inputText)

        for (pinyin in listOf("jiang", "guang")) {
            engine.clearComposition()
            val result = type(pinyin)
            assertTrue("$pinyin must not auto-commit at four keys", result.committedText.isEmpty())
            assertEquals(pinyin, result.inputText)
            assertTrue(result.candidates.any { it.sourceType == "reverse_lookup" })
        }
    }

    private fun typeThen(letter: Char, keycode: Int): RimeProcessResult {
        engine.clearComposition()
        val letterDispatch = engine.dispatchKey(letter.code, 0)
        assertTrue(letterDispatch is RimeKeyDispatch.Handled)
        val keyDispatch = engine.dispatchKey(keycode, 0)
        assertFalse(keyDispatch is RimeKeyDispatch.Unavailable)
        return requireNotNull(keyDispatch.result)
    }

    private fun type(input: String): RimeProcessResult {
        var result: RimeProcessResult? = null
        for (char in input) {
            val dispatch = engine.dispatchKey(char.code, 0)
            assertFalse("Rime unavailable while typing $input", dispatch is RimeKeyDispatch.Unavailable)
            result = requireNotNull(dispatch.result)
        }
        return requireNotNull(result)
    }
}
