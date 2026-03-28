# firestreams

Live sports streaming for Fire TV.

## About

*firestreams* is a native Android TV app that aggregates live sports streams from around the world.
It pulls real-time match data and resolves playable HLS streams directly on your Fire TV — no subscription, no login required.

## API Example

Match and stream data is retrieved from the streamed.pk API.

```kotlin
// Fetch all live matches
val response = http.newCall(
    Request.Builder().url("https://streamed.pk/api/matches/live").build()
).execute()

// Resolve a playable stream URL for a given source
val embedUrl = http.newCall(
    Request.Builder().url("https://streamed.pk/api/stream/$source/$id").build()
).execute()
```

Each match contains a list of sources. The app resolves each source to a playable `.m3u8` HLS stream, automatically cycling through fallbacks if one fails.

## Usage

- Browse live and upcoming matches organized by sport.
- Select any match to go straight to the player.
- Press the D-pad center while watching to open the stream source picker.
- The app automatically switches sources if a stream buffers or fails.

## Sideloading to Fire TV

### Prerequisites
- Android Studio or the Android SDK command-line tools
- A Fire TV with Developer Options enabled
- [Downloader](https://www.amazon.com/AFTVnews-com-Downloader/dp/B01N0BP507) installed from the Amazon App Store

### Installation

1. Clone the repository:

```bash
git clone https://github.com/bjaxqq/firestreams.git
cd firestreams
```

2. Build the APK:

```bash
./gradlew assembleDebug
```

3. The APK will be at `app/build/outputs/apk/debug/app-debug.apk`. Host it somewhere with a direct download URL (Dropbox, Google Drive, or a GitHub Release), then open Downloader on your Fire TV and enter the URL to install.

To deploy directly via ADB:

```bash
adb connect <your-fire-tv-ip>:5555
./gradlew installDebug
```

## Tech Stack

[Kotlin](https://kotlinlang.org/), [Android TV / Leanback](https://developer.android.com/training/tv), [ExoPlayer / Media3](https://developer.android.com/media/media3/exoplayer), [OkHttp](https://square.github.io/okhttp/), [Glide](https://bumptech.github.io/glide/)

## License

This project is licensed under the MIT License - see the [LICENSE](https://github.com/bjaxqq/firestreams/blob/master/LICENSE) file for details.
