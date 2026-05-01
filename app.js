const tracks = [
    {
        title: "No One Noticed",
        artist: "The Marías",
        src: "./The Marías - No One Noticed.mp3",
        mood: "Late-night replay energy.",
        note: "A softer opener for the queue while the broader library is still being built.",
        accent: "#ff8a5b"
    }
];

const youtubeEmbeds = [
    {
        title: "YouTube pick 1",
        url: "https://youtu.be/f3DoKx_R1_s",
        description: "First saved YouTube link from the current queue update."
    },
    {
        title: "YouTube pick 2",
        url: "https://youtu.be/h2GQVlMkFiE",
        description: "Second saved YouTube link from the current queue update."
    }
];

function formatTime(seconds) {
    if (!Number.isFinite(seconds) || seconds < 0) {
        return "0:00";
    }

    const totalSeconds = Math.floor(seconds);
    const minutes = Math.floor(totalSeconds / 60);
    const remainingSeconds = String(totalSeconds % 60).padStart(2, "0");
    return `${minutes}:${remainingSeconds}`;
}

function clamp(value, min, max) {
    return Math.min(max, Math.max(min, value));
}

function normalizeYouTubeItem(entry, index) {
    if (typeof entry === "string") {
        return {
            title: `YouTube item ${index + 1}`,
            url: entry,
            description: "Added from a YouTube link."
        };
    }

    return {
        title: entry.title || `YouTube item ${index + 1}`,
        url: entry.url || entry.id || "",
        description: entry.description || "Embedded from a YouTube link.",
        kind: entry.kind || ""
    };
}

function parseYouTubeResource(value, preferredKind = "") {
    if (!value) {
        return null;
    }

    const trimmed = value.trim();

    if (!trimmed) {
        return null;
    }

    if (!trimmed.includes("://")) {
        const type = preferredKind || (/^(PL|UU|FL|OLAK5uy_)/.test(trimmed) ? "playlist" : "video");
        return {
            type,
            id: trimmed
        };
    }

    try {
        const url = new URL(trimmed);
        const hostname = url.hostname.replace(/^www\./, "");
        const pathSegments = url.pathname.split("/").filter(Boolean);
        const listId = url.searchParams.get("list");
        const videoId = url.searchParams.get("v");

        if (listId && preferredKind !== "video") {
            return {
                type: "playlist",
                id: listId
            };
        }

        if (hostname === "youtu.be" && pathSegments[0]) {
            return {
                type: "video",
                id: pathSegments[0]
            };
        }

        if ((hostname === "youtube.com" || hostname.endsWith(".youtube.com")) && videoId) {
            return {
                type: "video",
                id: videoId
            };
        }

        if ((hostname === "youtube.com" || hostname.endsWith(".youtube.com")) && pathSegments[0] === "embed" && pathSegments[1]) {
            return {
                type: preferredKind || "video",
                id: pathSegments[1]
            };
        }

        if ((hostname === "youtube.com" || hostname.endsWith(".youtube.com")) && pathSegments[0] === "shorts" && pathSegments[1]) {
            return {
                type: "video",
                id: pathSegments[1]
            };
        }
    } catch (error) {
        const type = preferredKind || (/^(PL|UU|FL|OLAK5uy_)/.test(trimmed) ? "playlist" : "video");
        return {
            type,
            id: trimmed
        };
    }

    return null;
}

function buildYouTubeEmbed(value, preferredKind = "") {
    const resource = parseYouTubeResource(value, preferredKind);

    if (!resource || !resource.id) {
        return null;
    }

    const params = new URLSearchParams({
        playsinline: "1",
        rel: "0",
        iv_load_policy: "3"
    });

    if (resource.type === "playlist") {
        return {
            type: "playlist",
            src: `https://www.youtube-nocookie.com/embed/videoseries?list=${encodeURIComponent(resource.id)}&${params.toString()}`
        };
    }

    return {
        type: "video",
        src: `https://www.youtube-nocookie.com/embed/${encodeURIComponent(resource.id)}?${params.toString()}`
    };
}

class VibePlayer {
    constructor(trackData, youtubeData) {
        this.tracks = trackData;
        this.youtubeItems = youtubeData;
        this.currentIndex = 0;
        this.statusTimeout = null;

        this.audio = document.getElementById("audio");
        this.trackTitle = document.getElementById("track-title");
        this.trackArtist = document.getElementById("track-artist");
        this.trackMood = document.getElementById("track-mood");
        this.trackNote = document.getElementById("track-note");
        this.trackList = document.getElementById("track-list");
        this.trackCount = document.getElementById("track-count");
        this.playButton = document.getElementById("play-button");
        this.prevButton = document.getElementById("prev-button");
        this.nextButton = document.getElementById("next-button");
        this.progress = document.getElementById("progress");
        this.currentTime = document.getElementById("current-time");
        this.duration = document.getElementById("duration");
        this.loopButton = document.getElementById("loop-button");
        this.volume = document.getElementById("volume");
        this.statusPill = document.getElementById("status-pill");
        this.helperText = document.getElementById("helper-text");
        this.playerVisual = document.getElementById("player-visual");
        this.playlistGrid = document.getElementById("playlist-grid");
        this.youtubeCount = document.getElementById("youtube-count");
        this.heroPlayButton = document.querySelector('[data-action="toggle-play"]');

        this.handleFirstInteraction = () => {
            if (this.audio.paused) {
                this.playCurrentTrack();
            }
        };

        this.init();
    }

    init() {
        if (!this.audio || !this.tracks.length) {
            return;
        }

        this.audio.loop = true;
        this.audio.volume = 0.88;

        this.renderTrackList();
        this.renderYouTubeEmbeds();
        this.bindEvents();
        this.loadTrack(0);
        this.updateVolumeLabel();
        this.tryAutoplay();
    }

    bindEvents() {
        this.playButton.addEventListener("click", () => this.togglePlayback());
        this.heroPlayButton.addEventListener("click", () => this.togglePlayback());
        this.prevButton.addEventListener("click", () => this.changeTrack(-1));
        this.nextButton.addEventListener("click", () => this.changeTrack(1));

        this.progress.addEventListener("input", () => {
            if (!Number.isFinite(this.audio.duration)) {
                return;
            }

            const nextTime = (Number(this.progress.value) / 100) * this.audio.duration;
            this.audio.currentTime = nextTime;
            this.currentTime.textContent = formatTime(nextTime);
        });

        this.volume.addEventListener("input", () => {
            this.audio.volume = clamp(Number(this.volume.value) / 100, 0, 1);
            this.updateVolumeLabel();
        });

        this.loopButton.addEventListener("click", () => {
            this.audio.loop = !this.audio.loop;
            this.loopButton.textContent = this.audio.loop ? "Loop on" : "Loop off";
            this.loopButton.classList.toggle("chip-button-active", this.audio.loop);
            this.loopButton.setAttribute("aria-pressed", String(this.audio.loop));
            this.setStatus(this.audio.loop ? "Loop enabled" : "Loop disabled");
        });

        this.audio.addEventListener("loadedmetadata", () => {
            this.duration.textContent = formatTime(this.audio.duration);
            this.updateProgress();
        });

        this.audio.addEventListener("timeupdate", () => this.updateProgress());

        this.audio.addEventListener("play", () => {
            this.playButton.textContent = "Pause";
            this.heroPlayButton.textContent = "Pause current track";
            this.setStatus("Playing now");
            this.updateTrackButtons();
        });

        this.audio.addEventListener("pause", () => {
            this.playButton.textContent = "Play";
            this.heroPlayButton.textContent = "Play current track";
            this.setStatus("Paused");
            this.updateTrackButtons();
        });

        this.audio.addEventListener("waiting", () => this.setStatus("Buffering..."));
        this.audio.addEventListener("ended", () => {
            if (!this.audio.loop && this.tracks.length > 1) {
                this.changeTrack(1);
                return;
            }

            this.setStatus("Track ended");
        });

        this.audio.addEventListener("error", () => {
            this.setStatus("Audio file could not be loaded");
            this.helperText.textContent = "Check the track path in app.js if a song does not play.";
        });

        document.addEventListener("keydown", (event) => this.handleKeyboard(event));
        window.addEventListener("pointerdown", this.handleFirstInteraction, { once: true });
    }

    handleKeyboard(event) {
        const target = event.target;
        const tagName = target && target.tagName ? target.tagName : "";
        const isFormField = ["INPUT", "TEXTAREA", "SELECT"].includes(tagName);

        if (isFormField) {
            return;
        }

        if (event.code === "Space") {
            event.preventDefault();
            this.togglePlayback();
        }

        if (event.code === "ArrowRight") {
            event.preventDefault();
            this.audio.currentTime = clamp(this.audio.currentTime + 5, 0, this.audio.duration || 0);
            this.updateProgress();
        }

        if (event.code === "ArrowLeft") {
            event.preventDefault();
            this.audio.currentTime = clamp(this.audio.currentTime - 5, 0, this.audio.duration || 0);
            this.updateProgress();
        }

        if (event.code === "ArrowUp") {
            event.preventDefault();
            this.audio.volume = clamp(this.audio.volume + 0.05, 0, 1);
            this.volume.value = Math.round(this.audio.volume * 100);
            this.updateVolumeLabel();
        }

        if (event.code === "ArrowDown") {
            event.preventDefault();
            this.audio.volume = clamp(this.audio.volume - 0.05, 0, 1);
            this.volume.value = Math.round(this.audio.volume * 100);
            this.updateVolumeLabel();
        }
    }

    loadTrack(index, autoplay = false) {
        this.currentIndex = (index + this.tracks.length) % this.tracks.length;
        const track = this.tracks[this.currentIndex];

        this.audio.src = track.src;
        this.audio.load();

        this.trackTitle.textContent = track.title;
        this.trackArtist.textContent = track.artist;
        this.trackMood.textContent = track.mood || "Set the mood for this track.";
        this.trackNote.textContent = track.note || "Add a short note for this track in app.js.";
        this.playerVisual.style.setProperty("--art-accent", track.accent || "#ff8a5b");

        this.currentTime.textContent = "0:00";
        this.duration.textContent = "0:00";
        this.progress.value = "0";

        this.updateTrackButtons();
        this.syncMediaSession(track);

        if (autoplay) {
            this.playCurrentTrack();
        }
    }

    changeTrack(direction) {
        const nextIndex = this.currentIndex + direction;
        this.loadTrack(nextIndex, true);
    }

    togglePlayback() {
        if (this.audio.paused) {
            this.playCurrentTrack();
            return;
        }

        this.audio.pause();
    }

    playCurrentTrack() {
        this.audio.play().catch(() => {
            this.setStatus("Press play to start audio");
            this.helperText.textContent = "Browser autoplay rules blocked playback until you interacted with the page.";
        });
    }

    tryAutoplay() {
        this.playCurrentTrack();
    }

    updateProgress() {
        if (!Number.isFinite(this.audio.duration) || this.audio.duration <= 0) {
            this.progress.value = "0";
            return;
        }

        const ratio = (this.audio.currentTime / this.audio.duration) * 100;
        this.progress.value = String(ratio);
        this.currentTime.textContent = formatTime(this.audio.currentTime);
        this.duration.textContent = formatTime(this.audio.duration);
    }

    updateVolumeLabel() {
        const percentage = Math.round(this.audio.volume * 100);
        this.volume.setAttribute("aria-label", `Volume ${percentage} percent`);
    }

    setStatus(message) {
        this.statusPill.textContent = message;

        if (this.statusTimeout) {
            window.clearTimeout(this.statusTimeout);
        }

        if (message !== "Playing now") {
            this.statusTimeout = window.setTimeout(() => {
                if (this.audio.paused) {
                    this.statusPill.textContent = "Ready to play";
                } else {
                    this.statusPill.textContent = "Playing now";
                }
            }, 2200);
        }
    }

    renderTrackList() {
        this.trackList.innerHTML = "";
        this.trackCount.textContent = `${this.tracks.length} ${this.tracks.length === 1 ? "track" : "tracks"}`;

        this.tracks.forEach((track, index) => {
            const item = document.createElement("li");
            const button = document.createElement("button");
            const meta = document.createElement("span");
            const title = document.createElement("span");
            const line = document.createElement("span");
            const state = document.createElement("span");

            button.type = "button";
            button.className = "track-button";
            button.dataset.index = String(index);

            meta.className = "track-meta";
            title.className = "track-title";
            line.className = "track-line";
            state.className = "track-state";

            title.textContent = track.title;
            line.textContent = `${track.artist} - ${track.mood || "Mood line pending"}`;
            state.textContent = "Ready";

            meta.append(title, line);
            button.append(meta, state);
            button.addEventListener("click", () => this.loadTrack(index, true));

            item.appendChild(button);
            this.trackList.appendChild(item);
        });

        this.updateTrackButtons();
    }

    updateTrackButtons() {
        const buttons = this.trackList.querySelectorAll(".track-button");

        buttons.forEach((button) => {
            const index = Number(button.dataset.index);
            const isActive = index === this.currentIndex;
            const state = button.querySelector(".track-state");

            button.classList.toggle("track-button-active", isActive);

            if (!state) {
                return;
            }

            if (isActive && !this.audio.paused) {
                state.textContent = "Playing";
            } else if (isActive) {
                state.textContent = "Selected";
            } else {
                state.textContent = "Ready";
            }
        });
    }

    renderYouTubeEmbeds() {
        const normalized = this.youtubeItems
            .map((entry, index) => normalizeYouTubeItem(entry, index))
            .map((entry) => ({
                ...entry,
                embed: buildYouTubeEmbed(entry.url, entry.kind)
            }))
            .filter((entry) => entry.embed);

        if (this.youtubeCount) {
            this.youtubeCount.textContent = normalized.length
                ? `${normalized.length} ${normalized.length === 1 ? "link" : "links"} added`
                : "Ready for links";
        }

        if (!normalized.length) {
            this.playlistGrid.innerHTML = `
                <article class="playlist-empty">
                    <h3>YouTube space is ready</h3>
                    <p>
                        Drop YouTube video or playlist links into <code>youtubeEmbeds</code> in <code>app.js</code>
                        and this section will render them as embeds automatically.
                    </p>
                </article>
            `;
            return;
        }

        this.playlistGrid.innerHTML = "";

        normalized.forEach((playlist) => {
            const card = document.createElement("article");
            card.className = "playlist-item";

            const title = document.createElement("h3");
            title.textContent = playlist.title;

            const description = document.createElement("p");
            description.textContent = playlist.description;

            const link = document.createElement("a");
            link.className = "playlist-link";
            link.href = playlist.url;
            link.target = "_blank";
            link.rel = "noreferrer";
            link.textContent = "Open on YouTube";

            const frame = document.createElement("iframe");
            frame.loading = "lazy";
            frame.allow = "accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share";
            frame.referrerPolicy = "strict-origin-when-cross-origin";
            frame.allowFullscreen = true;
            frame.src = playlist.embed.src;
            frame.title = `${playlist.title} ${playlist.embed.type}`;

            card.append(title, description, link, frame);
            this.playlistGrid.appendChild(card);
        });
    }

    syncMediaSession(track) {
        if (!("mediaSession" in navigator) || typeof MediaMetadata !== "function") {
            return;
        }

        navigator.mediaSession.metadata = new MediaMetadata({
            title: track.title,
            artist: track.artist,
            album: "Vibe"
        });
    }
}

new VibePlayer(tracks, youtubeEmbeds);
