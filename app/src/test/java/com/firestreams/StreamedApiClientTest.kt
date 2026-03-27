package com.firestreams

import com.firestreams.data.StreamedApiClient
import org.junit.Assert.*
import org.junit.Test

class StreamedApiClientTest {

    @Test
    fun parsesLiveMatchesJson() {
        val json = """[
            {"id":"abc","title":"Team A vs Team B","category":"soccer",
             "poster":null,"sources":[{"source":"delta","id":"match-123"}]}
        ]"""
        val matches = StreamedApiClient.parseLiveMatches(json)
        assertEquals(1, matches.size)
        assertEquals("Team A vs Team B", matches[0].title)
        assertEquals("delta", matches[0].sources[0].source)
        assertTrue(matches[0].isLive)
    }

    @Test
    fun parsesSportsJson() {
        val json = """[{"id":"soccer","name":"Soccer"},{"id":"nba","name":"NBA"}]"""
        val sports = StreamedApiClient.parseSports(json)
        assertEquals(2, sports.size)
        assertEquals("Soccer", sports[0].name)
    }

    @Test
    fun parsesMatchesBySportJson() {
        val json = """[
            {"id":"xyz","title":"Lakers vs Celtics","category":"nba",
             "poster":"https://example.com/img.jpg",
             "sources":[{"source":"alpha","id":"game-456"}]}
        ]"""
        val matches = StreamedApiClient.parseMatches(json)
        assertEquals("https://example.com/img.jpg", matches[0].poster)
        assertFalse(matches[0].isLive)
    }

    @Test
    fun parsesEmbedUrl() {
        val json = """[{"embedUrl":"https://embedsports.top/embed/delta/match-123/1","hls":null}]"""
        val url = StreamedApiClient.parseEmbedUrl(json)
        assertEquals("https://embedsports.top/embed/delta/match-123/1", url)
    }

    @Test
    fun returnsNullEmbedUrlForEmptyArray() {
        val url = StreamedApiClient.parseEmbedUrl("[]")
        assertNull(url)
    }
}
