package com.kingzcheung.xime.service

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HardwareOptionShortcutTest {
    @Test
    fun `Ctrl period toggles punctuation`() {
        assertEquals(
            HardwareOptionShortcut.PUNCTUATION,
            hardwareOptionShortcut(KeyEvent.KEYCODE_PERIOD, KeyEvent.META_CTRL_ON),
        )
    }

    @Test
    fun `Shift Space toggles character width`() {
        assertEquals(
            HardwareOptionShortcut.CHARACTER_WIDTH,
            hardwareOptionShortcut(KeyEvent.KEYCODE_SPACE, KeyEvent.META_SHIFT_ON),
        )
    }

    @Test
    fun `plain and over-modified keys do not match`() {
        assertNull(hardwareOptionShortcut(KeyEvent.KEYCODE_PERIOD, 0))
        assertNull(hardwareOptionShortcut(KeyEvent.KEYCODE_SPACE, 0))
        assertNull(
            hardwareOptionShortcut(
                KeyEvent.KEYCODE_SPACE,
                KeyEvent.META_SHIFT_ON or KeyEvent.META_CTRL_ON,
            )
        )
    }
}
