package com.firestreams.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class SportsImageRepository(private val client: OkHttpClient) {

    private val cache = HashMap<String, String>() // team name -> url, or "" if not found

    suspend fun resolveImageUrl(match: Match): String? {
        if (!match.poster.isNullOrEmpty()) return match.poster
        for (team in extractTeams(match.title)) {
            val url = fetchTeamBadge(team)
            if (!url.isNullOrEmpty()) return url
        }
        return null
    }

    private fun extractTeams(title: String): List<String> {
        for (sep in listOf(" vs ", " vs. ", " v ", " @ ")) {
            if (sep in title) return title.split(sep, ignoreCase = true).map { it.trim() }
        }
        return emptyList()
    }

    private suspend fun fetchTeamBadge(teamName: String): String? {
        cache[teamName]?.let { return it.ifEmpty { null } }
        return withContext(Dispatchers.IO) {
            withTimeoutOrNull(3_000L) {
                try {
                    val encoded = teamName.replace(" ", "%20")
                    val body = client.newCall(
                        Request.Builder()
                            .url("https://www.thesportsdb.com/api/v1/json/3/searchteams.php?t=$encoded")
                            .build()
                    ).execute().body?.string() ?: ""
                    val teams = JSONObject(body).optJSONArray("teams")
                    if (teams == null || teams.length() == 0) {
                        cache[teamName] = ""; return@withTimeoutOrNull null
                    }
                    val t = teams.getJSONObject(0)
                    val url = t.optString("strTeamBadge").ifEmpty { null }
                        ?: t.optString("strStadiumThumb").ifEmpty { null }
                    cache[teamName] = url ?: ""
                    url
                } catch (e: Exception) {
                    cache[teamName] = ""; null
                }
            }
        }
    }
}
