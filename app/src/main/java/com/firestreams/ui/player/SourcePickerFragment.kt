package com.firestreams.ui.player

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.firestreams.data.Source

class SourcePickerFragment : DialogFragment() {

    private var onPicked: ((Source) -> Unit)? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, saved: Bundle?): View {
        val rv = RecyclerView(requireContext()).apply {
            layoutManager = LinearLayoutManager(requireContext())
            setPadding(32, 32, 32, 32)
            setBackgroundColor(0xDD111111.toInt())
        }

        val sources = arguments?.getStringArray(ARG_SOURCES)
            ?.mapNotNull { s -> s.split(":").takeIf { it.size == 2 }?.let { Source(it[0], it[1]) } }
            ?: emptyList()

        rv.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                val tv = TextView(parent.context).apply {
                    textSize = 18f
                    setTextColor(0xFFFFFFFF.toInt())
                    setPadding(24, 24, 24, 24)
                    isFocusable = true
                    isFocusableInTouchMode = true
                }
                return object : RecyclerView.ViewHolder(tv) {}
            }
            override fun getItemCount() = sources.size
            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, pos: Int) {
                val src = sources[pos]
                (holder.itemView as TextView).text = src.source
                holder.itemView.setOnClickListener {
                    onPicked?.invoke(src)
                    dismiss()
                }
            }
        }
        return rv
    }

    companion object {
        private const val ARG_SOURCES = "sources"

        fun newInstance(sources: List<Source>, onPicked: (Source) -> Unit): SourcePickerFragment {
            return SourcePickerFragment().apply {
                this.onPicked = onPicked
                arguments = Bundle().apply {
                    putStringArray(ARG_SOURCES, sources.map { "${it.source}:${it.id}" }.toTypedArray())
                }
            }
        }
    }
}
