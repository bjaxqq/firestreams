package com.firestreams.data

class SourceFallbackManager(private val sources: List<Source>) {
    private var index = 0

    fun current(): Source = sources[index]

    /** Advance to next source. Returns false if already on last. */
    fun advance(): Boolean {
        if (index >= sources.size - 1) return false
        index++
        return true
    }

    fun hasMore(): Boolean = index < sources.size - 1

    fun reset() { index = 0 }
}
