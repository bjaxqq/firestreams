# Firestreams Full TV App Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Transform the single-screen PoC into a full Fire TV sports streaming app with category browsing, details screen, auto-failover playback, multi-stream 2x2 grid, search, and upcoming games.

**Architecture:** Leanback BrowseFragment for the home screen (sport rows + Live Now), separate Activities for Details/Player/Search/MultiStream, shared data layer (API client + stream resolver) used by all screens. Stream resolution (WebView JW-hook intercept) is extracted from MainActivity into a reusable `StreamResolver` with a `SourceFallbackManager` handling auto-failover.

**Tech Stack:** Kotlin, Leanback 1.2.0, Media3/ExoPlayer 1.5.1, OkHttp 4.12.0, Coroutines 1.8.1, Glide 4.16.0, ViewModel/LiveData (lifecycle-ktx 2.8.7)

---

## File Map

```
app/src/main/java/com/firestreams/
  data/
    Models.kt                        # Match, Sport, Source data classes
    StreamedApiClient.kt             # All streamed.pk API calls
    StreamResolver.kt                # WebView JW-hook intercept (extracted from MainActivity)
    SourceFallbackManager.kt         # Tries sources in order, drives failover
  ui/
    browse/
      BrowseActivity.kt              # Container, sets up BrowseFragment
      BrowseFragment.kt              # Leanback BrowseFragment: Live Now + sport rows
      MatchCardPresenter.kt          # ImageCardView: title + sport label + LIVE badge
    details/
      DetailsActivity.kt             # Receives Match via intent, hosts DetailsFragment
      DetailsFragment.kt             # Leanback DetailsFragment: poster, title, Watch button
    player/
      PlayerActivity.kt              # Receives Match + source list via intent
      PlayerViewModel.kt             # Resolves stream, drives failover, exposes state
      StreamHealthMonitor.kt         # Watches ExoPlayer buffer, emits health level
      SourcePickerFragment.kt        # Overlay dialog: list of sources, d-pad selectable
    multistream/
      MultiStreamActivity.kt         # 2x2 grid of PlayerViews, d-pad to focus/fullscreen
    search/
      SearchActivity.kt              # Hosts Leanback SearchFragment
      SearchFragment.kt              # Leanback SearchFragment, queries API
  MainActivity.kt                    # REPLACED — becomes a redirect to BrowseActivity

app/src/main/res/
  layout/
    activity_browse.xml              # FrameLayout hosting BrowseFragment
    activity_details.xml             # FrameLayout hosting DetailsFragment
    activity_player.xml              # PlayerView + health dot + status overlay
    activity_multistream.xml         # 2x2 grid of PlayerViews
    fragment_source_picker.xml       # RecyclerView list of sources
    item_source.xml                  # Single source row in picker
  values/
    strings.xml                      # App strings
    colors.xml                       # LIVE badge red, health dot colors

app/src/test/java/com/firestreams/
  StreamedApiClientTest.kt           # JSON parsing unit tests
  SourceFallbackManagerTest.kt       # Fallback logic unit tests
```

---

## Task 1: Add dependencies

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: Add new versions to `gradle/libs.versions.toml`**

```toml
[versions]
# add after existing versions:
leanback = "1.2.0-alpha02"
glide = "4.16.0"
lifecycle = "2.8.7"

[libraries]
# add after existing libraries:
leanback = { group = "androidx.leanback", name = "leanback", version.ref = "leanback" }
glide = { group = "com.github.bumptech.glide", name = "glide", version.ref = "glide" }
lifecycle-viewmodel = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-ktx", version.ref = "lifecycle" }
lifecycle-runtime = { group = "androidx.lifecycle", name = "lifecycle-runtime-ktx", version.ref = "lifecycle" }
```

- [ ] **Step 2: Add to `app/build.gradle.kts` dependencies block**

```kotlin
implementation(libs.leanback)
implementation(libs.glide)
implementation(libs.lifecycle.viewmodel)
implementation(libs.lifecycle.runtime)
```

- [ ] **Step 3: Sync and verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts
git commit -m "build: add leanback, glide, lifecycle dependencies"
```

---

## Task 2: Data models

**Files:**
- Create: `app/src/main/java/com/firestreams/data/Models.kt`

- [ ] **Step 1: Create `data/Models.kt`**

```kotlin
package com.firestreams.data

data class Sport(
    val id: String,
    val name: String
)

data class Source(
    val source: String,
    val id: String
)

data class Match(
    val id: String,
    val title: String,
    val category: String,
    val poster: String?,
    val sources: List<Source>,
    val isLive: Boolean = true,
    val startTime: Long? = null  // epoch ms, null if live/unknown
)
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/firestreams/data/Models.kt
git commit -m "feat: add data models (Match, Sport, Source)"
```

---

## Task 3: API client with unit tests

**Files:**
- Create: `app/src/main/java/com/firestreams/data/StreamedApiClient.kt`
- Create: `app/src/test/java/com/firestreams/StreamedApiClientTest.kt`

- [ ] **Step 1: Write failing unit tests for JSON parsing**

Create `app/src/test/java/com/firestreams/StreamedApiClientTest.kt`:

```kotlin
package com.firestreams

import com.firestreams.data.StreamedApiClient
import org.junit.Assert.*
import org.junit.Test

class StreamedApiClientTest {

    @Test
    fun parsesLiveMatchesJson() {
        val json = """[
            {"id":"abc","title":"Team A vs Team B","category":"soccer",
             "poster":null,"sources":[{"source":"delta","id":"match-123"}]}
        ]"""
        val matches = StreamedApiClient.parseLiveMatches(json)
        assertEquals(1, matches.size)
        assertEquals("Team A vs Team B", matches[0].title)
        assertEquals("delta", matches[0].sources[0].source)
        assertTrue(matches[0].isLive)
    }

    @Test
    fun parsesSportsJson() {
        val json = """[{"id":"soccer","name":"Soccer"},{"id":"nba","name":"NBA"}]"""
        val sports = StreamedApiClient.parseSports(json)
        assertEquals(2, sports.size)
        assertEquals("Soccer", sports[0].name)
    }

    @Test
    fun parsesMatchesBySportJson() {
        val json = """[
            {"id":"xyz","title":"Lakers vs Celtics","category":"nba",
             "poster":"https://example.com/img.jpg",
             "sources":[{"source":"alpha","id":"game-456"}]}
        ]"""
        val matches = StreamedApiClient.parseMatches(json)
        assertEquals("https://example.com/img.jpg", matches[0].poster)
        assertFalse(matches[0].isLive)  // parseMatches sets isLive=false (non-live endpoint)
    }

    @Test
    fun parsesEmbedUrl() {
        val json = """[{"embedUrl":"https://embedsports.top/embed/delta/match-123/1","hls":null}]"""
        val url = StreamedApiClient.parseEmbedUrl(json)
        assertEquals("https://embedsports.top/embed/delta/match-123/1", url)
    }

    @Test
    fun returnsNullEmbedUrlForEmptyArray() {
        val url = StreamedApiClient.parseEmbedUrl("[]")
        assertNull(url)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests "com.firestreams.StreamedApiClientTest"`
Expected: FAIL — `StreamedApiClient` not yet defined

- [ ] **Step 3: Create `data/StreamedApiClient.kt`**

```kotlin
package com.firestreams.data

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

class StreamedApiClient(private val http: OkHttpClient) {

    companion object {
        private const val BASE = "https://streamed.pk/api"

        fun parseLiveMatches(json: String): List<Match> =
            parseMatchArray(json, isLive = true)

        fun parseSports(json: String): List<Sport> {
            val arr = JSONArray(json)
            return (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Sport(id = o.getString("id"), name = o.getString("name"))
            }
        }

        fun parseMatches(json: String): List<Match> =
            parseMatchArray(json, isLive = false)

        fun parseEmbedUrl(json: String): String? {
            val arr = JSONArray(json)
            if (arr.length() == 0) return null
            val url = arr.getJSONObject(0).optString("embedUrl")
            return url.ifEmpty { null }
        }

        private fun parseMatchArray(json: String, isLive: Boolean): List<Match> {
            val arr = JSONArray(json)
            return (0 until arr.length()).mapNotNull { i ->
                val o = arr.getJSONObject(i)
                val sourcesArr = o.optJSONArray("sources") ?: return@mapNotNull null
                val sources = (0 until sourcesArr.length()).map { j ->
                    val s = sourcesArr.getJSONObject(j)
                    Source(source = s.getString("source"), id = s.getString("id"))
                }
                if (sources.isEmpty()) return@mapNotNull null
                Match(
                    id = o.optString("id", ""),
                    title = o.optString("title", "Unknown"),
                    category = o.optString("category", ""),
                    poster = o.optString("poster").ifEmpty { null },
                    sources = sources,
                    isLive = isLive,
                    startTime = if (o.has("startTime")) o.getLong("startTime") else null
                )
            }
        }
    }

    suspend fun getLiveMatches(): List<Match> =
        get("$BASE/matches/live")?.let { parseLiveMatches(it) } ?: emptyList()

    suspend fun getSports(): List<Sport> =
        get("$BASE/sports")?.let { parseSports(it) } ?: emptyList()

    suspend fun getMatchesBySport(sport: String): List<Match> =
        get("$BASE/matches/$sport")?.let { parseMatches(it) } ?: emptyList()

    suspend fun getEmbedUrl(source: String, id: String): String? =
        get("$BASE/stream/$source/$id")?.let { parseEmbedUrl(it) }

    suspend fun getAllMatches(): List<Match> =
        get("$BASE/matches/all")?.let { parseMatches(it) } ?: emptyList()

    private suspend fun get(url: String): String? =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val req = Request.Builder().url(url).build()
                http.newCall(req).execute().use { it.body?.string() }
            } catch (e: Exception) {
                null
            }
        }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew test --tests "com.firestreams.StreamedApiClientTest"`
Expected: 5 tests PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/firestreams/data/StreamedApiClient.kt \
        app/src/test/java/com/firestreams/StreamedApiClientTest.kt
git commit -m "feat: add StreamedApiClient with JSON parsing"
```

---

## Task 4: Extract StreamResolver

Extract WebView intercept logic from `MainActivity` into a standalone class.

**Files:**
- Create: `app/src/main/java/com/firestreams/data/StreamResolver.kt`

- [ ] **Step 1: Create `data/StreamResolver.kt`**

```kotlin
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

    // Timeout: if no stream found in 20s, call onFailed
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

                // Fallback: direct .m3u8/.mpd request
                if ((reqUrl.contains(".m3u8") || reqUrl.contains(".mpd")) && !resolved) {
                    resolved = true
                    handler.removeCallbacks(timeoutRunnable)
                    handler.post { onResolved(reqUrl, embedUrl) }
                }

                // Patch main HTML to inject JW Player hook
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
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/firestreams/data/StreamResolver.kt
git commit -m "feat: extract StreamResolver from MainActivity"
```

---

## Task 5: SourceFallbackManager with unit tests

Manages the list of sources for a match, tries them in order, drives failover.

**Files:**
- Create: `app/src/main/java/com/firestreams/data/SourceFallbackManager.kt`
- Create: `app/src/test/java/com/firestreams/SourceFallbackManagerTest.kt`

- [ ] **Step 1: Write failing unit tests**

Create `app/src/test/java/com/firestreams/SourceFallbackManagerTest.kt`:

```kotlin
package com.firestreams

import com.firestreams.data.Source
import com.firestreams.data.SourceFallbackManager
import org.junit.Assert.*
import org.junit.Test

class SourceFallbackManagerTest {

    @Test
    fun firstSourceIsCurrentOnInit() {
        val mgr = SourceFallbackManager(listOf(
            Source("delta", "id1"),
            Source("alpha", "id2")
        ))
        assertEquals(Source("delta", "id1"), mgr.current())
    }

    @Test
    fun advanceMovesToNextSource() {
        val mgr = SourceFallbackManager(listOf(
            Source("delta", "id1"),
            Source("alpha", "id2")
        ))
        assertTrue(mgr.advance())
        assertEquals(Source("alpha", "id2"), mgr.current())
    }

    @Test
    fun advanceReturnsFalseWhenExhausted() {
        val mgr = SourceFallbackManager(listOf(Source("delta", "id1")))
        assertFalse(mgr.advance())
    }

    @Test
    fun hasMoreReturnsTrueWhenSourcesRemain() {
        val mgr = SourceFallbackManager(listOf(
            Source("delta", "id1"),
            Source("alpha", "id2")
        ))
        assertTrue(mgr.hasMore())
        mgr.advance()
        assertFalse(mgr.hasMore())
    }

    @Test
    fun resetGoesBackToFirst() {
        val mgr = SourceFallbackManager(listOf(
            Source("delta", "id1"),
            Source("alpha", "id2")
        ))
        mgr.advance()
        mgr.reset()
        assertEquals(Source("delta", "id1"), mgr.current())
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests "com.firestreams.SourceFallbackManagerTest"`
Expected: FAIL — class not defined

- [ ] **Step 3: Create `data/SourceFallbackManager.kt`**

```kotlin
package com.firestreams.data

class SourceFallbackManager(private val sources: List<Source>) {
    private var index = 0

    fun current(): Source = sources[index]

    /** Advance to next source. Returns false if already on last. */
    fun advance(): Boolean {
        if (index >= sources.size - 1) return false
        index++
        return true
    }

    fun hasMore(): Boolean = index < sources.size - 1

    fun reset() { index = 0 }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew test --tests "com.firestreams.SourceFallbackManagerTest"`
Expected: 5 tests PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/firestreams/data/SourceFallbackManager.kt \
        app/src/test/java/com/firestreams/SourceFallbackManagerTest.kt
git commit -m "feat: add SourceFallbackManager with tests"
```

---

## Task 6: Browse screen

**Files:**
- Create: `app/src/main/java/com/firestreams/ui/browse/BrowseActivity.kt`
- Create: `app/src/main/java/com/firestreams/ui/browse/BrowseFragment.kt`
- Create: `app/src/main/java/com/firestreams/ui/browse/MatchCardPresenter.kt`
- Create: `app/src/main/res/layout/activity_browse.xml`
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Create `res/layout/activity_browse.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/browse_container"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="#000000" />
```

- [ ] **Step 2: Create `ui/browse/MatchCardPresenter.kt`**

```kotlin
package com.firestreams.ui.browse

import android.view.ViewGroup
import androidx.leanback.widget.ImageCardView
import androidx.leanback.widget.Presenter
import com.bumptech.glide.Glide
import com.firestreams.data.Match

class MatchCardPresenter : Presenter() {

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val card = ImageCardView(parent.context).apply {
            isFocusable = true
            isFocusableInTouchMode = true
            setMainImageDimensions(CARD_WIDTH, CARD_HEIGHT)
        }
        return ViewHolder(card)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any) {
        val match = item as Match
        val card = viewHolder.view as ImageCardView
        card.titleText = match.title
        card.contentText = match.category.replaceFirstChar { it.uppercase() }
        card.badgeImage = if (match.isLive) {
            card.context.getDrawable(android.R.drawable.presence_online) // replaced in Task 13
        } else null

        if (!match.poster.isNullOrEmpty()) {
            Glide.with(card.context).load(match.poster).centerCrop().into(card.mainImageView)
        } else {
            card.mainImageView.setImageDrawable(null)
        }
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        val card = viewHolder.view as ImageCardView
        card.badgeImage = null
        card.mainImageView.setImageDrawable(null)
    }

    companion object {
        private const val CARD_WIDTH = 320
        private const val CARD_HEIGHT = 180
    }
}
```

- [ ] **Step 3: Create `ui/browse/BrowseFragment.kt`**

```kotlin
package com.firestreams.ui.browse

import android.content.Intent
import android.os.Bundle
import androidx.leanback.app.BrowseSupportFragment
import androidx.leanback.widget.*
import androidx.lifecycle.lifecycleScope
import com.firestreams.data.Match
import com.firestreams.data.StreamedApiClient
import com.firestreams.ui.details.DetailsActivity
import com.firestreams.ui.search.SearchActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

class BrowseFragment : BrowseSupportFragment() {

    private val api by lazy { StreamedApiClient(OkHttpClient()) }
    private val rowsAdapter = ArrayObjectAdapter(ListRowPresenter())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Firestreams"
        headersState = HEADERS_ENABLED
        isHeadersTransitionOnBackEnabled = true
        adapter = rowsAdapter

        setOnSearchClickedListener {
            startActivity(Intent(requireContext(), SearchActivity::class.java))
        }

        setOnItemViewClickedListener { _, item, _, _ ->
            if (item is Match) {
                val intent = Intent(requireContext(), DetailsActivity::class.java)
                intent.putExtra(DetailsActivity.EXTRA_MATCH_ID, item.id)
                intent.putExtra(DetailsActivity.EXTRA_MATCH_TITLE, item.title)
                intent.putExtra(DetailsActivity.EXTRA_MATCH_CATEGORY, item.category)
                intent.putExtra(DetailsActivity.EXTRA_MATCH_POSTER, item.poster)
                intent.putExtra(DetailsActivity.EXTRA_MATCH_IS_LIVE, item.isLive)
                intent.putExtra(DetailsActivity.EXTRA_MATCH_SOURCES,
                    item.sources.map { "${it.source}:${it.id}" }.toTypedArray())
                startActivity(intent)
            }
        }

        loadContent()
    }

    private fun loadContent() {
        lifecycleScope.launch {
            val (liveMatches, sports) = withContext(Dispatchers.IO) {
                api.getLiveMatches() to api.getSports()
            }

            rowsAdapter.clear()

            // Row 1: Live Now
            if (liveMatches.isNotEmpty()) {
                val liveAdapter = ArrayObjectAdapter(MatchCardPresenter())
                liveMatches.forEach { liveAdapter.add(it) }
                rowsAdapter.add(ListRow(HeaderItem("Live Now"), liveAdapter))
            }

            // Per-sport rows
            sports.forEach { sport ->
                val matches = withContext(Dispatchers.IO) { api.getMatchesBySport(sport.id) }
                if (matches.isNotEmpty()) {
                    val sportAdapter = ArrayObjectAdapter(MatchCardPresenter())
                    matches.forEach { sportAdapter.add(it) }
                    rowsAdapter.add(ListRow(HeaderItem(sport.name), sportAdapter))
                }
            }

            // Upcoming row
            val upcoming = withContext(Dispatchers.IO) { api.getAllMatches() }
                .filter { !it.isLive }
            if (upcoming.isNotEmpty()) {
                val upcomingAdapter = ArrayObjectAdapter(MatchCardPresenter())
                upcoming.forEach { upcomingAdapter.add(it) }
                rowsAdapter.add(ListRow(HeaderItem("Upcoming"), upcomingAdapter))
            }
        }
    }
}
```

- [ ] **Step 4: Create `ui/browse/BrowseActivity.kt`**

```kotlin
package com.firestreams.ui.browse

import android.os.Bundle
import androidx.fragment.app.FragmentActivity

class BrowseActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(com.firestreams.R.layout.activity_browse)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(com.firestreams.R.id.browse_container, BrowseFragment())
                .commit()
        }
    }
}
```

- [ ] **Step 5: Update `AndroidManifest.xml` — replace MainActivity as launcher**

Replace the existing `<activity android:name=".MainActivity" ...>` block with:

```xml
<activity
    android:name=".ui.browse.BrowseActivity"
    android:exported="true"
    android:configChanges="keyboard|keyboardHidden|navigation|orientation|screenSize">
    <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LEANBACK_LAUNCHER" />
    </intent-filter>
</activity>

<activity
    android:name=".ui.details.DetailsActivity"
    android:exported="false"
    android:configChanges="keyboard|keyboardHidden|navigation|orientation|screenSize" />

<activity
    android:name=".ui.player.PlayerActivity"
    android:exported="false"
    android:configChanges="keyboard|keyboardHidden|navigation|orientation|screenSize" />

<activity
    android:name=".ui.multistream.MultiStreamActivity"
    android:exported="false"
    android:configChanges="keyboard|keyboardHidden|navigation|orientation|screenSize" />

<activity
    android:name=".ui.search.SearchActivity"
    android:exported="false"
    android:configChanges="keyboard|keyboardHidden|navigation|orientation|screenSize" />
```

- [ ] **Step 6: Build to verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL (Details/Player/Search/MultiStream activities don't exist yet — add stub classes to fix compile errors)

Stub for each missing activity (repeat pattern):

```kotlin
// app/src/main/java/com/firestreams/ui/details/DetailsActivity.kt
package com.firestreams.ui.details
import androidx.fragment.app.FragmentActivity
class DetailsActivity : FragmentActivity() {
    companion object {
        const val EXTRA_MATCH_ID = "match_id"
        const val EXTRA_MATCH_TITLE = "match_title"
        const val EXTRA_MATCH_CATEGORY = "match_category"
        const val EXTRA_MATCH_POSTER = "match_poster"
        const val EXTRA_MATCH_IS_LIVE = "match_is_live"
        const val EXTRA_MATCH_SOURCES = "match_sources"
    }
}

// app/src/main/java/com/firestreams/ui/player/PlayerActivity.kt
package com.firestreams.ui.player
import androidx.fragment.app.FragmentActivity
class PlayerActivity : FragmentActivity()

// app/src/main/java/com/firestreams/ui/multistream/MultiStreamActivity.kt
package com.firestreams.ui.multistream
import androidx.fragment.app.FragmentActivity
class MultiStreamActivity : FragmentActivity()

// app/src/main/java/com/firestreams/ui/search/SearchActivity.kt
package com.firestreams.ui.search
import androidx.fragment.app.FragmentActivity
class SearchActivity : FragmentActivity()
```

- [ ] **Step 7: Install and verify browse screen appears**

Run: `./gradlew installDebug && adb shell am start -n com.firestreams/.ui.browse.BrowseActivity`
Expected: Browse screen launches, rows load, cards show match titles

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/firestreams/ui/ \
        app/src/main/res/layout/activity_browse.xml \
        app/src/main/AndroidManifest.xml
git commit -m "feat: add browse screen with Leanback rows"
```

---

## Task 7: Details screen

**Files:**
- Modify: `app/src/main/java/com/firestreams/ui/details/DetailsActivity.kt`
- Create: `app/src/main/java/com/firestreams/ui/details/DetailsFragment.kt`
- Create: `app/src/main/res/layout/activity_details.xml`

- [ ] **Step 1: Create `res/layout/activity_details.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/details_container"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="#000000" />
```

- [ ] **Step 2: Create `ui/details/DetailsFragment.kt`**

```kotlin
package com.firestreams.ui.details

import android.content.Intent
import android.os.Bundle
import androidx.leanback.app.DetailsSupportFragment
import androidx.leanback.widget.*
import com.bumptech.glide.Glide
import com.firestreams.ui.player.PlayerActivity

class DetailsFragment : DetailsSupportFragment() {

    private lateinit var title: String
    private lateinit var category: String
    private var poster: String? = null
    private var isLive: Boolean = true
    private lateinit var sources: Array<String>
    private lateinit var matchId: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        matchId = requireActivity().intent.getStringExtra(DetailsActivity.EXTRA_MATCH_ID) ?: ""
        title = requireActivity().intent.getStringExtra(DetailsActivity.EXTRA_MATCH_TITLE) ?: ""
        category = requireActivity().intent.getStringExtra(DetailsActivity.EXTRA_MATCH_CATEGORY) ?: ""
        poster = requireActivity().intent.getStringExtra(DetailsActivity.EXTRA_MATCH_POSTER)
        isLive = requireActivity().intent.getBooleanExtra(DetailsActivity.EXTRA_MATCH_IS_LIVE, true)
        sources = requireActivity().intent.getStringArrayExtra(DetailsActivity.EXTRA_MATCH_SOURCES) ?: emptyArray()

        val row = buildDetailsRow()
        val adapter = ArrayObjectAdapter(FullWidthDetailsOverviewRowPresenter(DetailsDescriptionPresenter()))
        adapter.add(row)
        this.adapter = adapter

        setOnActionClickedListener { action ->
            if (action.id == ACTION_WATCH) {
                val intent = Intent(requireContext(), PlayerActivity::class.java).apply {
                    putExtra(PlayerActivity.EXTRA_MATCH_ID, matchId)
                    putExtra(PlayerActivity.EXTRA_MATCH_TITLE, title)
                    putExtra(PlayerActivity.EXTRA_SOURCES, sources)
                }
                startActivity(intent)
            }
        }
    }

    private fun buildDetailsRow(): DetailsOverviewRow {
        val row = DetailsOverviewRow(MatchDetails(title, category, isLive))
        val actions = ArrayObjectAdapter()
        actions.add(Action(ACTION_WATCH, if (isLive) "Watch Live" else "Watch"))
        row.actionsAdapter = actions

        if (!poster.isNullOrEmpty()) {
            Glide.with(requireContext())
                .asBitmap()
                .load(poster)
                .into(object : com.bumptech.glide.request.target.CustomTarget<android.graphics.Bitmap>() {
                    override fun onResourceReady(res: android.graphics.Bitmap, t: com.bumptech.glide.request.transition.Transition<in android.graphics.Bitmap>?) {
                        row.imageDrawable = android.graphics.drawable.BitmapDrawable(resources, res)
                    }
                    override fun onLoadCleared(p: android.graphics.drawable.Drawable?) {}
                })
        }

        return row
    }

    data class MatchDetails(val title: String, val category: String, val isLive: Boolean)

    inner class DetailsDescriptionPresenter : AbstractDetailsDescriptionPresenter() {
        override fun onBindDescription(vh: ViewHolder, item: Any) {
            val details = item as MatchDetails
            vh.title.text = details.title
            vh.subtitle.text = details.category.replaceFirstChar { it.uppercase() }
            vh.body.text = if (details.isLive) "🔴 LIVE" else "Upcoming"
        }
    }

    companion object {
        private const val ACTION_WATCH = 1L
    }
}
```

- [ ] **Step 3: Replace stub `DetailsActivity.kt` with full implementation**

```kotlin
package com.firestreams.ui.details

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import com.firestreams.R

class DetailsActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_details)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.details_container, DetailsFragment())
                .commit()
        }
    }

    companion object {
        const val EXTRA_MATCH_ID = "match_id"
        const val EXTRA_MATCH_TITLE = "match_title"
        const val EXTRA_MATCH_CATEGORY = "match_category"
        const val EXTRA_MATCH_POSTER = "match_poster"
        const val EXTRA_MATCH_IS_LIVE = "match_is_live"
        const val EXTRA_MATCH_SOURCES = "match_sources"
    }
}
```

- [ ] **Step 4: Install and verify**

Run: `./gradlew installDebug`
Navigate to a game card and press select — details screen should appear with title, category, and Watch button.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/firestreams/ui/details/ \
        app/src/main/res/layout/activity_details.xml
git commit -m "feat: add details screen with Leanback DetailsFragment"
```

---

## Task 8: Player screen with auto-failover and health indicator

**Files:**
- Create: `app/src/main/java/com/firestreams/ui/player/StreamHealthMonitor.kt`
- Create: `app/src/main/java/com/firestreams/ui/player/PlayerViewModel.kt`
- Modify: `app/src/main/java/com/firestreams/ui/player/PlayerActivity.kt`
- Create: `app/src/main/res/layout/activity_player.xml`

- [ ] **Step 1: Create `res/layout/activity_player.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="#000000">

    <androidx.media3.ui.PlayerView
        android:id="@+id/player_view"
        android:layout_width="match_parent"
        android:layout_height="match_parent" />

    <!-- Health dot: green=good, yellow=buffering, red=bad -->
    <View
        android:id="@+id/health_dot"
        android:layout_width="12dp"
        android:layout_height="12dp"
        android:layout_gravity="top|end"
        android:layout_margin="16dp"
        android:background="@drawable/health_dot"
        android:visibility="visible" />

    <TextView
        android:id="@+id/status_overlay"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_gravity="center"
        android:background="#AA000000"
        android:padding="16dp"
        android:textColor="#FFFFFF"
        android:textSize="20sp"
        android:visibility="gone" />

</FrameLayout>
```

- [ ] **Step 2: Create health dot drawable `res/drawable/health_dot.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="oval">
    <solid android:color="#00FF00" />
</shape>
```

- [ ] **Step 3: Create `ui/player/StreamHealthMonitor.kt`**

```kotlin
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
```

- [ ] **Step 4: Create `ui/player/PlayerViewModel.kt`**

```kotlin
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

    private val _state = MutableLiveData<PlayerState>(PlayerState.Loading)
    val state: LiveData<PlayerState> = _state

    fun start(sources: List<Source>) {
        fallback = SourceFallbackManager(sources)
        tryCurrentSource()
    }

    fun tryNextSource() {
        val fb = fallback ?: return
        if (!fb.advance()) {
            _state.value = PlayerState.Failed
            return
        }
        val attempt = sources().indexOf(fb.current()) + 1
        _state.value = PlayerState.Reconnecting(attempt, sources().size)
        tryCurrentSource()
    }

    fun forceSource(source: Source) {
        val fb = fallback ?: return
        // Rebuild fallback starting from chosen source
        val allSources = sources()
        val idx = allSources.indexOf(source)
        if (idx < 0) return
        fallback = SourceFallbackManager(allSources.drop(idx) + allSources.take(idx))
        _state.value = PlayerState.Loading
        tryCurrentSource()
    }

    fun sources(): List<Source> = fallback?.let {
        // Reconstruct the full list — SourceFallbackManager doesn't expose it, so we store it
        _allSources
    } ?: emptyList()

    private var _allSources: List<Source> = emptyList()

    fun start(allSources: List<Source>, startFrom: Source? = null) {
        _allSources = allSources
        val ordered = if (startFrom != null) {
            val idx = allSources.indexOf(startFrom)
            if (idx >= 0) allSources.drop(idx) + allSources.take(idx) else allSources
        } else allSources
        fallback = SourceFallbackManager(ordered)
        tryCurrentSource()
    }

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
```

- [ ] **Step 5: Replace stub `PlayerActivity.kt` with full implementation**

```kotlin
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
```

- [ ] **Step 6: Create `ui/player/SourcePickerFragment.kt`**

```kotlin
package com.firestreams.ui.player

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.firestreams.R
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
```

- [ ] **Step 7: Build, install, verify end-to-end**

Run: `./gradlew installDebug`

Test: Browse → select game → Details → Watch → stream plays, green dot shows in corner.
Test failover: temporarily break the network and verify "Reconnecting..." overlay appears.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/firestreams/ui/player/ \
        app/src/main/java/com/firestreams/data/StreamResolver.kt \
        app/src/main/res/layout/activity_player.xml \
        app/src/main/res/drawable/health_dot.xml
git commit -m "feat: player screen with auto-failover, health indicator, source picker"
```

---

## Task 9: Search screen

**Files:**
- Modify: `app/src/main/java/com/firestreams/ui/search/SearchActivity.kt`
- Create: `app/src/main/java/com/firestreams/ui/search/SearchFragment.kt`
- Create: `app/src/main/res/layout/activity_search.xml`

- [ ] **Step 1: Create `res/layout/activity_search.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/search_container"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="#000000" />
```

- [ ] **Step 2: Create `ui/search/SearchFragment.kt`**

```kotlin
package com.firestreams.ui.search

import android.content.Intent
import android.os.Bundle
import androidx.leanback.app.SearchSupportFragment
import androidx.leanback.widget.*
import androidx.lifecycle.lifecycleScope
import com.firestreams.data.Match
import com.firestreams.data.StreamedApiClient
import com.firestreams.ui.browse.MatchCardPresenter
import com.firestreams.ui.details.DetailsActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

class SearchFragment : SearchSupportFragment(), SearchSupportFragment.SearchResultProvider {

    private val api by lazy { StreamedApiClient(OkHttpClient()) }
    private val rowsAdapter = ArrayObjectAdapter(ListRowPresenter())
    private var searchJob: Job? = null
    private var allMatches: List<Match> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setSearchResultProvider(this)
        setOnItemViewClickedListener { _, item, _, _ ->
            if (item is Match) {
                startActivity(Intent(requireContext(), DetailsActivity::class.java).apply {
                    putExtra(DetailsActivity.EXTRA_MATCH_ID, item.id)
                    putExtra(DetailsActivity.EXTRA_MATCH_TITLE, item.title)
                    putExtra(DetailsActivity.EXTRA_MATCH_CATEGORY, item.category)
                    putExtra(DetailsActivity.EXTRA_MATCH_POSTER, item.poster)
                    putExtra(DetailsActivity.EXTRA_MATCH_IS_LIVE, item.isLive)
                    putExtra(DetailsActivity.EXTRA_MATCH_SOURCES,
                        item.sources.map { "${it.source}:${it.id}" }.toTypedArray())
                })
            }
        }

        // Pre-load all matches for instant search
        lifecycleScope.launch {
            allMatches = withContext(Dispatchers.IO) {
                api.getLiveMatches() + api.getAllMatches()
            }
        }
    }

    override fun getResultsAdapter(): ObjectAdapter = rowsAdapter

    override fun onQueryTextChange(query: String): Boolean {
        searchJob?.cancel()
        searchJob = lifecycleScope.launch {
            delay(300) // debounce
            filterResults(query)
        }
        return true
    }

    override fun onQueryTextSubmit(query: String): Boolean {
        filterResults(query)
        return true
    }

    private fun filterResults(query: String) {
        rowsAdapter.clear()
        if (query.isBlank()) return
        val results = allMatches.filter {
            it.title.contains(query, ignoreCase = true) ||
            it.category.contains(query, ignoreCase = true)
        }
        if (results.isNotEmpty()) {
            val adapter = ArrayObjectAdapter(MatchCardPresenter())
            results.forEach { adapter.add(it) }
            rowsAdapter.add(ListRow(HeaderItem("Results"), adapter))
        }
    }
}
```

- [ ] **Step 3: Replace stub `SearchActivity.kt`**

```kotlin
package com.firestreams.ui.search

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import com.firestreams.R

class SearchActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.search_container, SearchFragment())
                .commit()
        }
    }
}
```

- [ ] **Step 4: Install and verify search works**

Run: `./gradlew installDebug`
Press the search icon on the browse screen, type a team name, verify results appear and are selectable.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/firestreams/ui/search/ \
        app/src/main/res/layout/activity_search.xml
git commit -m "feat: add search screen with Leanback SearchFragment"
```

---

## Task 10: Multi-stream 2x2 grid

**Files:**
- Modify: `app/src/main/java/com/firestreams/ui/multistream/MultiStreamActivity.kt`
- Create: `app/src/main/res/layout/activity_multistream.xml`
- Modify: `app/src/main/java/com/firestreams/ui/browse/BrowseFragment.kt` (add Multi-Stream row entry)

- [ ] **Step 1: Create `res/layout/activity_multistream.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<GridLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="#000000"
    android:columnCount="2"
    android:rowCount="2">

    <FrameLayout android:id="@+id/cell_0"
        android:layout_width="0dp" android:layout_height="0dp"
        android:layout_columnWeight="1" android:layout_rowWeight="1"
        android:padding="2dp">
        <androidx.media3.ui.PlayerView android:id="@+id/player_0"
            android:layout_width="match_parent" android:layout_height="match_parent" />
        <TextView android:id="@+id/label_0" android:layout_width="wrap_content"
            android:layout_height="wrap_content" android:textColor="#FFF"
            android:background="#88000000" android:padding="4dp"
            android:text="Tap to select" android:layout_gravity="top|start" />
    </FrameLayout>

    <FrameLayout android:id="@+id/cell_1"
        android:layout_width="0dp" android:layout_height="0dp"
        android:layout_columnWeight="1" android:layout_rowWeight="1"
        android:padding="2dp">
        <androidx.media3.ui.PlayerView android:id="@+id/player_1"
            android:layout_width="match_parent" android:layout_height="match_parent" />
        <TextView android:id="@+id/label_1" android:layout_width="wrap_content"
            android:layout_height="wrap_content" android:textColor="#FFF"
            android:background="#88000000" android:padding="4dp"
            android:text="Tap to select" android:layout_gravity="top|start" />
    </FrameLayout>

    <FrameLayout android:id="@+id/cell_2"
        android:layout_width="0dp" android:layout_height="0dp"
        android:layout_columnWeight="1" android:layout_rowWeight="1"
        android:padding="2dp">
        <androidx.media3.ui.PlayerView android:id="@+id/player_2"
            android:layout_width="match_parent" android:layout_height="match_parent" />
        <TextView android:id="@+id/label_2" android:layout_width="wrap_content"
            android:layout_height="wrap_content" android:textColor="#FFF"
            android:background="#88000000" android:padding="4dp"
            android:text="Tap to select" android:layout_gravity="top|start" />
    </FrameLayout>

    <FrameLayout android:id="@+id/cell_3"
        android:layout_width="0dp" android:layout_height="0dp"
        android:layout_columnWeight="1" android:layout_rowWeight="1"
        android:padding="2dp">
        <androidx.media3.ui.PlayerView android:id="@+id/player_3"
            android:layout_width="match_parent" android:layout_height="match_parent" />
        <TextView android:id="@+id/label_3" android:layout_width="wrap_content"
            android:layout_height="wrap_content" android:textColor="#FFF"
            android:background="#88000000" android:padding="4dp"
            android:text="Tap to select" android:layout_gravity="top|start" />
    </FrameLayout>

</GridLayout>
```

- [ ] **Step 2: Replace stub `MultiStreamActivity.kt`**

```kotlin
package com.firestreams.ui.multistream

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
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

    // Slot index waiting for a stream assignment (set when user picks a slot)
    private var pendingSlot: Int? = null

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

        // Focus first cell
        updateFocus(0)

        // Each empty cell click → go back to browse to pick a game for that slot
        for (i in 0..3) {
            val cell = findViewById<FrameLayout>(cellIds[i])
            cell.setOnClickListener {
                if (players[i] == null) {
                    // Return to browse to pick a game for this slot
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
```

- [ ] **Step 3: Add `EXTRA_MULTISTREAM_SLOT` to `BrowseActivity` and wire up multi-stream entry point**

In `BrowseActivity.kt`, add companion object:

```kotlin
companion object {
    const val EXTRA_MULTISTREAM_SLOT = "multistream_slot"
}
```

In `BrowseFragment.kt`, in `setOnItemViewClickedListener`, check if we're in multi-stream slot assignment mode:

```kotlin
setOnItemViewClickedListener { _, item, _, _ ->
    if (item is Match) {
        val multistreamSlot = requireActivity().intent.getIntExtra(
            BrowseActivity.EXTRA_MULTISTREAM_SLOT, -1)

        if (multistreamSlot >= 0) {
            // We were picking for a multistream slot — go back to MultiStreamActivity
            val intent = Intent(requireContext(), MultiStreamActivity::class.java).apply {
                putExtra(MultiStreamActivity.EXTRA_SLOT, multistreamSlot)
                putExtra(MultiStreamActivity.EXTRA_TITLE, item.title)
                putExtra(MultiStreamActivity.EXTRA_SOURCES,
                    item.sources.map { "${it.source}:${it.id}" }.toTypedArray())
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            startActivity(intent)
        } else {
            // Normal flow → details
            val intent = Intent(requireContext(), DetailsActivity::class.java).apply {
                putExtra(DetailsActivity.EXTRA_MATCH_ID, item.id)
                putExtra(DetailsActivity.EXTRA_MATCH_TITLE, item.title)
                putExtra(DetailsActivity.EXTRA_MATCH_CATEGORY, item.category)
                putExtra(DetailsActivity.EXTRA_MATCH_POSTER, item.poster)
                putExtra(DetailsActivity.EXTRA_MATCH_IS_LIVE, item.isLive)
                putExtra(DetailsActivity.EXTRA_MATCH_SOURCES,
                    item.sources.map { "${it.source}:${it.id}" }.toTypedArray())
            }
            startActivity(intent)
        }
    }
}
```

Add a "Multi-Stream" action button to the browse screen in `BrowseFragment.onCreate`:

```kotlin
// Add after setOnSearchClickedListener
// Add multi-stream button to the browse title area as a special row entry
val multiStreamRow = ArrayObjectAdapter(object : Presenter() {
    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val btn = android.widget.Button(parent.context).apply {
            text = "⊞ Multi-Stream"
            setTextColor(android.graphics.Color.WHITE)
            setBackgroundColor(0xFF222222.toInt())
            isFocusable = true
        }
        return ViewHolder(btn)
    }
    override fun onBindViewHolder(vh: ViewHolder, item: Any) {}
    override fun onUnbindViewHolder(vh: ViewHolder) {}
})
multiStreamRow.add(Object())
rowsAdapter.add(0, ListRow(HeaderItem("Multi-Stream"), multiStreamRow))
```

Set click listener for the multi-stream button entry (add to `setOnItemViewClickedListener` block — check row header):

```kotlin
// In setOnItemViewClickedListener, add before the Match check:
val row = rowAdapter as? ListRow
if (row?.headerItem?.name == "Multi-Stream") {
    startActivity(Intent(requireContext(), MultiStreamActivity::class.java))
    return@setOnItemViewClickedListener
}
```

- [ ] **Step 4: Build, install, and verify multi-stream**

Run: `./gradlew installDebug`

Test: Browse → Multi-Stream row → opens 2x2 grid with empty cells. Click empty cell → returns to browse. Select game → fills that cell with live stream. Repeat for other cells.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/firestreams/ui/multistream/ \
        app/src/main/java/com/firestreams/ui/browse/ \
        app/src/main/res/layout/activity_multistream.xml
git commit -m "feat: add multi-stream 2x2 grid"
```

---

## Task 11: Polish — LIVE badge drawable & app icon

**Files:**
- Create: `app/src/main/res/drawable/live_badge.xml`
- Modify: `app/src/main/java/com/firestreams/ui/browse/MatchCardPresenter.kt`

- [ ] **Step 1: Create `res/drawable/live_badge.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <solid android:color="#FFE53935" />
    <corners android:radius="4dp" />
    <padding android:left="6dp" android:top="2dp" android:right="6dp" android:bottom="2dp" />
</shape>
```

- [ ] **Step 2: Replace placeholder badge in `MatchCardPresenter.onBindViewHolder`**

Replace:
```kotlin
card.badgeImage = if (match.isLive) {
    card.context.getDrawable(android.R.drawable.presence_online)
} else null
```

With:
```kotlin
card.badgeImage = if (match.isLive) {
    card.context.getDrawable(R.drawable.live_badge)
} else null
```

- [ ] **Step 3: Build and verify**

Run: `./gradlew installDebug`
Cards should show red LIVE badge.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/res/drawable/live_badge.xml \
        app/src/main/java/com/firestreams/ui/browse/MatchCardPresenter.kt
git commit -m "polish: add red LIVE badge to cards"
```

---

## Task 12: Final wiring — remove old MainActivity

- [ ] **Step 1: Delete `MainActivity.kt`**

```bash
rm app/src/main/java/com/firestreams/MainActivity.kt
```

- [ ] **Step 2: Remove it from AndroidManifest if still present**

Ensure `AndroidManifest.xml` has no reference to `.MainActivity`.

- [ ] **Step 3: Final build and full smoke test**

Run: `./gradlew installDebug`

Full test flow:
1. App opens to Browse screen with sport rows ✓
2. Search icon → search works ✓
3. Select game → Details screen ✓
4. Watch → stream plays, green health dot ✓
5. Menu button → source picker appears ✓
6. Kill network → "Reconnecting..." overlay, auto-switches source ✓
7. Multi-Stream row → 2x2 grid → fill all 4 cells ✓

- [ ] **Step 4: Final commit**

```bash
git add -A
git commit -m "feat: complete Firestreams TV app — browse, details, player, multi-stream, search"
```
