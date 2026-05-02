import { clamp } from './utils.js';

export class AudioEngine extends EventTarget {
    constructor(audioElement) {
        super();
        this.audio = audioElement;
        this.tracks = [];
        this.currentIndex = 0;
        this.isShuffle = false;
        
        this._setupAudioListeners();
    }

    setTracks(tracks) {
        this.tracks = tracks;
        this.dispatchEvent(new CustomEvent('tracksChanged', { detail: { tracks } }));
    }

    get currentTrack() {
        return this.tracks[this.currentIndex];
    }

    loadTrack(index, autoplay = false) {
        if (this.tracks.length === 0) return;
        this.currentIndex = (index + this.tracks.length) % this.tracks.length;
        const track = this.tracks[this.currentIndex];
        
        this.audio.src = track.src;
        this.audio.load();

        this.dispatchEvent(new CustomEvent('trackLoaded', { detail: { track, index: this.currentIndex } }));

        if (autoplay) {
            this.play();
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
            this.play();
        } else {
            this.pause();
        }
    }

    play() {
        return this.audio.play().catch((err) => {
            this.dispatchEvent(new CustomEvent('playbackBlocked', { detail: err }));
        });
    }

    pause() {
        this.audio.pause();
    }

    setVolume(vol) {
        this.audio.volume = clamp(vol, 0, 1);
    }

    getVolume() {
        return this.audio.volume;
    }

    seekTo(time) {
        if (Number.isFinite(this.audio.duration)) {
            this.audio.currentTime = clamp(time, 0, this.audio.duration);
        }
    }

    toggleLoop() {
        this.audio.loop = !this.audio.loop;
        this.dispatchEvent(new CustomEvent('loopChanged', { detail: { isLooping: this.audio.loop } }));
        return this.audio.loop;
    }

    toggleShuffle() {
        this.isShuffle = !this.isShuffle;
        this.dispatchEvent(new CustomEvent('shuffleChanged', { detail: { isShuffle: this.isShuffle } }));
        return this.isShuffle;
    }

    _setupAudioListeners() {
        this.audio.addEventListener("play", () => this.dispatchEvent(new Event("play")));
        this.audio.addEventListener("pause", () => this.dispatchEvent(new Event("pause")));
        this.audio.addEventListener("waiting", () => this.dispatchEvent(new Event("waiting")));
        this.audio.addEventListener("canplay", () => this.dispatchEvent(new Event("canplay")));
        this.audio.addEventListener("ratechange", () => this.dispatchEvent(new Event("ratechange")));
        this.audio.addEventListener("durationchange", () => this.dispatchEvent(new Event("durationchange")));
        this.audio.addEventListener("loadedmetadata", () => this.dispatchEvent(new Event("loadedmetadata")));
        this.audio.addEventListener("error", () => this.dispatchEvent(new Event("error")));
        
        this.audio.addEventListener("ended", () => {
            this.dispatchEvent(new Event("ended"));
            if (!this.audio.loop && this.tracks.length > 1) {
                this.changeTrack(1);
            }
        });
    }
}
