package com.firestreams

import com.firestreams.data.Source
import com.firestreams.data.SourceFallbackManager
import org.junit.Assert.*
import org.junit.Test

class SourceFallbackManagerTest {

    @Test
    fun firstSourceIsCurrentOnInit() {
        val mgr = SourceFallbackManager(listOf(
            Source("delta", "id1"),
            Source("alpha", "id2")
        ))
        assertEquals(Source("delta", "id1"), mgr.current())
    }

    @Test
    fun advanceMovesToNextSource() {
        val mgr = SourceFallbackManager(listOf(
            Source("delta", "id1"),
            Source("alpha", "id2")
        ))
        assertTrue(mgr.advance())
        assertEquals(Source("alpha", "id2"), mgr.current())
    }

    @Test
    fun advanceReturnsFalseWhenExhausted() {
        val mgr = SourceFallbackManager(listOf(Source("delta", "id1")))
        assertFalse(mgr.advance())
    }

    @Test
    fun hasMoreReturnsTrueWhenSourcesRemain() {
        val mgr = SourceFallbackManager(listOf(
            Source("delta", "id1"),
            Source("alpha", "id2")
        ))
        assertTrue(mgr.hasMore())
        mgr.advance()
        assertFalse(mgr.hasMore())
    }

    @Test
    fun resetGoesBackToFirst() {
        val mgr = SourceFallbackManager(listOf(
            Source("delta", "id1"),
            Source("alpha", "id2")
        ))
        mgr.advance()
        mgr.reset()
        assertEquals(Source("delta", "id1"), mgr.current())
    }
}
