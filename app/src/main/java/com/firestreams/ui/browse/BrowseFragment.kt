package com.firestreams.ui.browse

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.leanback.app.BrowseSupportFragment
import androidx.leanback.widget.*
import androidx.lifecycle.lifecycleScope
import com.firestreams.data.Match
import com.firestreams.data.StreamedApiClient
import com.firestreams.ui.SoundManager
import com.firestreams.ui.details.DetailsActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

class BrowseFragment : BrowseSupportFragment() {

    private val api by lazy { StreamedApiClient(OkHttpClient()) }
    private val rowsAdapter = ArrayObjectAdapter(ListRowPresenter().also {
        it.shadowEnabled = false
    })

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = ""
        headersState = HEADERS_DISABLED
        isHeadersTransitionOnBackEnabled = false
        brandColor = android.graphics.Color.parseColor("#000000")
        adapter = rowsAdapter

        setOnItemViewSelectedListener { _, _, _, _ ->
            SoundManager.playFocus()
        }

        setOnItemViewClickedListener { _, item, _, _ ->
            when (item) {
                is Match -> {
                    SoundManager.playSelect()
                    openDetails(item)
                }
            }
        }

        loadContent()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Hide the Leanback search orb
        view.findViewById<View>(androidx.leanback.R.id.title_orb)?.visibility = View.GONE
    }

    private fun openDetails(match: Match) {
        val intent = Intent(requireContext(), DetailsActivity::class.java).apply {
            putExtra(DetailsActivity.EXTRA_MATCH_ID, match.id)
            putExtra(DetailsActivity.EXTRA_MATCH_TITLE, match.title)
            putExtra(DetailsActivity.EXTRA_MATCH_CATEGORY, match.category)
            putExtra(DetailsActivity.EXTRA_MATCH_POSTER, match.poster)
            putExtra(DetailsActivity.EXTRA_MATCH_IS_LIVE, match.isLive)
            putExtra(DetailsActivity.EXTRA_MATCH_SOURCES,
                match.sources.map { "${it.source}:${it.id}" }.toTypedArray())
        }
        startActivity(intent)
    }

    private fun loadContent() {
        lifecycleScope.launch {
            val (liveMatches, sports) = withContext(Dispatchers.IO) {
                api.getLiveMatches() to api.getSports()
            }

            rowsAdapter.clear()

            val heroMatch = liveMatches.firstOrNull()
            if (heroMatch != null) {
                val heroAdapter = ArrayObjectAdapter(HeroBannerPresenter { match ->
                    openDetails(match)
                })
                heroAdapter.add(heroMatch)
                rowsAdapter.add(ListRow(HeaderItem(""), heroAdapter))
            }

            val liveRest = if (liveMatches.size > 1) liveMatches.drop(1) else liveMatches
            if (liveRest.isNotEmpty()) {
                val liveAdapter = ArrayObjectAdapter(MatchCardPresenter())
                liveRest.forEach { liveAdapter.add(it) }
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
