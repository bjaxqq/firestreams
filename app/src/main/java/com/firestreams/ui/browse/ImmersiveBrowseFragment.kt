package com.firestreams.ui.browse

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.firestreams.R
import com.firestreams.data.Match
import com.firestreams.data.SportsImageRepository
import com.firestreams.data.StreamedApiClient
import com.firestreams.ui.SoundManager
import com.firestreams.ui.player.PlayerActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

class ImmersiveBrowseFragment : Fragment() {

    private val client = OkHttpClient()
    private val api by lazy { StreamedApiClient(client) }
    private val imageRepo by lazy { SportsImageRepository(client) }

    private lateinit var bgImage: ImageView
    private lateinit var infoTitle: TextView
    private lateinit var infoCategory: TextView
    private lateinit var infoLiveBadge: TextView
    private lateinit var rowAdapter: BrowseRowAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_immersive_browse, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        bgImage = view.findViewById(R.id.bg_image)
        infoTitle = view.findViewById(R.id.info_title)
        infoCategory = view.findViewById(R.id.info_category)
        infoLiveBadge = view.findViewById(R.id.info_live_badge)

        rowAdapter = BrowseRowAdapter(
            onFocused = { mwi -> onCardFocused(mwi) },
            onClick = { match -> openDetails(match) }
        )

        view.findViewById<RecyclerView>(R.id.rows_recycler).apply {
            layoutManager = CenteringLayoutManager(requireContext())
            adapter = rowAdapter
        }

        loadContent()
    }

    private fun onCardFocused(mwi: MatchWithImage) {
        SoundManager.playFocus()

        infoTitle.text = mwi.match.title
        infoCategory.text = mwi.match.category.replaceFirstChar { it.uppercase() }
        infoLiveBadge.visibility = if (mwi.match.isLive) View.VISIBLE else View.GONE

        if (!mwi.imageUrl.isNullOrEmpty()) {
            Glide.with(this)
                .load(mwi.imageUrl)
                .centerCrop()
                .transition(DrawableTransitionOptions.withCrossFade(400))
                .into(bgImage)
        } else {
            bgImage.setImageDrawable(sportGradient(mwi.match.category))
        }
    }

    private fun openDetails(match: Match) {
        SoundManager.playSelect()
        startActivity(Intent(requireContext(), PlayerActivity::class.java).apply {
            putExtra(PlayerActivity.EXTRA_MATCH_TITLE, match.title)
            putExtra(PlayerActivity.EXTRA_IS_LIVE, match.isLive)
            putExtra(PlayerActivity.EXTRA_SOURCES, match.sources.map { "${it.source}:${it.id}" }.toTypedArray())
        })
    }

    private fun loadContent() {
        lifecycleScope.launch {
            val (liveMatches, sports) = withContext(Dispatchers.IO) {
                api.getLiveMatches() to api.getSports()
            }

            val rows = mutableListOf<BrowseRow>()

            // Featured: top 5 live matches
            if (liveMatches.isNotEmpty()) {
                val resolved = resolveImages(liveMatches.take(5))
                rows += BrowseRow("Featured", resolved)
                rowAdapter.setRows(rows.toList())
                resolved.firstOrNull()?.let { onCardFocused(it) }
                view?.findViewById<RecyclerView>(R.id.rows_recycler)?.post {
                    val rv = view?.findViewById<RecyclerView>(R.id.rows_recycler) ?: return@post
                    val lm = rv.layoutManager as? LinearLayoutManager ?: return@post
                    val halfRow = (106 * rv.context.resources.displayMetrics.density).toInt()
                    lm.scrollToPositionWithOffset(0, rv.height / 2 - rv.paddingTop - halfRow)
                }
            }

            // Live Now: remaining live matches
            val liveRest = liveMatches.drop(5)
            if (liveRest.isNotEmpty()) {
                rows += BrowseRow("Live Now", resolveImages(liveRest))
                rowAdapter.setRows(rows.toList())
            }

            // Per-sport rows
            for (sport in sports) {
                val matches = withContext(Dispatchers.IO) { api.getMatchesBySport(sport.id) }
                if (matches.isNotEmpty()) {
                    rows += BrowseRow(sport.name, resolveImages(matches))
                    rowAdapter.setRows(rows.toList())
                }
            }

            // Upcoming
            val upcoming = withContext(Dispatchers.IO) { api.getAllMatches() }
                .filter { !it.isLive }
                .take(20)
            if (upcoming.isNotEmpty()) {
                rows += BrowseRow("Upcoming", resolveImages(upcoming))
                rowAdapter.setRows(rows.toList())
            }
        }
    }

    private suspend fun resolveImages(matches: List<Match>): List<MatchWithImage> = coroutineScope {
        matches.map { match ->
            async { MatchWithImage(match, imageRepo.resolveImageUrl(match)) }
        }.map { it.await() }
    }

    private fun sportGradient(category: String): GradientDrawable {
        val cat = category.lowercase()
        val (top, bottom) = when {
            "soccer" in cat || ("football" in cat && "american" !in cat) ->
                Color.argb(255, 12, 48, 22) to Color.argb(255, 6, 26, 12)
            "basketball" in cat ->
                Color.argb(255, 80, 28, 4) to Color.argb(255, 42, 14, 2)
            "american-football" in cat || "nfl" in cat ->
                Color.argb(255, 12, 22, 60) to Color.argb(255, 6, 11, 32)
            "baseball" in cat ->
                Color.argb(255, 68, 12, 28) to Color.argb(255, 36, 6, 14)
            "hockey" in cat ->
                Color.argb(255, 6, 36, 66) to Color.argb(255, 3, 18, 36)
            "tennis" in cat ->
                Color.argb(255, 16, 46, 16) to Color.argb(255, 8, 26, 8)
            "mma" in cat || "boxing" in cat || "ufc" in cat ->
                Color.argb(255, 38, 8, 66) to Color.argb(255, 20, 4, 36)
            "motorsport" in cat || "f1" in cat ->
                Color.argb(255, 88, 4, 4) to Color.argb(255, 48, 2, 2)
            "rugby" in cat ->
                Color.argb(255, 28, 48, 4) to Color.argb(255, 14, 26, 2)
            "cricket" in cat ->
                Color.argb(255, 8, 48, 18) to Color.argb(255, 4, 26, 9)
            else ->
                Color.argb(255, 18, 18, 28) to Color.argb(255, 10, 10, 18)
        }
        return GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(top, bottom))
    }
}

private class CenteringLayoutManager(context: Context) : LinearLayoutManager(context) {
    override fun onRequestChildFocus(
        parent: RecyclerView, state: RecyclerView.State, child: View, focused: View?
    ): Boolean {
        val offset = (parent.height - child.height) / 2 - parent.paddingTop
        scrollToPositionWithOffset(getPosition(child), offset)
        return true
    }
}
