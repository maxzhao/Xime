package com.kingzcheung.xime.service

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyCodeMapperTest {
    @Test
    fun mapsModifierKeysToRimeKeyCodes() {
        assertEquals(RIME_KEY_SHIFT_L, keyCodeToRimeModifierKeyCode(KeyEvent.KEYCODE_SHIFT_LEFT))
        assertEquals(RIME_KEY_SHIFT_R, keyCodeToRimeModifierKeyCode(KeyEvent.KEYCODE_SHIFT_RIGHT))
        assertEquals(RIME_KEY_CONTROL_L, keyCodeToRimeModifierKeyCode(KeyEvent.KEYCODE_CTRL_LEFT))
        assertEquals(RIME_KEY_CONTROL_R, keyCodeToRimeModifierKeyCode(KeyEvent.KEYCODE_CTRL_RIGHT))
        assertEquals(RIME_KEY_ALT_L, keyCodeToRimeModifierKeyCode(KeyEvent.KEYCODE_ALT_LEFT))
        assertEquals(RIME_KEY_ALT_R, keyCodeToRimeModifierKeyCode(KeyEvent.KEYCODE_ALT_RIGHT))
        assertEquals(RIME_KEY_SUPER_L, keyCodeToRimeModifierKeyCode(KeyEvent.KEYCODE_META_LEFT))
        assertEquals(RIME_KEY_SUPER_R, keyCodeToRimeModifierKeyCode(KeyEvent.KEYCODE_META_RIGHT))
        assertEquals(RIME_KEY_CAPS_LOCK, keyCodeToRimeModifierKeyCode(KeyEvent.KEYCODE_CAPS_LOCK))
        assertNull(keyCodeToRimeModifierKeyCode(KeyEvent.KEYCODE_A))
    }

    @Test
    fun convertsAndroidMetaStateToRimeMask() {
        val androidMask = KeyEvent.META_SHIFT_LEFT_ON or
            KeyEvent.META_CTRL_RIGHT_ON or
            KeyEvent.META_ALT_LEFT_ON or
            KeyEvent.META_META_RIGHT_ON or
            KeyEvent.META_CAPS_LOCK_ON or
            KeyEvent.META_NUM_LOCK_ON or
            KeyEvent.META_SCROLL_LOCK_ON

        val expected = RIME_SHIFT_MASK or RIME_CONTROL_MASK or RIME_ALT_MASK or
            RIME_SUPER_MASK or RIME_LOCK_MASK or RIME_MOD2_MASK or RIME_MOD3_MASK
        assertEquals(expected, androidMetaStateToRimeMask(androidMask))
        assertTrue(hasRimeChordModifier(expected))
        assertFalse(hasRimeChordModifier(RIME_LOCK_MASK or RIME_MOD2_MASK))
        assertTrue(hasRimeCommandModifier(expected))
        assertFalse(hasRimeCommandModifier(RIME_SHIFT_MASK or RIME_LOCK_MASK))
        assertEquals(0x40000000, RIME_RELEASE_MASK)
    }

    @Test
    fun mapsShiftedUsKeyboardSymbols() {
        assertEquals("!", keyCodeToKey(KeyEvent.KEYCODE_1, true))
        assertEquals("@", keyCodeToKey(KeyEvent.KEYCODE_2, true))
        assertEquals(")", keyCodeToKey(KeyEvent.KEYCODE_0, true))
        assertEquals("<", keyCodeToKey(KeyEvent.KEYCODE_COMMA, true))
        assertEquals(">", keyCodeToKey(KeyEvent.KEYCODE_PERIOD, true))
        assertEquals("?", keyCodeToKey(KeyEvent.KEYCODE_SLASH, true))
        assertEquals("|", keyCodeToKey(KeyEvent.KEYCODE_BACKSLASH, true))
        assertEquals("~", keyCodeToKey(KeyEvent.KEYCODE_GRAVE, true))
    }

    @Test
    fun mapsNavigationEditingAndFunctionKeysToRime() {
        assertEquals(0xff1b, keyCodeToRimeKeyCode(KeyEvent.KEYCODE_ESCAPE))
        assertEquals(0xff50, keyCodeToRimeKeyCode(KeyEvent.KEYCODE_MOVE_HOME))
        assertEquals(0xff57, keyCodeToRimeKeyCode(KeyEvent.KEYCODE_MOVE_END))
        assertEquals(0xff55, keyCodeToRimeKeyCode(KeyEvent.KEYCODE_PAGE_UP))
        assertEquals(0xff56, keyCodeToRimeKeyCode(KeyEvent.KEYCODE_PAGE_DOWN))
        assertEquals(0xffff, keyCodeToRimeKeyCode(KeyEvent.KEYCODE_FORWARD_DEL))
        assertEquals(0xff63, keyCodeToRimeKeyCode(KeyEvent.KEYCODE_INSERT))
        assertEquals(0xff51, keyCodeToRimeKeyCode(KeyEvent.KEYCODE_DPAD_LEFT))
        assertEquals(0xff54, keyCodeToRimeKeyCode(KeyEvent.KEYCODE_DPAD_DOWN))
        assertEquals(0xffbe, keyCodeToRimeKeyCode(KeyEvent.KEYCODE_F1))
        assertEquals(0xffc9, keyCodeToRimeKeyCode(KeyEvent.KEYCODE_F12))
        assertEquals(0xffd5, keyCodeToRimeKeyCode(KeyEvent.KEYCODE_F24))
        assertTrue(isRimeSpecialKey(KeyEvent.KEYCODE_ESCAPE))
        assertTrue(isRimeSpecialKey(KeyEvent.KEYCODE_F24))
        assertFalse(isRimeSpecialKey(KeyEvent.KEYCODE_A))
    }

    @Test
    fun mapsNumpadKeysAndCandidateIndexes() {
        assertEquals('0'.code, keyCodeToRimeKeyCode(KeyEvent.KEYCODE_NUMPAD_0))
        assertEquals('9'.code, keyCodeToRimeKeyCode(KeyEvent.KEYCODE_NUMPAD_9))
        assertEquals('+'.code, keyCodeToRimeKeyCode(KeyEvent.KEYCODE_NUMPAD_ADD))
        assertEquals('/'.code, keyCodeToRimeKeyCode(KeyEvent.KEYCODE_NUMPAD_DIVIDE))
        assertEquals(0xff0d, keyCodeToRimeKeyCode(KeyEvent.KEYCODE_NUMPAD_ENTER))
        assertEquals(0, candidateIndexForHardwareKey(KeyEvent.KEYCODE_NUMPAD_1))
        assertEquals(8, candidateIndexForHardwareKey(KeyEvent.KEYCODE_NUMPAD_9))
        assertEquals(9, candidateIndexForHardwareKey(KeyEvent.KEYCODE_NUMPAD_0))
        assertNull(candidateIndexForHardwareKey(KeyEvent.KEYCODE_NUMPAD_ADD))
    }
}
