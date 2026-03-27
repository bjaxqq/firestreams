package com.firestreams.data

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import okhttp3.OkHttpClient

/**
 * Loads an embedUrl in a WebView, injects a hook to intercept the JW Player setup call,
 * and returns the resolved .m3u8 URL via [onResolved]. Call [destroy] when done.
 *
 * Must be created and used on the main thread.
 */
class StreamResolver(
    context: Context,
    private val http: OkHttpClient,
    private val onResolved: (streamUrl: String, embedUrl: String) -> Unit,
    private val onFailed: () -> Unit
) {
    private val webView: WebView = WebView(context)
    private val handler = Handler(Looper.getMainLooper())
    private var resolved = false

    private val timeoutRunnable = Runnable {
        if (!resolved) onFailed()
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun resolve(embedUrl: String) {
        resolved = false
        handler.postDelayed(timeoutRunnable, 20_000)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36"
        }

        webView.addJavascriptInterface(object {
            @android.webkit.JavascriptInterface
            fun onStreamUrl(url: String) {
                if (resolved) return
                resolved = true
                handler.removeCallbacks(timeoutRunnable)
                handler.post { onResolved(url, embedUrl) }
            }
        }, "Android")

        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? {
                val reqUrl = request.url.toString()

                if ((reqUrl.contains(".m3u8") || reqUrl.contains(".mpd")) && !resolved) {
                    resolved = true
                    handler.removeCallbacks(timeoutRunnable)
                    handler.post { onResolved(reqUrl, embedUrl) }
                }

                if (reqUrl == embedUrl) {
                    try {
                        val resp = http.newCall(
                            okhttp3.Request.Builder()
                                .url(reqUrl)
                                .header("Referer", "https://streamed.pk")
                                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36")
                                .apply { request.requestHeaders.forEach { (k, v) -> header(k, v) } }
                                .build()
                        ).execute()
                        val html = resp.body?.string() ?: return super.shouldInterceptRequest(view, request)
                        val patched = JW_HOOK + html
                        return WebResourceResponse("text/html", "utf-8", patched.byteInputStream())
                    } catch (e: Exception) {
                        return super.shouldInterceptRequest(view, request)
                    }
                }

                return super.shouldInterceptRequest(view, request)
            }
        }

        webView.loadUrl(embedUrl, mapOf("Referer" to "https://streamed.pk"))
    }

    fun destroy() {
        handler.removeCallbacks(timeoutRunnable)
        webView.loadUrl("about:blank")
        webView.destroy()
    }

    companion object {
        private val JW_HOOK = """<script>
(function() {
    var _jw = null;
    Object.defineProperty(window, 'jwplayer', {
        configurable: true,
        get: function() { return _jw; },
        set: function(jw) {
            _jw = function() {
                var inst = jw.apply(this, arguments);
                var orig = inst.setup;
                inst.setup = function(cfg) {
                    var src = (cfg && cfg.file)
                        || (cfg && cfg.playlist && cfg.playlist[0] && (cfg.playlist[0].file || (cfg.playlist[0].sources && cfg.playlist[0].sources[0] && cfg.playlist[0].sources[0].file)))
                        || (cfg && cfg.sources && cfg.sources[0] && cfg.sources[0].file);
                    if (src) Android.onStreamUrl(src);
                    return orig.call(this, cfg);
                };
                return inst;
            };
        }
    });
})();
</script>"""
    }
}
