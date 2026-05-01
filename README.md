# Vibe

Vibe is a small static listening room built with HTML, CSS, and JavaScript.

It now includes:

- A custom audio player instead of the browser default control bar
- A real track queue powered by JavaScript data
- Keyboard shortcuts for playback, seeking, and volume
- Responsive styling that works better on mobile and desktop
- A dedicated section for embedding YouTube videos and playlists
- Privacy-enhanced YouTube embeds via `youtube-nocookie.com`

## Current setup

The project currently ships with one local audio file:

- `The Marías - No One Noticed.mp3`

The app reads its content from two arrays in `app.js`:

- `tracks` for local audio files
- `youtubeEmbeds` for YouTube video links, playlist links, or raw IDs

## Run locally

Because this is a static project, you can open `index.html` directly or serve the folder with any simple static server.

## Add more tracks

Edit the `tracks` array in `app.js`:

```js
const tracks = [
  {
    title: "Song title",
    artist: "Artist name",
    src: "./your-file.mp3",
    mood: "Short mood line",
    note: "Optional supporting copy",
    accent: "#ff7b54"
  }
];
```

## Add YouTube links

Edit the `youtubeEmbeds` array in `app.js` and add either direct YouTube URLs or objects:

```js
const youtubeEmbeds = [
  "https://youtu.be/YOUR_VIDEO_ID",
  "https://www.youtube.com/playlist?list=YOUR_PLAYLIST_ID",
  {
    title: "Night drive",
    url: "https://www.youtube.com/playlist?list=YOUR_PLAYLIST_ID",
    description: "Optional short description",
    kind: "playlist"
  },
  {
    title: "One-off video",
    url: "https://www.youtube.com/watch?v=YOUR_VIDEO_ID",
    description: "Optional short description",
    kind: "video"
  }
];
```

The app converts those into embedded video or playlist cards automatically.

## Privacy note

The YouTube embeds use `youtube-nocookie.com`, which is YouTube's privacy-enhanced embed mode.
That reduces personalization, but it does not give the site its own ad blocker and it cannot force
YouTube to never show ads or account prompts.

## Keyboard shortcuts

- `Space`: play or pause
- `Left Arrow`: rewind 5 seconds
- `Right Arrow`: skip forward 5 seconds
- `Up Arrow`: raise volume
- `Down Arrow`: lower volume

## Files

- `index.html`: app structure
- `styles.css`: visual design and responsive layout
- `app.js`: player logic, queue rendering, and YouTube embeds
