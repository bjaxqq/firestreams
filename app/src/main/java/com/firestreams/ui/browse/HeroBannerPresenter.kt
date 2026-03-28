package com.firestreams.ui.browse

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.leanback.widget.Presenter
import com.bumptech.glide.Glide
import com.firestreams.R
import com.firestreams.data.Match

class HeroBannerPresenter(
    private val onWatchClicked: (Match) -> Unit
) : Presenter() {

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.hero_banner, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any) {
        val match = item as Match
        val view = viewHolder.view

        view.findViewById<TextView>(R.id.hero_title).text = match.title
        view.findViewById<TextView>(R.id.hero_category).text =
            match.category.replaceFirstChar { it.uppercase() }

        val liveBadge = view.findViewById<TextView>(R.id.hero_live_badge)
        liveBadge.visibility = if (match.isLive) View.VISIBLE else View.GONE

        val backdrop = view.findViewById<ImageView>(R.id.hero_backdrop)
        if (!match.poster.isNullOrEmpty()) {
            backdrop.setImageDrawable(null)
            backdrop.background = null
            Glide.with(view.context).load(match.poster).centerCrop().into(backdrop)
        } else {
            backdrop.setImageDrawable(null)
            backdrop.background = sportGradient(match.category)
        }

        val watchBtn = view.findViewById<TextView>(R.id.hero_watch_btn)
        watchBtn.text = if (match.isLive) "WATCH LIVE" else "WATCH"
        watchBtn.setOnClickListener { onWatchClicked(match) }
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        val view = viewHolder.view
        val backdrop = view.findViewById<ImageView>(R.id.hero_backdrop)
        Glide.with(view.context).clear(backdrop)
        backdrop.background = null
    }

    private fun sportGradient(category: String): GradientDrawable {
        val cat = category.lowercase()
        val (left, right) = when {
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
            else ->
                Color.argb(255, 18, 18, 28) to Color.argb(255, 10, 10, 18)
        }
        return GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(left, right))
    }
}
