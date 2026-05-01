const tracks = [
    {
        title: "No One Noticed",
        artist: "The Marías",
        src: "./The Marías - No One Noticed.mp3",
        mood: "Late-night replay energy.",
        note: "A softer opener for the queue while the broader library is still being built.",
        accent: "#ff8a5b"
    },
    {
        title: "Illusionary Daytime x 室内系 TrackMaker [Extended Edit Version]",
        artist: "Reis",
        src: "./Illusionary Daytime x TrackMaker Extended Edit Version [8KI21GoNiuI].mp3",
        mood: "Fast, bright instrumental energy.",
        note: "Converted from the provided YouTube link and stored locally as an MP3 for Vibe.",
        accent: "#7ad7ff"
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

function getSourceLabel(src) {
    return /^https?:\/\//i.test(src) ? "Remote MP3" : "Local MP3";
}

class VibePlayer {
    constructor(trackData) {
        this.tracks = trackData;
        this.currentIndex = 0;
        this.isShuffle = false;
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
        this.shuffleButton = document.getElementById("shuffle-button");
        this.volume = document.getElementById("volume");
        this.volumeLabel = document.getElementById("volume-label");
        this.statusPill = document.getElementById("status-pill");
        this.helperText = document.getElementById("helper-text");
        this.playerVisual = document.getElementById("player-visual");
        this.sourceGrid = document.getElementById("source-grid");
        this.sourceCount = document.getElementById("source-count");
        this.heroPlayButton = document.querySelector('[data-action="toggle-play"]');

        this.handleFirstInteraction = () => {
            if (this.audio.paused && this.audio.src) {
                this.playCurrentTrack();
                this.cleanupFirstInteraction();
            }
        };

        this.init();
    }

    cleanupFirstInteraction() {
        window.removeEventListener("pointerdown", this.handleFirstInteraction);
        window.removeEventListener("keydown", this.handleFirstInteraction);
    }

    init() {
        if (!this.audio) {
            return;
        }

        this.audio.preload = "auto";
        this.audio.playsInline = true;

        this.renderTrackList();
        this.renderSourceCards();
        this.registerServiceWorker();
        this.setupMediaSessionHandlers();

        if (!this.tracks.length) {
            this.helperText.textContent = "Add at least one MP3 track in app.js to start playback.";
            return;
        }

        this.audio.loop = true;
        this.audio.volume = 0.88;
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
            this.updateMediaSessionState();
        });

        this.audio.addEventListener("pause", () => {
            this.playButton.textContent = "Play";
            this.heroPlayButton.textContent = "Play current track";
            this.setStatus("Paused");
            this.updateTrackButtons();
            this.updateMediaSessionState();
        });

        this.audio.addEventListener("waiting", () => this.setStatus("Buffering..."));
        this.audio.addEventListener("ratechange", () => this.updatePositionState());
        this.audio.addEventListener("durationchange", () => this.updatePositionState());
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

        this.shuffleButton.addEventListener("click", () => {
            this.isShuffle = !this.isShuffle;
            this.shuffleButton.textContent = this.isShuffle ? "Shuffle on" : "Shuffle off";
            this.shuffleButton.classList.toggle("chip-button-active", this.isShuffle);
            this.shuffleButton.setAttribute("aria-pressed", String(this.isShuffle));
            this.setStatus(this.isShuffle ? "Shuffle enabled" : "Shuffle disabled");
        });

        document.addEventListener("keydown", (event) => this.handleKeyboard(event));
        window.addEventListener("pointerdown", this.handleFirstInteraction, { once: true });
        window.addEventListener("keydown", this.handleFirstInteraction, { once: true });
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
            const duration = Number.isFinite(this.audio.duration) ? this.audio.duration : 0;
            this.audio.currentTime = clamp(this.audio.currentTime + 5, 0, duration);
            this.updateProgress();
        }

        if (event.code === "ArrowLeft") {
            event.preventDefault();
            const duration = Number.isFinite(this.audio.duration) ? this.audio.duration : 0;
            this.audio.currentTime = clamp(this.audio.currentTime - 5, 0, duration);
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
        this.updateMediaSessionState();
        this.updatePositionState();

        if (autoplay) {
            this.playCurrentTrack();
        }
    }

    changeTrack(direction) {
        if (this.isShuffle && this.tracks.length > 1) {
            let nextIndex = this.currentIndex;
            while (nextIndex === this.currentIndex) {
                nextIndex = Math.floor(Math.random() * this.tracks.length);
            }
            this.loadTrack(nextIndex, true);
        } else {
            const nextIndex = this.currentIndex + direction;
            this.loadTrack(nextIndex, true);
        }
    }

    togglePlayback() {
        if (this.audio.paused) {
            this.playCurrentTrack();
            this.cleanupFirstInteraction();
            return;
        }

        this.audio.pause();
    }

    playCurrentTrack() {
        this.audio.play().then(() => {
            this.cleanupFirstInteraction();
        }).catch(() => {
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
        this.updatePositionState();
    }

    updateVolumeLabel() {
        const percentage = Math.round(this.audio.volume * 100);
        this.volume.setAttribute("aria-label", `Volume ${percentage} percent`);
        if (this.volumeLabel) {
            this.volumeLabel.textContent = `Volume ${percentage}%`;
        }
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

    renderSourceCards() {
        if (!this.sourceGrid) {
            return;
        }

        if (this.sourceCount) {
            this.sourceCount.textContent = this.tracks.length
                ? `${this.tracks.length} ${this.tracks.length === 1 ? "source" : "sources"}`
                : "Ready for files";
        }

        if (!this.tracks.length) {
            this.sourceGrid.innerHTML = `
                <article class="playlist-empty">
                    <h3>MP3 library is ready</h3>
                    <p>
                        Add direct MP3 files to the <code>tracks</code> array in <code>app.js</code> and they
                        will appear here automatically.
                    </p>
                </article>
            `;
            return;
        }

        this.sourceGrid.innerHTML = "";

        this.tracks.forEach((track, index) => {
            const card = document.createElement("article");
            card.className = "playlist-item";

            const title = document.createElement("h3");
            title.textContent = track.title;

            const description = document.createElement("p");
            description.textContent = `${track.artist} · ${getSourceLabel(track.src)}`;

            const source = document.createElement("p");
            source.textContent = track.src;

            const button = document.createElement("button");
            button.type = "button";
            button.className = "button button-secondary";
            button.textContent = "Load track";
            button.addEventListener("click", () => this.loadTrack(index, true));

            card.append(title, description, source, button);
            this.sourceGrid.appendChild(card);
        });
    }

    syncMediaSession(track) {
        if (!("mediaSession" in navigator) || typeof MediaMetadata !== "function") {
            return;
        }

        navigator.mediaSession.metadata = new MediaMetadata({
            title: track.title,
            artist: track.artist,
            album: "Vibe",
            artwork: [
                {
                    src: new URL("./icon.svg", window.location.href).href,
                    sizes: "any",
                    type: "image/svg+xml"
                }
            ]
        });
    }

    setupMediaSessionHandlers() {
        if (!("mediaSession" in navigator) || typeof navigator.mediaSession.setActionHandler !== "function") {
            return;
        }

        const handlers = {
            play: () => this.playCurrentTrack(),
            pause: () => this.audio.pause(),
            previoustrack: () => this.changeTrack(-1),
            nexttrack: () => this.changeTrack(1),
            seekbackward: (details) => {
                const offset = details && typeof details.seekOffset === "number" ? details.seekOffset : 10;
                this.audio.currentTime = clamp(this.audio.currentTime - offset, 0, this.audio.duration || 0);
                this.updateProgress();
            },
            seekforward: (details) => {
                const offset = details && typeof details.seekOffset === "number" ? details.seekOffset : 10;
                this.audio.currentTime = clamp(this.audio.currentTime + offset, 0, this.audio.duration || 0);
                this.updateProgress();
            },
            seekto: (details) => {
                if (!details || typeof details.seekTime !== "number") {
                    return;
                }

                this.audio.currentTime = clamp(details.seekTime, 0, this.audio.duration || 0);
                this.updateProgress();
            }
        };

        Object.entries(handlers).forEach(([action, handler]) => {
            try {
                navigator.mediaSession.setActionHandler(action, handler);
            } catch (error) {
                return;
            }
        });
    }

    updateMediaSessionState() {
        if (!("mediaSession" in navigator)) {
            return;
        }

        navigator.mediaSession.playbackState = this.audio.paused ? "paused" : "playing";
    }

    updatePositionState() {
        if (
            !("mediaSession" in navigator) ||
            typeof navigator.mediaSession.setPositionState !== "function" ||
            !Number.isFinite(this.audio.duration) ||
            this.audio.duration <= 0
        ) {
            return;
        }

        try {
            navigator.mediaSession.setPositionState({
                duration: this.audio.duration,
                playbackRate: this.audio.playbackRate || 1,
                position: clamp(this.audio.currentTime, 0, this.audio.duration)
            });
        } catch (error) {
            return;
        }
    }

    registerServiceWorker() {
        if (!("serviceWorker" in navigator) || !window.isSecureContext) {
            return;
        }

        window.addEventListener("load", () => {
            navigator.serviceWorker.register("./sw.js").catch(() => {
                return;
            });
        });
    }
}

new VibePlayer(tracks);
