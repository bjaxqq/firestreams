package com.firestreams.ui.player

import android.graphics.Color
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.TextView
import androidx.annotation.OptIn
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.ui.PlayerView
import com.firestreams.R
import com.firestreams.data.Source

class PlayerActivity : FragmentActivity() {

    private var player: ExoPlayer? = null
    private var healthMonitor: StreamHealthMonitor? = null
    private lateinit var viewModel: PlayerViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        val rawSources = intent.getStringArrayExtra(EXTRA_SOURCES) ?: emptyArray()
        val sources = rawSources.mapNotNull { s ->
            val parts = s.split(":")
            if (parts.size == 2) Source(parts[0], parts[1]) else null
        }

        viewModel = ViewModelProvider(this)[PlayerViewModel::class.java]

        viewModel.state.observe(this) { state ->
            when (state) {
                is PlayerState.Loading -> showOverlay("Loading stream...")
                is PlayerState.Reconnecting -> showOverlay("Reconnecting... (${state.attempt}/${state.total})")
                is PlayerState.Ready -> {
                    hideOverlay()
                    playStream(state.streamUrl, state.embedUrl)
                }
                is PlayerState.Failed -> showOverlay("Stream unavailable")
            }
        }

        if (savedInstanceState == null) {
            viewModel.start(sources)
        }
    }

    @OptIn(UnstableApi::class)
    private fun playStream(streamUrl: String, embedUrl: String) {
        player?.release()
        healthMonitor?.detach()

        val exo = ExoPlayer.Builder(this).build()
        player = exo

        healthMonitor = StreamHealthMonitor(exo) { health ->
            val dot = findViewById<View>(R.id.health_dot)
            dot.setBackgroundColor(when (health) {
                HealthLevel.GOOD -> Color.GREEN
                HealthLevel.BUFFERING -> Color.YELLOW
                HealthLevel.BAD -> Color.RED
            })
            if (health == HealthLevel.BAD) {
                viewModel.tryNextSource()
            }
        }

        exo.addListener(object : Player.Listener {
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                viewModel.tryNextSource()
            }
        })

        val dataSource = DefaultHttpDataSource.Factory()
            .setDefaultRequestProperties(mapOf(
                "Referer" to embedUrl,
                "Origin" to "https://embedsports.top"
            ))
        val mediaSource = HlsMediaSource.Factory(dataSource)
            .createMediaSource(MediaItem.fromUri(streamUrl))

        findViewById<PlayerView>(R.id.player_view).player = exo
        exo.setMediaSource(mediaSource)
        exo.prepare()
        exo.playWhenReady = true
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_BUTTON_SELECT) {
            showSourcePicker()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun showSourcePicker() {
        val sources = viewModel.sources()
        if (sources.isEmpty()) return
        SourcePickerFragment.newInstance(sources) { chosen ->
            viewModel.forceSource(chosen)
        }.show(supportFragmentManager, "source_picker")
    }

    private fun showOverlay(msg: String) {
        findViewById<TextView>(R.id.status_overlay).apply {
            text = msg
            visibility = View.VISIBLE
        }
    }

    private fun hideOverlay() {
        findViewById<TextView>(R.id.status_overlay).visibility = View.GONE
    }

    override fun onDestroy() {
        super.onDestroy()
        healthMonitor?.detach()
        player?.release()
    }

    companion object {
        const val EXTRA_MATCH_ID = "match_id"
        const val EXTRA_MATCH_TITLE = "match_title"
        const val EXTRA_SOURCES = "sources"
    }
}
