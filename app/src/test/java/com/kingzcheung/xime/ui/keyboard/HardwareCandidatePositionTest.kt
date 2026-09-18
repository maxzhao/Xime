package com.kingzcheung.xime.ui.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HardwareCandidatePositionTest {
    @Test
    fun `screen cursor is converted into nonzero candidate layer coordinates`() {
        val position = calculateHardwareCandidatePosition(
            containerWidth = 1000,
            containerHeight = 700,
            viewScreenX = 40,
            viewScreenY = 200,
            cursorScreenX = 540,
            cursorScreenTop = 300,
            cursorScreenBottom = 330,
            cursorVisible = true,
            cardWidth = 300,
            cardHeight = 120,
            margin = 10,
            gap = 20,
            fallbackTop = 60,
        )

        assertEquals(350, position.x)
        assertEquals(150, position.y)
    }

    @Test
    fun `card near every edge remains fully inside candidate layer`() {
        val anchors = listOf(
            Triple(-100, -100, -80),
            Triple(1400, -100, -80),
            Triple(-100, 900, 920),
            Triple(1400, 900, 920),
        )
        for ((x, top, bottom) in anchors) {
            val position = calculateHardwareCandidatePosition(
                containerWidth = 800,
                containerHeight = 600,
                viewScreenX = 100,
                viewScreenY = 150,
                cursorScreenX = x,
                cursorScreenTop = top,
                cursorScreenBottom = bottom,
                cursorVisible = true,
                cardWidth = 320,
                cardHeight = 180,
                margin = 8,
                gap = 16,
                fallbackTop = 60,
            )
            assertTrue(position.x >= 8)
            assertTrue(position.y >= 8)
            assertTrue(position.x + 320 <= 800 - 8)
            assertTrue(position.y + 180 <= 600 - 8)
        }
    }

    @Test
    fun `invalid cursor fallback is centered and constrained`() {
        val position = calculateHardwareCandidatePosition(
            containerWidth = 240,
            containerHeight = 120,
            viewScreenX = 0,
            viewScreenY = 0,
            cursorScreenX = 0,
            cursorScreenTop = 0,
            cursorScreenBottom = 0,
            cursorVisible = false,
            cardWidth = 300,
            cardHeight = 180,
            margin = 8,
            gap = 16,
            fallbackTop = 60,
        )

        assertEquals(0, position.x)
        assertEquals(0, position.y)
    }
}
