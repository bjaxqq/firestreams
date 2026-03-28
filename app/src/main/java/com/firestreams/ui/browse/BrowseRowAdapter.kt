package com.firestreams.ui.browse

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.leanback.widget.HorizontalGridView
import androidx.recyclerview.widget.RecyclerView
import com.firestreams.R
import com.firestreams.data.Match

data class BrowseRow(val label: String, val items: List<MatchWithImage>)

class BrowseRowAdapter(
    private val onFocused: (MatchWithImage) -> Unit,
    private val onClick: (Match) -> Unit
) : RecyclerView.Adapter<BrowseRowAdapter.RowViewHolder>() {

    private var rows = listOf<BrowseRow>()

    fun setRows(newRows: List<BrowseRow>) {
        rows = newRows
        notifyDataSetChanged()
    }

    inner class RowViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val label: TextView = view.findViewById(R.id.row_label)
        val cardsList: HorizontalGridView = view.findViewById(R.id.cards_list)
        val cardAdapter = MatchCardTvAdapter(onFocused, onClick)

        init {
            cardsList.setNumRows(1)
            cardsList.adapter = cardAdapter
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RowViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_browse_row, parent, false)
        return RowViewHolder(view)
    }

    override fun onBindViewHolder(holder: RowViewHolder, position: Int) {
        val row = rows[position]
        holder.label.text = row.label
        holder.cardAdapter.setItems(row.items)
    }

    override fun getItemCount() = rows.size
}
