package com.firestreams.ui.player

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.firestreams.data.Source
import com.firestreams.data.SourceFallbackManager
import com.firestreams.data.StreamedApiClient
import com.firestreams.data.StreamResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

sealed class PlayerState {
    object Loading : PlayerState()
    data class Ready(val streamUrl: String, val embedUrl: String) : PlayerState()
    data class Reconnecting(val attempt: Int, val total: Int) : PlayerState()
    object Failed : PlayerState()
}

class PlayerViewModel(app: Application) : AndroidViewModel(app) {

    private val tag = "PlayerViewModel"
    private val api = StreamedApiClient(OkHttpClient())
    private var resolver: StreamResolver? = null
    private var fallback: SourceFallbackManager? = null
    private var _allSources: List<Source> = emptyList()

    private val _state = MutableLiveData<PlayerState>(PlayerState.Loading)
    val state: LiveData<PlayerState> = _state

    fun start(allSources: List<Source>, startFrom: Source? = null) {
        _allSources = allSources
        val ordered = if (startFrom != null) {
            val idx = allSources.indexOf(startFrom)
            if (idx >= 0) allSources.drop(idx) + allSources.take(idx) else allSources
        } else allSources
        fallback = SourceFallbackManager(ordered)
        tryCurrentSource()
    }

    fun tryNextSource() {
        val fb = fallback ?: return
        if (!fb.advance()) {
            _state.value = PlayerState.Failed
            return
        }
        val attempt = _allSources.indexOf(fb.current()) + 1
        _state.value = PlayerState.Reconnecting(attempt, _allSources.size)
        tryCurrentSource()
    }

    fun forceSource(source: Source) {
        val idx = _allSources.indexOf(source)
        if (idx < 0) return
        fallback = SourceFallbackManager(_allSources.drop(idx) + _allSources.take(idx))
        _state.value = PlayerState.Loading
        tryCurrentSource()
    }

    fun sources(): List<Source> = _allSources

    private fun tryCurrentSource() {
        val src = fallback?.current() ?: run {
            _state.value = PlayerState.Failed
            return
        }
        _state.value = PlayerState.Loading
        resolver?.destroy()

        viewModelScope.launch(Dispatchers.Main) {
            val embedUrl = withContext(Dispatchers.IO) {
                api.getEmbedUrl(src.source, src.id)
            } ?: run {
                Log.d(tag, "No embedUrl for ${src.source}/${src.id}, trying next")
                tryNextSource()
                return@launch
            }

            resolver = StreamResolver(
                context = getApplication(),
                http = OkHttpClient(),
                onResolved = { streamUrl, embed ->
                    _state.value = PlayerState.Ready(streamUrl, embed)
                },
                onFailed = {
                    Log.d(tag, "Resolver timed out for ${src.source}, trying next")
                    tryNextSource()
                }
            )
            resolver?.resolve(embedUrl)
        }
    }

    override fun onCleared() {
        resolver?.destroy()
    }
}
