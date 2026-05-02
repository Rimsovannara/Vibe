import { formatTime, clamp } from './utils.js';

export class UI {
    constructor(engine) {
        this.engine = engine;
        this.statusTimeout = null;
        this.isScrubbing = false;
        this.animationFrameId = null;

        // DOM Elements
        this.trackTitle = document.getElementById("track-title");
        this.trackArtist = document.getElementById("track-artist");
        this.trackPosition = document.getElementById("track-position");
        this.trackMood = document.getElementById("track-mood");
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
        this.visualizer = document.getElementById("visualizer");
        this.vinylDisc = document.querySelector(".vinyl-disc");

        // Set up album art element dynamically if not present
        this.albumArt = document.getElementById("album-art");
        if (!this.albumArt) {
            this.albumArt = document.createElement("img");
            this.albumArt.id = "album-art";
            this.albumArt.style.position = "absolute";
            this.albumArt.style.top = "0";
            this.albumArt.style.left = "0";
            this.albumArt.style.width = "100%";
            this.albumArt.style.height = "100%";
            this.albumArt.style.objectFit = "cover";
            this.albumArt.style.borderRadius = "50%";
            this.albumArt.style.opacity = "0"; // fade in later
            this.albumArt.style.transition = "opacity 0.3s ease";
            this.albumArt.style.mixBlendMode = "screen";
            this.vinylDisc.appendChild(this.albumArt);
        }

        this._bindDOMEvents();
        this._bindEngineEvents();
    }

    setStatus(message) {
        this.statusPill.textContent = message;
        if (this.statusTimeout) window.clearTimeout(this.statusTimeout);

        if (message !== "Playing") {
            this.statusTimeout = window.setTimeout(() => {
                this.statusPill.textContent = this.engine.audio.paused ? "Ready" : "Playing";
            }, 2200);
        }
    }

    _bindDOMEvents() {
        this.playButton.addEventListener("click", () => this.engine.togglePlayback());
        this.prevButton.addEventListener("click", () => this.engine.changeTrack(-1));
        this.nextButton.addEventListener("click", () => this.engine.changeTrack(1));

        this.progress.addEventListener("input", () => {
            if (!Number.isFinite(this.engine.audio.duration)) return;
            const nextTime = (Number(this.progress.value) / 100) * this.engine.audio.duration;
            this.engine.audio.currentTime = nextTime;
            this.currentTime.textContent = formatTime(nextTime);
        });

        this.progress.addEventListener("pointerdown", () => { this.isScrubbing = true; });
        window.addEventListener("pointerup", () => {
            if (this.isScrubbing) {
                this.isScrubbing = false;
                this.updateProgress();
            }
        });

        this.volume.addEventListener("input", () => {
            this.engine.setVolume(Number(this.volume.value) / 100);
            this.updateVolumeLabel();
        });

        this.loopButton.addEventListener("click", () => {
            const isLooping = this.engine.toggleLoop();
            if (isLooping && this.engine.isShuffle) this.engine.toggleShuffle();
        });

        this.shuffleButton.addEventListener("click", () => {
            const isShuffle = this.engine.toggleShuffle();
            if (isShuffle && this.engine.audio.loop) this.engine.toggleLoop();
        });

        document.addEventListener("keydown", (event) => this._handleKeyboard(event));

        // Autoplay interaction hack
        const handleFirstInteraction = () => {
            if (this.engine.audio.paused && this.engine.audio.src) {
                this.engine.play();
                window.removeEventListener("pointerdown", handleFirstInteraction);
                window.removeEventListener("keydown", handleFirstInteraction);
            }
        };
        window.addEventListener("pointerdown", handleFirstInteraction, { once: true });
        window.addEventListener("keydown", handleFirstInteraction, { once: true });
    }

    _handleKeyboard(event) {
        const tagName = event.target?.tagName || "";
        if (["INPUT", "TEXTAREA", "SELECT"].includes(tagName) || event.metaKey || event.ctrlKey || event.altKey) return;

        switch(event.code) {
            case "Space":
                event.preventDefault();
                this.engine.togglePlayback();
                break;
            case "ArrowRight":
                event.preventDefault();
                this.engine.seekTo(this.engine.audio.currentTime + 5);
                this.updateProgress();
                break;
            case "ArrowLeft":
                event.preventDefault();
                this.engine.seekTo(this.engine.audio.currentTime - 5);
                this.updateProgress();
                break;
            case "ArrowUp":
                event.preventDefault();
                this.engine.setVolume(this.engine.getVolume() + 0.05);
                this.volume.value = Math.round(this.engine.getVolume() * 100);
                this.updateVolumeLabel();
                break;
            case "ArrowDown":
                event.preventDefault();
                this.engine.setVolume(this.engine.getVolume() - 0.05);
                this.volume.value = Math.round(this.engine.getVolume() * 100);
                this.updateVolumeLabel();
                break;
        }
    }

    _bindEngineEvents() {
        this.engine.addEventListener("tracksChanged", () => this.renderTrackList());
        
        this.engine.addEventListener("trackLoaded", (e) => {
            const { track, index } = e.detail;
            this.trackTitle.textContent = track.title;
            this.trackArtist.textContent = track.artist;
            this.trackPosition.textContent = `Track ${index + 1} of ${this.engine.tracks.length}`;
            if (this.trackMood) this.trackMood.textContent = track.mood || "";

            this.currentTime.textContent = "0:00";
            this.duration.textContent = "0:00";
            this.progress.value = "0";

            // Update Album Art
            this.albumArt.style.opacity = "0";
            const artUrl = track.src.replace('/audio/', '/art/');
            this.albumArt.onload = () => { this.albumArt.style.opacity = "0.7"; };
            this.albumArt.onerror = () => { this.albumArt.style.opacity = "0"; };
            this.albumArt.src = artUrl;

            this.updateTrackButtons();
        });

        this.engine.addEventListener("play", () => {
            this.playButton.querySelector(".icon-play").style.display = "none";
            this.playButton.querySelector(".icon-pause").style.display = "";
            this.setStatus("Playing");
            this.updateTrackButtons();
            this.startProgressLoop();
            if (this.visualizer) this.visualizer.classList.add("is-playing");
            if (this.vinylDisc) this.vinylDisc.classList.add("is-playing");
        });

        this.engine.addEventListener("pause", () => {
            this.playButton.querySelector(".icon-play").style.display = "";
            this.playButton.querySelector(".icon-pause").style.display = "none";
            this.setStatus("Paused");
            this.updateTrackButtons();
            this.stopProgressLoop();
            if (this.visualizer) this.visualizer.classList.remove("is-playing");
            if (this.vinylDisc) this.vinylDisc.classList.remove("is-playing");
        });

        this.engine.addEventListener("waiting", () => this.setStatus("Buffering..."));
        this.engine.addEventListener("canplay", () => {
            if (this.statusPill.textContent === "Buffering...") {
                this.setStatus(this.engine.audio.paused ? "Ready" : "Playing");
            }
        });
        
        this.engine.addEventListener("ended", () => {
            this.stopProgressLoop();
            if (this.engine.tracks.length <= 1 || this.engine.audio.loop) {
                this.setStatus("Track ended");
            }
        });

        this.engine.addEventListener("error", () => {
            this.setStatus("Audio file could not be loaded");
            this.helperText.textContent = "Check the track path if a song does not play.";
        });

        this.engine.addEventListener("loadedmetadata", () => {
            this.duration.textContent = formatTime(this.engine.audio.duration);
            this.updateProgress();
        });

        this.engine.addEventListener("playbackBlocked", () => {
            this.setStatus("Press play to start audio");
            this.helperText.textContent = "Browser autoplay rules blocked playback until you interacted with the page.";
        });

        this.engine.addEventListener("loopChanged", (e) => {
            const isLooping = e.detail.isLooping;
            this.loopButton.setAttribute("aria-pressed", String(isLooping));
            this.loopButton.classList.toggle("chip-active", isLooping);
            this.setStatus(isLooping ? "Loop enabled" : "Loop disabled");
        });

        this.engine.addEventListener("shuffleChanged", (e) => {
            const isShuffle = e.detail.isShuffle;
            this.shuffleButton.setAttribute("aria-pressed", String(isShuffle));
            this.shuffleButton.classList.toggle("chip-active", isShuffle);
            this.setStatus(isShuffle ? "Shuffle enabled" : "Shuffle disabled");
        });
    }

    startProgressLoop() {
        if (this.animationFrameId !== null) return;
        const loop = () => {
            this.updateProgress();
            this.animationFrameId = requestAnimationFrame(loop);
        };
        this.animationFrameId = requestAnimationFrame(loop);
    }

    stopProgressLoop() {
        if (this.animationFrameId !== null) {
            cancelAnimationFrame(this.animationFrameId);
            this.animationFrameId = null;
        }
    }

    updateProgress() {
        const audio = this.engine.audio;
        if (!Number.isFinite(audio.duration) || audio.duration <= 0) {
            if (!this.isScrubbing) {
                this.progress.value = "0";
                this.progress.style.setProperty("--pct", "0%");
            }
            return;
        }

        const ratio = (audio.currentTime / audio.duration) * 100;
        if (!this.isScrubbing) {
            this.progress.value = String(ratio);
            this.progress.style.setProperty("--pct", `${ratio}%`);
        }
        this.currentTime.textContent = formatTime(audio.currentTime);
        this.duration.textContent = formatTime(audio.duration);
    }

    updateVolumeLabel() {
        const percentage = Math.round(this.engine.getVolume() * 100);
        this.volume.setAttribute("aria-label", `Volume ${percentage} percent`);
        if (this.volumeLabel) {
            this.volumeLabel.textContent = `Volume ${percentage}%`;
        }
    }

    renderTrackList() {
        this.trackList.innerHTML = "";
        const tracks = this.engine.tracks;
        this.trackCount.textContent = `${tracks.length} ${tracks.length === 1 ? "track" : "tracks"}`;

        tracks.forEach((track, index) => {
            const item = document.createElement("li");
            const button = document.createElement("button");
            
            button.type = "button";
            button.className = "track-button";
            button.dataset.index = String(index);
            button.setAttribute("aria-label", `Play ${track.title} by ${track.artist}`);

            const num = document.createElement("span");
            num.className = "track-num";
            num.textContent = String(index + 1);

            const meta = document.createElement("span");
            meta.className = "track-meta";
            
            const title = document.createElement("span");
            title.className = "track-title";
            title.textContent = track.title;
            
            const line = document.createElement("span");
            line.className = "track-line";
            line.textContent = track.artist;
            
            meta.append(title, line);

            const state = document.createElement("span");
            state.className = "track-state";
            
            button.append(num, meta, state);
            button.addEventListener("click", () => {
                if (index === this.engine.currentIndex) {
                    this.engine.togglePlayback();
                } else {
                    this.engine.loadTrack(index, true);
                }
            });

            item.appendChild(button);
            this.trackList.appendChild(item);
        });

        this.updateTrackButtons();
    }

    updateTrackButtons() {
        const buttons = this.trackList.querySelectorAll(".track-button");
        buttons.forEach((button) => {
            const index = Number(button.dataset.index);
            const isActive = index === this.engine.currentIndex;
            const state = button.querySelector(".track-state");

            button.classList.toggle("track-button-active", isActive);
            button.setAttribute("aria-current", isActive ? "true" : "false");

            if (!state) return;

            if (isActive && !this.engine.audio.paused) {
                state.textContent = "▶ Playing";
            } else if (isActive) {
                state.textContent = "●";
            } else {
                state.textContent = "";
            }
        });
    }
}
