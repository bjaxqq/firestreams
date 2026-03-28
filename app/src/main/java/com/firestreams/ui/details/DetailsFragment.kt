package com.firestreams.ui.details

import android.content.Intent
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.firestreams.R
import com.firestreams.ui.player.PlayerActivity

class DetailsFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_details, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val activity = requireActivity()
        val title = activity.intent.getStringExtra(DetailsActivity.EXTRA_MATCH_TITLE) ?: ""
        val category = activity.intent.getStringExtra(DetailsActivity.EXTRA_MATCH_CATEGORY) ?: ""
        val poster = activity.intent.getStringExtra(DetailsActivity.EXTRA_MATCH_POSTER)
        val isLive = activity.intent.getBooleanExtra(DetailsActivity.EXTRA_MATCH_IS_LIVE, true)
        val matchId = activity.intent.getStringExtra(DetailsActivity.EXTRA_MATCH_ID) ?: ""
        val sources = activity.intent.getStringArrayExtra(DetailsActivity.EXTRA_MATCH_SOURCES) ?: emptyArray()

        view.findViewById<TextView>(R.id.details_title).text = title
        view.findViewById<TextView>(R.id.details_meta).text =
            category.replaceFirstChar { it.uppercase() }

        val liveBadge = view.findViewById<TextView>(R.id.details_live_badge)
        liveBadge.visibility = if (isLive) View.VISIBLE else View.GONE

        val watchBtn = view.findViewById<TextView>(R.id.details_watch_btn)
        watchBtn.text = if (isLive) "WATCH LIVE" else "WATCH"
        watchBtn.requestFocus()
        watchBtn.setOnClickListener { launchPlayer(matchId, title, isLive, sources) }
        watchBtn.setOnFocusChangeListener { v, focused ->
            val tv = v as TextView
            tv.setTextColor(
                if (focused) resources.getColor(R.color.bg_primary, null)
                else resources.getColor(R.color.text_primary, null)
            )
        }

        if (!poster.isNullOrEmpty()) {
            Glide.with(this)
                .asBitmap()
                .load(poster)
                .into(object : CustomTarget<android.graphics.Bitmap>() {
                    override fun onResourceReady(
                        resource: android.graphics.Bitmap,
                        transition: Transition<in android.graphics.Bitmap>?
                    ) {
                        view.findViewById<ImageView>(R.id.details_backdrop)
                            .setImageDrawable(BitmapDrawable(resources, resource))
                    }
                    override fun onLoadCleared(placeholder: android.graphics.drawable.Drawable?) {}
                })
        }
    }

    private fun launchPlayer(matchId: String, title: String, isLive: Boolean, sources: Array<String>) {
        val intent = Intent(requireContext(), PlayerActivity::class.java).apply {
            putExtra(PlayerActivity.EXTRA_MATCH_ID, matchId)
            putExtra(PlayerActivity.EXTRA_MATCH_TITLE, title)
            putExtra(PlayerActivity.EXTRA_IS_LIVE, isLive)
            putExtra(PlayerActivity.EXTRA_SOURCES, sources)
        }
        startActivity(intent)
    }
}
