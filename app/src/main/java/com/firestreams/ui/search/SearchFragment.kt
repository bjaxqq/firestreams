package com.firestreams.ui.search

import android.content.Intent
import android.os.Bundle
import androidx.leanback.app.SearchSupportFragment
import androidx.leanback.widget.*
import androidx.lifecycle.lifecycleScope
import com.firestreams.data.Match
import com.firestreams.data.StreamedApiClient
import com.firestreams.ui.browse.MatchCardPresenter
import com.firestreams.ui.details.DetailsActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

class SearchFragment : SearchSupportFragment(), SearchSupportFragment.SearchResultProvider {

    private val api by lazy { StreamedApiClient(OkHttpClient()) }
    private val rowsAdapter = ArrayObjectAdapter(ListRowPresenter())
    private var searchJob: Job? = null
    private var allMatches: List<Match> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setSearchResultProvider(this)
        setOnItemViewClickedListener { _, item, _, _ ->
            if (item is Match) {
                startActivity(Intent(requireContext(), DetailsActivity::class.java).apply {
                    putExtra(DetailsActivity.EXTRA_MATCH_ID, item.id)
                    putExtra(DetailsActivity.EXTRA_MATCH_TITLE, item.title)
                    putExtra(DetailsActivity.EXTRA_MATCH_CATEGORY, item.category)
                    putExtra(DetailsActivity.EXTRA_MATCH_POSTER, item.poster)
                    putExtra(DetailsActivity.EXTRA_MATCH_IS_LIVE, item.isLive)
                    putExtra(DetailsActivity.EXTRA_MATCH_SOURCES,
                        item.sources.map { "${it.source}:${it.id}" }.toTypedArray())
                })
            }
        }

        // Pre-load all matches for instant search
        lifecycleScope.launch {
            allMatches = withContext(Dispatchers.IO) {
                api.getLiveMatches() + api.getAllMatches()
            }
        }
    }

    override fun getResultsAdapter(): ObjectAdapter = rowsAdapter

    override fun onQueryTextChange(query: String): Boolean {
        searchJob?.cancel()
        searchJob = lifecycleScope.launch {
            delay(300) // debounce
            filterResults(query)
        }
        return true
    }

    override fun onQueryTextSubmit(query: String): Boolean {
        filterResults(query)
        return true
    }

    private fun filterResults(query: String) {
        rowsAdapter.clear()
        if (query.isBlank()) return
        val results = allMatches.filter {
            it.title.contains(query, ignoreCase = true) ||
            it.category.contains(query, ignoreCase = true)
        }
        if (results.isNotEmpty()) {
            val adapter = ArrayObjectAdapter(MatchCardPresenter())
            results.forEach { adapter.add(it) }
            rowsAdapter.add(ListRow(HeaderItem("Results"), adapter))
        }
    }
}
