package com.firestreams

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray

class MainActivity : AppCompatActivity() {

    private val tag = "Firestreams"
    private val client = OkHttpClient()
    private var player: ExoPlayer? = null
    private var resolverWebView: WebView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        fetchAndPlay()
    }

    private fun fetchAndPlay() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val embedUrl = resolveEmbedUrl() ?: run {
                    setStatus("No live streams found")
                    return@launch
                }
                withContext(Dispatchers.Main) {
                    setStatus("Resolving stream...")
                    interceptStream(embedUrl)
                }
            } catch (e: Exception) {
                Log.e(tag, "fetchAndPlay error", e)
                setStatus("Error: ${e.message}")
            }
        }
    }

    private fun resolveEmbedUrl(): String? {
        // Step 1: get live matches
        val matchesJson = get("https://streamed.pk/api/matches/live") ?: return null
        val matches = JSONArray(matchesJson)

        for (i in 0 until matches.length()) {
            val match = matches.getJSONObject(i)
            val sources = match.optJSONArray("sources") ?: continue
            if (sources.length() == 0) continue

            val source = sources.getJSONObject(0)
            val sourceName = source.getString("source")
            val sourceId = source.getString("id")
            val title = match.optString("title", "Unknown")

            Log.d(tag, "Trying match: $title [$sourceName/$sourceId]")

            // Step 2: get stream for this source
            val streamsJson = get("https://streamed.pk/api/stream/$sourceName/$sourceId") ?: continue
            val streams = JSONArray(streamsJson)
            if (streams.length() == 0) continue

            val embedUrl = streams.getJSONObject(0).optString("embedUrl")
            if (embedUrl.isNotEmpty()) {
                Log.d(tag, "Got embedUrl: $embedUrl")
                return embedUrl
            }
        }
        return null
    }

    private fun get(url: String): String? {
        return try {
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { it.body?.string() }
        } catch (e: Exception) {
            Log.e(tag, "GET $url failed", e)
            null
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun interceptStream(embedUrl: String) {
        val webView = findViewById<WebView>(R.id.resolver_web_view).also { resolverWebView = it }
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        webView.settings.userAgentString =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36"

        webView.addJavascriptInterface(object {
            @android.webkit.JavascriptInterface
            fun onStreamUrl(url: String) {
                Log.d(tag, "JS bridge got stream URL: $url")
                Handler(Looper.getMainLooper()).post {
                    playStream(url, embedUrl)
                    webView.loadUrl("about:blank")
                    resolverWebView = null
                }
            }
        }, "Android")

        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? {
                val reqUrl = request.url.toString()

                // Catch direct .m3u8/.mpd requests as fallback
                if (reqUrl.contains(".m3u8") || reqUrl.contains(".mpd")) {
                    Log.d(tag, "Intercepted stream URL: $reqUrl")
                    Handler(Looper.getMainLooper()).post {
                        playStream(reqUrl, embedUrl)
                        webView.loadUrl("about:blank")
                        resolverWebView = null
                    }
                }

                // Patch the main HTML page to inject a fetch() hook before any scripts run
                if (reqUrl == embedUrl) {
                    try {
                        val reqBuilder = okhttp3.Request.Builder()
                            .url(reqUrl)
                            .header("Referer", "https://streamed.pk")
                            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36")
                        request.requestHeaders.forEach { (k, v) -> reqBuilder.header(k, v) }
                        val resp = client.newCall(reqBuilder.build()).execute()
                        val html = resp.body?.string() ?: return super.shouldInterceptRequest(view, request)
                        Log.d(tag, "HTML patch: fetched ${html.length} bytes, status ${resp.code}")
                        val hook = """<script>
// Hook jwplayer() setup to grab stream URL after WASM decoding
(function() {
    var _jwSetup = null;
    Object.defineProperty(window, 'jwplayer', {
        configurable: true,
        get: function() { return _jwSetup; },
        set: function(jw) {
            _jwSetup = function() {
                var inst = jw.apply(this, arguments);
                var origSetup = inst.setup;
                inst.setup = function(config) {
                    console.log('JW SETUP: ' + JSON.stringify(config).substring(0, 500));
                    var src = config && config.file;
                    if (!src && config && config.playlist) {
                        src = config.playlist[0] && (config.playlist[0].file || (config.playlist[0].sources && config.playlist[0].sources[0] && config.playlist[0].sources[0].file));
                    }
                    if (!src && config && config.sources) {
                        src = config.sources[0] && config.sources[0].file;
                    }
                    if (src) Android.onStreamUrl(src);
                    return origSetup.call(this, config);
                };
                return inst;
            };
        }
    });
})();
</script>"""
                        val patched = html.replace("<!doctype", "$hook<!doctype", ignoreCase = true)
                        return WebResourceResponse("text/html", "utf-8", patched.byteInputStream())
                    } catch (e: Exception) {
                        Log.e(tag, "HTML patch error", e)
                    }
                }

                return super.shouldInterceptRequest(view, request)
            }
        }

        webView.loadUrl(embedUrl, mapOf("Referer" to "https://streamed.pk"))
    }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    private fun playStream(streamUrl: String, embedUrl: String) {
        findViewById<TextView>(R.id.status_text).visibility = android.view.View.GONE

        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setDefaultRequestProperties(mapOf(
                "Referer" to embedUrl,
                "Origin" to "https://embedsports.top"
            ))

        val mediaSource = HlsMediaSource.Factory(dataSourceFactory)
            .createMediaSource(MediaItem.fromUri(streamUrl))

        player = ExoPlayer.Builder(this).build().also { exo ->
            findViewById<PlayerView>(R.id.player_view).player = exo
            exo.setMediaSource(mediaSource)
            exo.prepare()
            exo.playWhenReady = true
        }
    }

    private suspend fun setStatus(msg: String?) = withContext(Dispatchers.Main) {
        val tv = findViewById<TextView>(R.id.status_text)
        if (msg == null) {
            tv.visibility = android.view.View.GONE
        } else {
            tv.visibility = android.view.View.VISIBLE
            tv.text = msg
        }
    }

    private fun setStatus(msg: String) {
        runOnUiThread {
            val tv = findViewById<TextView>(R.id.status_text)
            tv.visibility = android.view.View.VISIBLE
            tv.text = msg
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        player?.release()
        resolverWebView?.loadUrl("about:blank")
    }
}
