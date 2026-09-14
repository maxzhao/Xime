package com.kingzcheung.xime.rime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CandidateShortcutResolverTest {
    private val engine = listOf(
        RimeCandidate("一", ""),
        RimeCandidate("二", ""),
        RimeCandidate("三", ""),
    )

    @Test
    fun `plugin insertion and reorder resolve displayed action`() {
        val transformed = listOf(
            RimeCandidate("插件", "") to CandidateSelectionRef.DirectCommit("插件"),
            engine[2] to CandidateSelectionRef.Engine(2),
            engine[0] to CandidateSelectionRef.Engine(0),
        )

        assertEquals(
            RimeCandidateSelection.DirectCommit("插件"),
            resolveDisplayedCandidateSelection(transformed, engine, 0),
        )
        assertEquals(
            RimeCandidateSelection.Engine(2),
            resolveDisplayedCandidateSelection(transformed, engine, 1),
        )
        assertEquals(
            RimeCandidateSelection.Engine(0),
            resolveDisplayedCandidateSelection(transformed, engine, 2),
        )
    }

    @Test
    fun `shrunk transform consumes missing shortcut without engine fallback`() {
        val transformed = listOf(engine[1] to CandidateSelectionRef.Engine(1))
        assertTrue(resolveDisplayedCandidateSelection(transformed, engine, 1) is RimeCandidateSelection.NoAction)
        assertTrue(resolveDisplayedCandidateSelection(transformed, engine, 2) is RimeCandidateSelection.NoAction)
    }

    @Test
    fun `untransformed display maps directly to current engine page`() {
        assertEquals(
            RimeCandidateSelection.Engine(1),
            resolveDisplayedCandidateSelection(null, engine, 1),
        )
        assertTrue(resolveDisplayedCandidateSelection(null, engine, 3) is RimeCandidateSelection.NoAction)
    }
}
