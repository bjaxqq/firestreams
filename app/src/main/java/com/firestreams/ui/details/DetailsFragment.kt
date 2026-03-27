package com.firestreams.ui.details

import android.content.Intent
import android.os.Bundle
import androidx.leanback.app.DetailsSupportFragment
import androidx.leanback.widget.*
import com.bumptech.glide.Glide
import com.firestreams.ui.player.PlayerActivity

class DetailsFragment : DetailsSupportFragment() {

    private lateinit var title: String
    private lateinit var category: String
    private var poster: String? = null
    private var isLive: Boolean = true
    private lateinit var sources: Array<String>
    private lateinit var matchId: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        matchId = requireActivity().intent.getStringExtra(DetailsActivity.EXTRA_MATCH_ID) ?: ""
        title = requireActivity().intent.getStringExtra(DetailsActivity.EXTRA_MATCH_TITLE) ?: ""
        category = requireActivity().intent.getStringExtra(DetailsActivity.EXTRA_MATCH_CATEGORY) ?: ""
        poster = requireActivity().intent.getStringExtra(DetailsActivity.EXTRA_MATCH_POSTER)
        isLive = requireActivity().intent.getBooleanExtra(DetailsActivity.EXTRA_MATCH_IS_LIVE, true)
        sources = requireActivity().intent.getStringArrayExtra(DetailsActivity.EXTRA_MATCH_SOURCES) ?: emptyArray()

        val row = buildDetailsRow()
        val rowPresenter = FullWidthDetailsOverviewRowPresenter(DetailsDescriptionPresenter())
        rowPresenter.setOnActionClickedListener { action ->
            if (action.id == ACTION_WATCH) {
                val intent = Intent(requireContext(), PlayerActivity::class.java).apply {
                    putExtra(PlayerActivity.EXTRA_MATCH_ID, matchId)
                    putExtra(PlayerActivity.EXTRA_MATCH_TITLE, title)
                    putExtra(PlayerActivity.EXTRA_SOURCES, sources)
                }
                startActivity(intent)
            }
        }
        val adapter = ArrayObjectAdapter(rowPresenter)
        adapter.add(row)
        this.adapter = adapter
    }

    private fun buildDetailsRow(): DetailsOverviewRow {
        val row = DetailsOverviewRow(MatchDetails(title, category, isLive))
        val actions = ArrayObjectAdapter()
        actions.add(Action(ACTION_WATCH, if (isLive) "Watch Live" else "Watch"))
        row.actionsAdapter = actions

        if (!poster.isNullOrEmpty()) {
            Glide.with(requireContext())
                .asBitmap()
                .load(poster)
                .into(object : com.bumptech.glide.request.target.CustomTarget<android.graphics.Bitmap>() {
                    override fun onResourceReady(res: android.graphics.Bitmap, t: com.bumptech.glide.request.transition.Transition<in android.graphics.Bitmap>?) {
                        row.imageDrawable = android.graphics.drawable.BitmapDrawable(resources, res)
                    }
                    override fun onLoadCleared(p: android.graphics.drawable.Drawable?) {}
                })
        }

        return row
    }

    data class MatchDetails(val title: String, val category: String, val isLive: Boolean)

    inner class DetailsDescriptionPresenter : AbstractDetailsDescriptionPresenter() {
        override fun onBindDescription(vh: ViewHolder, item: Any) {
            val details = item as MatchDetails
            vh.title.text = details.title
            vh.subtitle.text = details.category.replaceFirstChar { it.uppercase() }
            vh.body.text = if (details.isLive) "Live Now" else "Upcoming"
        }
    }

    companion object {
        private const val ACTION_WATCH = 1L
    }
}
