package com.firestreams.ui.browse

import android.content.Intent
import android.os.Bundle
import androidx.leanback.app.BrowseSupportFragment
import androidx.leanback.widget.*
import androidx.lifecycle.lifecycleScope
import com.firestreams.data.Match
import com.firestreams.data.StreamedApiClient
import com.firestreams.ui.details.DetailsActivity
import com.firestreams.ui.multistream.MultiStreamActivity
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

        setOnItemViewClickedListener { _, item, _, row ->
            // Check if this is the Multi-Stream button row
            if ((row as? ListRow)?.headerItem?.name == "Multi-Stream") {
                startActivity(Intent(requireContext(), MultiStreamActivity::class.java))
                return@setOnItemViewClickedListener
            }

            if (item is Match) {
                val multistreamSlot = requireActivity().intent.getIntExtra(
                    BrowseActivity.EXTRA_MULTISTREAM_SLOT, -1)

                if (multistreamSlot >= 0) {
                    // We were picking for a multistream slot — go back to MultiStreamActivity
                    val intent = Intent(requireContext(), MultiStreamActivity::class.java).apply {
                        putExtra(MultiStreamActivity.EXTRA_SLOT, multistreamSlot)
                        putExtra(MultiStreamActivity.EXTRA_TITLE, item.title)
                        putExtra(MultiStreamActivity.EXTRA_SOURCES,
                            item.sources.map { "${it.source}:${it.id}" }.toTypedArray())
                        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                    startActivity(intent)
                } else {
                    // Normal flow → details
                    val intent = Intent(requireContext(), DetailsActivity::class.java).apply {
                        putExtra(DetailsActivity.EXTRA_MATCH_ID, item.id)
                        putExtra(DetailsActivity.EXTRA_MATCH_TITLE, item.title)
                        putExtra(DetailsActivity.EXTRA_MATCH_CATEGORY, item.category)
                        putExtra(DetailsActivity.EXTRA_MATCH_POSTER, item.poster)
                        putExtra(DetailsActivity.EXTRA_MATCH_IS_LIVE, item.isLive)
                        putExtra(DetailsActivity.EXTRA_MATCH_SOURCES,
                            item.sources.map { "${it.source}:${it.id}" }.toTypedArray())
                    }
                    startActivity(intent)
                }
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

            // Multi-Stream entry row
            val multiStreamEntryAdapter = ArrayObjectAdapter(object : Presenter() {
                override fun onCreateViewHolder(parent: android.view.ViewGroup): ViewHolder {
                    val btn = android.widget.Button(parent.context).apply {
                        text = "Multi-Stream"
                        setTextColor(android.graphics.Color.WHITE)
                        setBackgroundColor(0xFF222222.toInt())
                        isFocusable = true
                    }
                    return ViewHolder(btn)
                }
                override fun onBindViewHolder(vh: ViewHolder, item: Any) {}
                override fun onUnbindViewHolder(vh: ViewHolder) {}
            })
            multiStreamEntryAdapter.add(Object())
            rowsAdapter.add(ListRow(HeaderItem("Multi-Stream"), multiStreamEntryAdapter))

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
