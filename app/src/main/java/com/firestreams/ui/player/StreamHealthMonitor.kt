package com.firestreams.ui.player

import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

enum class HealthLevel { GOOD, BUFFERING, BAD }

class StreamHealthMonitor(
    private val player: ExoPlayer,
    private val onHealthChanged: (HealthLevel) -> Unit
) : Player.Listener {

    init { player.addListener(this) }

    override fun onPlaybackStateChanged(state: Int) {
        val health = when (state) {
            Player.STATE_READY -> if (player.playWhenReady) HealthLevel.GOOD else HealthLevel.BUFFERING
            Player.STATE_BUFFERING -> HealthLevel.BUFFERING
            Player.STATE_IDLE, Player.STATE_ENDED -> HealthLevel.BAD
            else -> HealthLevel.BAD
        }
        onHealthChanged(health)
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        if (isPlaying) onHealthChanged(HealthLevel.GOOD)
    }

    fun detach() { player.removeListener(this) }
}
