package com.firestreams.ui.browse

import android.view.ViewGroup
import androidx.leanback.widget.ImageCardView
import androidx.leanback.widget.Presenter
import com.bumptech.glide.Glide
import com.firestreams.data.Match

class MatchCardPresenter : Presenter() {

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val card = ImageCardView(parent.context).apply {
            isFocusable = true
            isFocusableInTouchMode = true
            setMainImageDimensions(CARD_WIDTH, CARD_HEIGHT)
        }
        return ViewHolder(card)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any) {
        val match = item as Match
        val card = viewHolder.view as ImageCardView
        card.titleText = match.title
        card.contentText = match.category.replaceFirstChar { it.uppercase() }
        card.badgeImage = if (match.isLive) {
            card.context.getDrawable(com.firestreams.R.drawable.live_badge)
        } else null

        if (!match.poster.isNullOrEmpty()) {
            Glide.with(card.context).load(match.poster).centerCrop().into(card.mainImageView)
        } else {
            card.mainImageView.setImageDrawable(null)
        }
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        val card = viewHolder.view as ImageCardView
        card.badgeImage = null
        card.mainImageView.setImageDrawable(null)
    }

    companion object {
        private const val CARD_WIDTH = 320
        private const val CARD_HEIGHT = 180
    }
}
