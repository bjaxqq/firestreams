package com.firestreams.ui.browse

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.firestreams.R
import com.firestreams.data.Match

data class MatchWithImage(val match: Match, val imageUrl: String?)

class MatchCardTvAdapter(
    private val onFocused: (MatchWithImage) -> Unit,
    private val onClick: (Match) -> Unit
) : RecyclerView.Adapter<MatchCardTvAdapter.CardViewHolder>() {

    private var items = listOf<MatchWithImage>()

    fun setItems(newItems: List<MatchWithImage>) {
        items = newItems
        notifyDataSetChanged()
    }

    inner class CardViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val frame: FrameLayout = view.findViewById(R.id.card_frame)
        val image: ImageView = view.findViewById(R.id.card_image)
        val liveBadge: TextView = view.findViewById(R.id.card_live_badge)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CardViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_match_card_tv, parent, false)
        return CardViewHolder(view)
    }

    override fun onBindViewHolder(holder: CardViewHolder, position: Int) {
        val mwi = items[position]
        val match = mwi.match

        holder.liveBadge.visibility = if (match.isLive) View.VISIBLE else View.GONE

        if (!mwi.imageUrl.isNullOrEmpty()) {
            Glide.with(holder.itemView.context)
                .load(mwi.imageUrl)
                .centerCrop()
                .placeholder(sportGradient(match.category))
                .into(holder.image)
        } else {
            Glide.with(holder.itemView.context).clear(holder.image)
            holder.image.setImageDrawable(sportGradient(match.category))
        }

        holder.frame.setOnFocusChangeListener { _, hasFocus ->
            val scale = if (hasFocus) 1.04f else 1.0f
            holder.frame.animate().scaleX(scale).scaleY(scale).setDuration(150).start()
            if (hasFocus) onFocused(mwi)
        }
        holder.frame.setOnClickListener { onClick(match) }
    }

    override fun onViewRecycled(holder: CardViewHolder) {
        Glide.with(holder.itemView.context).clear(holder.image)
    }

    override fun getItemCount() = items.size

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
