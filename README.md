# Vibe

Vibe is a small static listening room built with HTML, CSS, and JavaScript.

It now includes:

- A custom audio player instead of the browser default control bar
- A real track list powered by JavaScript data
- Keyboard shortcuts for playback, seeking, and volume
- Responsive styling that works better on mobile and desktop
- An MP3-only player for local files or direct MP3 URLs
- Media Session support for lock-screen and headset controls on supported browsers
- A lightweight PWA shell for better installability and local caching

## Current setup

The project currently ships with bundled local audio files in `assets/audio/`.

The app reads its content from one array in `assets/js/app.js`:

- `tracks` for MP3 files and direct MP3 URLs

## Run locally

Because this is a static project, you can open `index.html` directly or serve the folder with any simple static server.
For the best background playback behavior on Android, use the hosted HTTPS version or another secure origin so the service worker and installable app shell can load.

## Add more tracks

Edit the `tracks` array in `assets/js/app.js`:

```js
const tracks = [
  {
    title: "Song title",
    artist: "Artist name",
    src: "./assets/audio/your-file.mp3",
    mood: "Short mood line",
    note: "Optional supporting copy",
    accent: "#ff7b54"
  },
  {
    title: "Hosted track",
    artist: "Artist name",
    src: "https://example.com/audio/your-file.mp3",
    mood: "Direct MP3 URL",
    note: "This also works if the URL points straight to an MP3 file.",
    accent: "#5bc0eb"
  }
];
```

Only direct MP3 sources work here. A YouTube page URL is not an MP3 file, so it will not play in the
audio element unless you replace it with a real `.mp3` file path or direct `.mp3` URL.

## Background playback

Vibe now exposes Media Session metadata and action handlers so supported browsers can show play, pause,
seek, and track controls from notifications, headsets, and some lock screens.

This improves screen-off and background playback, but a web app still cannot keep playing after a device
is fully powered down. The realistic target here is background playback while the screen is locked or the
browser is not foregrounded.

## Android APK

This repo now includes a native Android wrapper in `android/` that loads the existing Vibe site from
bundled app assets inside a WebView.

The Android module copies `index.html`, `sw.js`, `manifest.webmanifest`, and the full `assets/`
directory into the app during the build, so the web player stays the single source of truth.

Recommended build path:

- Push changes to GitHub.
- Run the `Build Android APK` GitHub Actions workflow.
- Download the `vibe-debug-apk` artifact from that workflow run.

If you want to build manually on a desktop with Android Studio or the Android SDK installed:

```bash
cd android
./gradlew assembleDebug
```

If the build succeeds, the APK is written to:

```text
android/app/build/outputs/apk/debug/app-debug.apk
```

## Keyboard shortcuts

- `Space`: play or pause
- `Left Arrow`: rewind 5 seconds
- `Right Arrow`: skip forward 5 seconds
- `Up Arrow`: raise volume
- `Down Arrow`: lower volume

## Files

- `index.html`: app structure
- `assets/audio/`: bundled MP3 files
- `assets/css/styles.css`: visual design and responsive layout
- `assets/icons/icon.svg`: app icon used by the page, manifest, and media session
- `assets/js/app.js`: player logic and track list rendering
- `android/`: native Android wrapper project
- `manifest.webmanifest`: installable app metadata
- `sw.js`: local shell caching for the hosted app
