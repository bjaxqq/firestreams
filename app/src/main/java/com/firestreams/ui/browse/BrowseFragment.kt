package com.firestreams.ui.browse

import android.content.Intent
import android.os.Bundle
import androidx.leanback.app.BrowseSupportFragment
import androidx.leanback.widget.*
import androidx.lifecycle.lifecycleScope
import com.firestreams.data.Match
import com.firestreams.data.StreamedApiClient
import com.firestreams.ui.details.DetailsActivity
import com.firestreams.ui.search.SearchActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

class BrowseFragment : BrowseSupportFragment() {

    private val api by lazy { StreamedApiClient(OkHttpClient()) }
    private val rowsAdapter = ArrayObjectAdapter(ListRowPresenter())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Firestreams"
        headersState = HEADERS_ENABLED
        isHeadersTransitionOnBackEnabled = true
        adapter = rowsAdapter

        setOnSearchClickedListener {
            startActivity(Intent(requireContext(), SearchActivity::class.java))
        }

        setOnItemViewClickedListener { _, item, _, _ ->
            if (item is Match) {
                val intent = Intent(requireContext(), DetailsActivity::class.java)
                intent.putExtra(DetailsActivity.EXTRA_MATCH_ID, item.id)
                intent.putExtra(DetailsActivity.EXTRA_MATCH_TITLE, item.title)
                intent.putExtra(DetailsActivity.EXTRA_MATCH_CATEGORY, item.category)
                intent.putExtra(DetailsActivity.EXTRA_MATCH_POSTER, item.poster)
                intent.putExtra(DetailsActivity.EXTRA_MATCH_IS_LIVE, item.isLive)
                intent.putExtra(DetailsActivity.EXTRA_MATCH_SOURCES,
                    item.sources.map { "${it.source}:${it.id}" }.toTypedArray())
                startActivity(intent)
            }
        }

        loadContent()
    }

    private fun loadContent() {
        lifecycleScope.launch {
            val (liveMatches, sports) = withContext(Dispatchers.IO) {
                api.getLiveMatches() to api.getSports()
            }

            rowsAdapter.clear()

            if (liveMatches.isNotEmpty()) {
                val liveAdapter = ArrayObjectAdapter(MatchCardPresenter())
                liveMatches.forEach { liveAdapter.add(it) }
                rowsAdapter.add(ListRow(HeaderItem("Live Now"), liveAdapter))
            }

            sports.forEach { sport ->
                val matches = withContext(Dispatchers.IO) { api.getMatchesBySport(sport.id) }
                if (matches.isNotEmpty()) {
                    val sportAdapter = ArrayObjectAdapter(MatchCardPresenter())
                    matches.forEach { sportAdapter.add(it) }
                    rowsAdapter.add(ListRow(HeaderItem(sport.name), sportAdapter))
                }
            }

            val upcoming = withContext(Dispatchers.IO) { api.getAllMatches() }
                .filter { !it.isLive }
            if (upcoming.isNotEmpty()) {
                val upcomingAdapter = ArrayObjectAdapter(MatchCardPresenter())
                upcoming.forEach { upcomingAdapter.add(it) }
                rowsAdapter.add(ListRow(HeaderItem("Upcoming"), upcomingAdapter))
            }
        }
    }
}
