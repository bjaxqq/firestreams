package com.firestreams.ui.player

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.firestreams.R
import com.firestreams.data.Source
import com.firestreams.data.StreamedApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

class SourcePickerFragment : DialogFragment() {

    private var onPicked: ((Source) -> Unit)? = null
    private val api by lazy { StreamedApiClient(OkHttpClient()) }
    private var adapter: SourceAdapter? = null

    init {
        setStyle(STYLE_NO_FRAME, 0)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, saved: Bundle?): View {
        val ctx = requireContext()
        val dp = resources.displayMetrics.density

        val sources = arguments?.getStringArray(ARG_SOURCES)
            ?.mapNotNull { s -> s.split(":").takeIf { it.size == 2 }?.let { Source(it[0], it[1]) } }
            ?: emptyList()
        val activeSource = arguments?.getString(ARG_ACTIVE)

        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.argb(230, 0, 0, 0))
        }

        // Header
        val header = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((28 * dp).toInt(), (32 * dp).toInt(), (28 * dp).toInt(), 0)
        }

        val label = TextView(ctx).apply {
            text = "STREAM SOURCE"
            textSize = 10f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(Color.argb(120, 255, 255, 255))
            letterSpacing = 0.14f
        }
        header.addView(label)

        val title = TextView(ctx).apply {
            text = activeSource?.replaceFirstChar { it.uppercase() } ?: "Select"
            textSize = 22f
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            setTextColor(Color.WHITE)
            letterSpacing = -0.01f
            setPadding(0, (6 * dp).toInt(), 0, (24 * dp).toInt())
        }
        header.addView(title)

        // Divider
        header.addView(View(ctx).apply {
            setBackgroundColor(Color.argb(30, 255, 255, 255))
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1))

        root.addView(header)

        // Source list
        val rv = RecyclerView(ctx).apply {
            layoutManager = LinearLayoutManager(ctx)
            overScrollMode = View.OVER_SCROLL_NEVER
            setPadding(0, (8 * dp).toInt(), 0, (8 * dp).toInt())
            clipToPadding = false
        }
        val sourceAdapter = SourceAdapter(sources, activeSource) { src ->
            onPicked?.invoke(src)
            dismiss()
        }
        adapter = sourceAdapter
        rv.adapter = sourceAdapter
        root.addView(rv, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        // Kick off status checks
        sources.forEachIndexed { index, src ->
            lifecycleScope.launch {
                val up = withContext(Dispatchers.IO) {
                    try { api.getEmbedUrl(src.source, src.id) != null } catch (e: Exception) { false }
                }
                sourceAdapter.setStatus(index, if (up) Status.UP else Status.DOWN)
            }
        }

        return root
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setWindowAnimations(R.style.Animation_SourcePicker)
            val dp = resources.displayMetrics.density
            setLayout((280 * dp).toInt(), ViewGroup.LayoutParams.MATCH_PARENT)
            setGravity(Gravity.END)
        }
    }

    enum class Status { CHECKING, UP, DOWN }

    private inner class SourceAdapter(
        private val sources: List<Source>,
        private val activeSource: String?,
        private val onPicked: (Source) -> Unit
    ) : RecyclerView.Adapter<SourceAdapter.VH>() {

        private val statuses = Array(sources.size) { Status.CHECKING }

        fun setStatus(index: Int, status: Status) {
            statuses[index] = status
            notifyItemChanged(index)
        }

        inner class VH(
            val row: LinearLayout,
            val accentBar: View,
            val statusDot: View,
            val nameLabel: TextView,
            val statusLabel: TextView
        ) : RecyclerView.ViewHolder(row)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val dp = parent.resources.displayMetrics.density
            val ctx = parent.context

            val accentBar = View(ctx).apply {
                layoutParams = LinearLayout.LayoutParams((3 * dp).toInt(), LinearLayout.LayoutParams.MATCH_PARENT)
                setBackgroundColor(Color.WHITE)
                visibility = View.INVISIBLE
            }

            val statusDot = View(ctx).apply {
                val size = (7 * dp).toInt()
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    marginStart = (20 * dp).toInt()
                    marginEnd = (12 * dp).toInt()
                }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.argb(100, 255, 255, 255))
                }
            }

            val nameLabel = TextView(ctx).apply {
                textSize = 15f
                typeface = Typeface.create("sans-serif", Typeface.NORMAL)
                setTextColor(Color.argb(180, 255, 255, 255))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    gravity = Gravity.CENTER_VERTICAL
                }
            }

            val statusLabel = TextView(ctx).apply {
                textSize = 10f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                letterSpacing = 0.06f
                setTextColor(Color.argb(80, 255, 255, 255))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    marginEnd = (20 * dp).toInt()
                }
            }

            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, (58 * dp).toInt()
                )
                isFocusable = true
                isFocusableInTouchMode = true
                addView(accentBar)
                addView(statusDot)
                addView(nameLabel)
                addView(statusLabel)
                setOnFocusChangeListener { _, hasFocus ->
                    if (hasFocus) {
                        setBackgroundColor(Color.argb(20, 255, 255, 255))
                        nameLabel.setTextColor(Color.WHITE)
                    } else {
                        background = null
                        nameLabel.setTextColor(Color.argb(180, 255, 255, 255))
                    }
                }
            }

            return VH(row, accentBar, statusDot, nameLabel, statusLabel)
        }

        override fun getItemCount() = sources.size

        override fun onBindViewHolder(holder: VH, pos: Int) {
            val src = sources[pos]
            holder.nameLabel.text = src.source.replaceFirstChar { it.uppercase() }

            val isActive = src.source.lowercase() == activeSource?.lowercase()
            if (isActive) {
                holder.accentBar.visibility = View.VISIBLE
                holder.nameLabel.setTextColor(Color.WHITE)
            }

            when (statuses[pos]) {
                Status.CHECKING -> {
                    setDotColor(holder.statusDot, Color.argb(80, 255, 255, 255))
                    holder.statusLabel.text = "CHECKING"
                }
                Status.UP -> {
                    setDotColor(holder.statusDot, Color.argb(255, 48, 209, 88))
                    holder.statusLabel.text = "ONLINE"
                }
                Status.DOWN -> {
                    setDotColor(holder.statusDot, Color.argb(255, 255, 59, 48))
                    holder.statusLabel.text = "OFFLINE"
                }
            }

            holder.row.setOnClickListener { onPicked(src) }
        }

        private fun setDotColor(dot: View, color: Int) {
            (dot.background as? GradientDrawable)?.setColor(color)
        }
    }

    companion object {
        private const val ARG_SOURCES = "sources"
        private const val ARG_ACTIVE = "active"

        fun newInstance(sources: List<Source>, activeSource: String?, onPicked: (Source) -> Unit): SourcePickerFragment {
            return SourcePickerFragment().apply {
                this.onPicked = onPicked
                arguments = Bundle().apply {
                    putStringArray(ARG_SOURCES, sources.map { "${it.source}:${it.id}" }.toTypedArray())
                    putString(ARG_ACTIVE, activeSource)
                }
            }
        }
    }
}
