package com.firestreams.ui.player

import android.os.Bundle
import android.os.Handler
import android.os.Looper
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

    private val handler = Handler(Looper.getMainLooper())
    private val hideInfoBarRunnable = Runnable { fadeOutInfoBar() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        val title = intent.getStringExtra(EXTRA_MATCH_TITLE) ?: ""
        val isLive = intent.getBooleanExtra(EXTRA_IS_LIVE, true)

        findViewById<TextView>(R.id.player_title).text = title
        findViewById<View>(R.id.player_live_pill).visibility =
            if (isLive) View.VISIBLE else View.GONE

        val rawSources = intent.getStringArrayExtra(EXTRA_SOURCES) ?: emptyArray()
        val sources = rawSources.mapNotNull { s ->
            val parts = s.split(":")
            if (parts.size == 2) Source(parts[0], parts[1]) else null
        }

        viewModel = ViewModelProvider(this)[PlayerViewModel::class.java]

        viewModel.state.observe(this) { state ->
            when (state) {
                is PlayerState.Loading -> showLoading("Finding stream\u2026")
                is PlayerState.Reconnecting -> showLoading("Trying source ${state.attempt} of ${state.total}\u2026")
                is PlayerState.Ready -> {
                    hideLoading()
                    playStream(state.streamUrl, state.embedUrl)
                    flashInfoBar()
                }
                is PlayerState.Failed -> showError("No streams available")
            }
        }

        if (savedInstanceState == null) {
            val adminSource = sources.firstOrNull { it.source.lowercase() == "admin" }
            viewModel.start(sources, startFrom = adminSource)
        }
    }

    private fun showLoading(message: String) {
        val loadingView = findViewById<View>(R.id.player_loading_view)
        loadingView.visibility = View.VISIBLE
        findViewById<TextView>(R.id.player_status_text).text = message
    }

    private fun hideLoading() {
        findViewById<View>(R.id.player_loading_view).visibility = View.GONE
    }

    private fun showError(message: String) {
        val loadingView = findViewById<View>(R.id.player_loading_view)
        loadingView.visibility = View.VISIBLE
        // Hide the spinner, keep just the text
        loadingView.findViewById<View>(android.R.id.progress)?.visibility = View.GONE
        findViewById<TextView>(R.id.player_status_text).text = message
    }

    private fun flashInfoBar() {
        val bar = findViewById<View>(R.id.player_info_bar)
        bar.visibility = View.VISIBLE
        bar.animate().alpha(1f).setDuration(300).start()
        handler.removeCallbacks(hideInfoBarRunnable)
        handler.postDelayed(hideInfoBarRunnable, 4000)
    }

    private fun showInfoBar() {
        val bar = findViewById<View>(R.id.player_info_bar)
        bar.visibility = View.VISIBLE
        bar.animate().cancel()
        bar.alpha = 1f
        handler.removeCallbacks(hideInfoBarRunnable)
        handler.postDelayed(hideInfoBarRunnable, 4000)
    }

    private fun fadeOutInfoBar() {
        val bar = findViewById<View>(R.id.player_info_bar)
        bar.animate().alpha(0f).setDuration(600).withEndAction {
            bar.visibility = View.INVISIBLE
        }.start()
    }

    @OptIn(UnstableApi::class)
    private fun playStream(streamUrl: String, embedUrl: String) {
        player?.release()
        healthMonitor?.detach()

        val exo = ExoPlayer.Builder(this).build()
        player = exo

        healthMonitor = StreamHealthMonitor(exo) { health ->
            when (health) {
                HealthLevel.GOOD -> { /* clean video — no indicator */ }
                HealthLevel.BUFFERING -> { /* brief buffering is normal for live HLS */ }
                HealthLevel.BAD -> {
                    showLoading("Reconnecting\u2026")
                    viewModel.tryNextSource()
                }
            }
        }

        exo.addListener(object : Player.Listener {
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                viewModel.tryNextSource()
            }
        })

        // Update source label in info bar
        val sourceName = viewModel.currentSource()?.source ?: ""
        findViewById<TextView>(R.id.player_source_label).text = sourceName

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
        when (keyCode) {
            KeyEvent.KEYCODE_MENU -> {
                showSourcePicker()
                return true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                showSourcePicker()
                return true
            }
        }
        showInfoBar()
        return super.onKeyDown(keyCode, event)
    }

    private fun showSourcePicker() {
        val sources = viewModel.sources()
        if (sources.isEmpty()) return
        val active = viewModel.currentSource()?.source
        SourcePickerFragment.newInstance(sources, active) { chosen ->
            viewModel.forceSource(chosen)
        }.show(supportFragmentManager, "source_picker")
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(hideInfoBarRunnable)
        healthMonitor?.detach()
        player?.release()
    }

    companion object {
        const val EXTRA_MATCH_ID = "match_id"
        const val EXTRA_MATCH_TITLE = "match_title"
        const val EXTRA_IS_LIVE = "is_live"
        const val EXTRA_SOURCES = "sources"
    }
}
