package com.kingzcheung.xime.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SoftCompositionRouteTest {
    @Test
    fun `T9 Enter and Space retain dedicated route`() {
        assertFalse(route("enter", isT9 = true))
        assertFalse(route("space", isT9 = true))
    }

    @Test
    fun `ordinary keyboard Enter and Space use generic composition route`() {
        assertTrue(route("enter", isT9 = false))
        assertTrue(route("space", isT9 = false))
    }

    @Test
    fun `panels and owned forms bypass generic composition route`() {
        assertFalse(route("enter", isT9 = false, toolPanel = true))
        assertFalse(route("space", isT9 = false, quickSend = true))
        assertFalse(route("enter", isT9 = false, panelLayout = true))
    }

    private fun route(
        key: String,
        isT9: Boolean,
        toolPanel: Boolean = false,
        quickSend: Boolean = false,
        panelLayout: Boolean = false,
    ) = shouldUseGenericSoftCompositionRoute(
        key = key,
        isT9 = isT9,
        toolPanelInputFocused = toolPanel,
        showQuickSendForm = quickSend,
        isPanelLayout = panelLayout,
    )
}
