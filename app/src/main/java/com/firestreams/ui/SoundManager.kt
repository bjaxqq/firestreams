package com.firestreams.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.sin

object SoundManager {

    private const val SAMPLE_RATE = 44100

    fun init(@Suppress("UNUSED_PARAMETER") context: Context) {
        // Eagerly warm up AudioTrack to avoid first-play latency
    }

    fun playFocus() = playTone(frequency = 880.0, durationMs = 18, volume = 0.06f)

    fun playSelect() = playTone(frequency = 1046.5, durationMs = 55, volume = 0.12f)

    fun playBack() = playTone(frequency = 660.0, durationMs = 35, volume = 0.08f)

    private fun playTone(frequency: Double, durationMs: Int, volume: Float) {
        Thread {
            val numSamples = (SAMPLE_RATE * durationMs / 1000.0).toInt()
            val buffer = ShortArray(numSamples)
            for (i in 0 until numSamples) {
                val angle = 2.0 * PI * i * frequency / SAMPLE_RATE
                // Fade in/out to avoid clicks (10% of samples each side)
                val fade = when {
                    i < numSamples * 0.1 -> i / (numSamples * 0.1)
                    i > numSamples * 0.9 -> (numSamples - i) / (numSamples * 0.1)
                    else -> 1.0
                }
                buffer[i] = (sin(angle) * fade * volume * Short.MAX_VALUE).toInt().toShort()
            }

            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(buffer.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            track.write(buffer, 0, buffer.size)
            track.play()
            Thread.sleep(durationMs.toLong() + 10)
            track.stop()
            track.release()
        }.apply { isDaemon = true }.start()
    }
}
