package com.kingzcheung.xime.rime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RimeCandidateTest {
    @Test
    fun `Wubi annotation changes display only`() {
        val candidate = RimeCandidate(
            text = "发",
            comment = "〔拼音〕",
            sourceType = "reverse_lookup",
            fullWubiCode = "ntcy",
        )
        assertEquals("发(ntcy)", candidate.displayText)
        assertEquals("发", candidate.text)
    }

    @Test
    fun `pure Wubi candidate remains undecorated`() {
        val candidate = RimeCandidate("发", "", sourceType = "table")
        assertEquals("发", candidate.displayText)
    }

    @Test
    fun `user config bool state distinguishes missing from false`() {
        assertEquals(true, decodeUserConfigBoolState(1))
        assertEquals(false, decodeUserConfigBoolState(0))
        assertEquals(null, decodeUserConfigBoolState(-1))
    }

    @Test
    fun `only authoritative unhandled dispatch allows host fallback`() {
        val result = RimeProcessResult(false, "", "", "", emptyArray(), false, false, false)
        assertTrue(RimeKeyDispatch.Unhandled(result).result === result)
        assertTrue(RimeKeyDispatch.Unhandled(result).allowsHostFallback())
        assertTrue(RimeCandidateShortcutDispatch.Unhandled.allowsHostFallback())
        assertTrue(!RimeKeyDispatch.Handled(result).allowsHostFallback())
        assertTrue(!RimeKeyDispatch.Unavailable.allowsHostFallback())
        assertTrue(!RimeCandidateShortcutDispatch.Unavailable.allowsHostFallback())
        assertNotEquals(RimeKeyDispatch.Unhandled(result), RimeKeyDispatch.Unavailable)
        assertEquals(null, RimeKeyDispatch.Unavailable.result)
    }
}
