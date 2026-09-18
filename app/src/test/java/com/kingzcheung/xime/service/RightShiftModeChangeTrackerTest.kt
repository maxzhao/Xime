package com.kingzcheung.xime.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RightShiftModeChangeTrackerTest {
    @Test
    fun `right Shift reports changed mode only on release`() {
        val tracker = RightShiftModeChangeTracker()

        assertNull(tracker.observe(RIME_KEY_SHIFT_R, isRelease = false, isAsciiMode = false))
        assertEquals(true, tracker.observe(RIME_KEY_SHIFT_R, isRelease = true, isAsciiMode = true))
    }

    @Test
    fun `right Shift without a mode change is silent`() {
        val tracker = RightShiftModeChangeTracker()

        assertNull(tracker.observe(RIME_KEY_SHIFT_R, isRelease = false, isAsciiMode = true))
        assertNull(tracker.observe(RIME_KEY_SHIFT_R, isRelease = true, isAsciiMode = true))
    }

    @Test
    fun `left Shift and unavailable results are silent`() {
        val tracker = RightShiftModeChangeTracker()

        assertNull(tracker.observe(RIME_KEY_SHIFT_L, isRelease = false, isAsciiMode = false))
        assertNull(tracker.observe(RIME_KEY_SHIFT_L, isRelease = true, isAsciiMode = true))
        assertNull(tracker.observe(RIME_KEY_SHIFT_R, isRelease = false, isAsciiMode = false))
        assertNull(tracker.observe(RIME_KEY_SHIFT_R, isRelease = true, isAsciiMode = null))
    }
}
