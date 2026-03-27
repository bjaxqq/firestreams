package com.firestreams.data

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray

class StreamedApiClient(private val http: OkHttpClient) {

    companion object {
        private const val BASE = "https://streamed.pk/api"

        fun parseLiveMatches(json: String): List<Match> =
            parseMatchArray(json, isLive = true)

        fun parseSports(json: String): List<Sport> {
            val arr = JSONArray(json)
            return (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Sport(id = o.getString("id"), name = o.getString("name"))
            }
        }

        fun parseMatches(json: String): List<Match> =
            parseMatchArray(json, isLive = false)

        fun parseEmbedUrl(json: String): String? {
            val arr = JSONArray(json)
            if (arr.length() == 0) return null
            val url = arr.getJSONObject(0).optString("embedUrl")
            return url.ifEmpty { null }
        }

        private fun parseMatchArray(json: String, isLive: Boolean): List<Match> {
            val arr = JSONArray(json)
            return (0 until arr.length()).mapNotNull { i ->
                val o = arr.getJSONObject(i)
                val sourcesArr = o.optJSONArray("sources") ?: return@mapNotNull null
                val sources = (0 until sourcesArr.length()).map { j ->
                    val s = sourcesArr.getJSONObject(j)
                    Source(source = s.getString("source"), id = s.getString("id"))
                }
                if (sources.isEmpty()) return@mapNotNull null
                Match(
                    id = o.optString("id", ""),
                    title = o.optString("title", "Unknown"),
                    category = o.optString("category", ""),
                    poster = o.optString("poster").ifEmpty { null },
                    sources = sources,
                    isLive = isLive,
                    startTime = if (o.has("startTime")) o.getLong("startTime") else null
                )
            }
        }
    }

    suspend fun getLiveMatches(): List<Match> =
        get("$BASE/matches/live")?.let { parseLiveMatches(it) } ?: emptyList()

    suspend fun getSports(): List<Sport> =
        get("$BASE/sports")?.let { parseSports(it) } ?: emptyList()

    suspend fun getMatchesBySport(sport: String): List<Match> =
        get("$BASE/matches/$sport")?.let { parseMatches(it) } ?: emptyList()

    suspend fun getEmbedUrl(source: String, id: String): String? =
        get("$BASE/stream/$source/$id")?.let { parseEmbedUrl(it) }

    suspend fun getAllMatches(): List<Match> =
        get("$BASE/matches/all")?.let { parseMatches(it) } ?: emptyList()

    private suspend fun get(url: String): String? =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val req = Request.Builder().url(url).build()
                http.newCall(req).execute().use { it.body?.string() }
            } catch (e: Exception) {
                null
            }
        }
}
