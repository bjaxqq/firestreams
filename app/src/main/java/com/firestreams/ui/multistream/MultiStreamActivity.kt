package com.firestreams.ui.multistream

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.widget.FrameLayout
import android.widget.TextView
import androidx.annotation.OptIn
import androidx.fragment.app.FragmentActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.ui.PlayerView
import com.firestreams.R
import com.firestreams.data.Source
import com.firestreams.data.SourceFallbackManager
import com.firestreams.data.StreamResolver
import com.firestreams.data.StreamedApiClient
import com.firestreams.ui.browse.BrowseActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

class MultiStreamActivity : FragmentActivity() {

    private val players = arrayOfNulls<ExoPlayer>(4)
    private val resolvers = arrayOfNulls<StreamResolver>(4)
    private var focusedCell = 0
    private val api = StreamedApiClient(OkHttpClient())

    private val cellIds = intArrayOf(R.id.cell_0, R.id.cell_1, R.id.cell_2, R.id.cell_3)
    private val playerIds = intArrayOf(R.id.player_0, R.id.player_1, R.id.player_2, R.id.player_3)
    private val labelIds = intArrayOf(R.id.label_0, R.id.label_1, R.id.label_2, R.id.label_3)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_multistream)

        // If launched with a match to fill a specific slot
        val slot = intent.getIntExtra(EXTRA_SLOT, -1)
        val rawSources = intent.getStringArrayExtra(EXTRA_SOURCES)
        val title = intent.getStringExtra(EXTRA_TITLE) ?: ""
        if (slot >= 0 && rawSources != null) {
            val sources = rawSources.mapNotNull { s ->
                s.split(":").takeIf { it.size == 2 }?.let { Source(it[0], it[1]) }
            }
            loadStream(slot, sources, title)
        }

        updateFocus(0)

        // Each empty cell click → go back to browse to pick a game for that slot
        for (i in 0..3) {
            val cell = findViewById<FrameLayout>(cellIds[i])
            cell.setOnClickListener {
                if (players[i] == null) {
                    val intent = Intent(this, BrowseActivity::class.java)
                    intent.putExtra(BrowseActivity.EXTRA_MULTISTREAM_SLOT, i)
                    startActivity(intent)
                } else {
                    updateFocus(i)
                }
            }
        }
    }

    private fun updateFocus(index: Int) {
        focusedCell = index
        for (i in 0..3) {
            val cell = findViewById<FrameLayout>(cellIds[i])
            cell.foreground = if (i == index) {
                getDrawable(android.R.drawable.list_selector_background)
            } else null
        }
    }

    @OptIn(UnstableApi::class)
    private fun loadStream(slot: Int, sources: List<Source>, title: String) {
        players[slot]?.release()
        resolvers[slot]?.destroy()

        val fb = SourceFallbackManager(sources)
        val label = findViewById<TextView>(labelIds[slot])
        label.text = title

        CoroutineScope(Dispatchers.Main).launch {
            val embedUrl = withContext(Dispatchers.IO) {
                api.getEmbedUrl(fb.current().source, fb.current().id)
            } ?: return@launch

            resolvers[slot] = StreamResolver(
                context = this@MultiStreamActivity,
                http = OkHttpClient(),
                onResolved = { streamUrl, embed ->
                    val exo = ExoPlayer.Builder(this@MultiStreamActivity).build()
                    players[slot] = exo
                    val ds = DefaultHttpDataSource.Factory()
                        .setDefaultRequestProperties(mapOf(
                            "Referer" to embed,
                            "Origin" to "https://embedsports.top"
                        ))
                    exo.setMediaSource(HlsMediaSource.Factory(ds).createMediaSource(MediaItem.fromUri(streamUrl)))
                    exo.prepare()
                    exo.playWhenReady = true
                    findViewById<PlayerView>(playerIds[slot]).player = exo
                    resolvers[slot]?.destroy()
                    resolvers[slot] = null
                },
                onFailed = {
                    if (fb.advance()) {
                        loadStream(slot, sources.drop(sources.indexOf(fb.current())), title)
                    }
                }
            )
            resolvers[slot]?.resolve(embedUrl)
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> { updateFocus((focusedCell - 1).coerceAtLeast(0)); return true }
            KeyEvent.KEYCODE_DPAD_RIGHT -> { updateFocus((focusedCell + 1).coerceAtMost(3)); return true }
            KeyEvent.KEYCODE_DPAD_UP -> { updateFocus((focusedCell - 2).coerceAtLeast(0)); return true }
            KeyEvent.KEYCODE_DPAD_DOWN -> { updateFocus((focusedCell + 2).coerceAtMost(3)); return true }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        super.onDestroy()
        players.forEach { it?.release() }
        resolvers.forEach { it?.destroy() }
    }

    companion object {
        const val EXTRA_SLOT = "slot"
        const val EXTRA_SOURCES = "sources"
        const val EXTRA_TITLE = "title"
    }
}
