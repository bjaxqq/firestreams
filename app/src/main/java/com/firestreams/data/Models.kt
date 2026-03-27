package com.firestreams.data

data class Sport(
    val id: String,
    val name: String
)

data class Source(
    val source: String,
    val id: String
)

data class Match(
    val id: String,
    val title: String,
    val category: String,
    val poster: String?,
    val sources: List<Source>,
    val isLive: Boolean = true,
    val startTime: Long? = null  // epoch ms, null if live/unknown
)
