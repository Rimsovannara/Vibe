export class Bridge {
    constructor(engine, ui) {
        this.engine = engine;
        this.ui = ui;
        
        this._setupAndroidSync();
        this._setupMediaSession();
        this._bindEngineEvents();
    }

    _setupAndroidSync() {
        if (!window.VibeApp) return;

        const androidActions = document.getElementById("android-actions");
        const syncButton = document.getElementById("sync-android-button");
        
        if (androidActions && syncButton) {
            androidActions.style.display = "block";
            syncButton.addEventListener("click", () => {
                window.VibeApp.requestDeviceAudio();
                this.ui.setStatus("Requesting device audio...");
            });
        }
        
        // This is called from Java
        window.onAndroidAudioSync = (syncedTracks) => {
            const currentTrack = this.engine.currentTrack;
            const isPlayingDeviceSong = currentTrack && currentTrack._fromDevice;
            const wasPlaying = !this.engine.audio.paused;

            // Remove all previously-synced device songs first, keeping only built-in tracks
            let tracks = this.engine.tracks.filter(t => !t._fromDevice);

            if (syncedTracks && syncedTracks.length > 0) {
                const deviceTracks = syncedTracks.map(t => ({ ...t, _fromDevice: true }));
                tracks = [...tracks, ...deviceTracks];
                this.engine.setTracks(tracks);
                
                if (isPlayingDeviceSong || this.engine.currentIndex >= tracks.length) {
                    this.engine.loadTrack(0, wasPlaying);
                }
                this.ui.setStatus(`Synced ${deviceTracks.length} songs from your device`);
            } else {
                this.engine.setTracks(tracks);
                if (isPlayingDeviceSong || this.engine.currentIndex >= tracks.length) {
                    this.engine.loadTrack(0, wasPlaying);
                }
                this.ui.setStatus("No music found on device");
            }
        };
        
        // Setup global VibePlayerInstance to support native media button commands
        window.VibePlayerInstance = this.engine;
    }

    _setupMediaSession() {
        if (!("mediaSession" in navigator) || typeof navigator.mediaSession.setActionHandler !== "function") {
            return;
        }

        const handlers = {
            play: () => this.engine.play(),
            pause: () => this.engine.pause(),
            previoustrack: () => this.engine.changeTrack(-1),
            nexttrack: () => this.engine.changeTrack(1),
            seekbackward: (details) => {
                const offset = details?.seekOffset ?? 10;
                this.engine.seekTo(this.engine.audio.currentTime - offset);
            },
            seekforward: (details) => {
                const offset = details?.seekOffset ?? 10;
                this.engine.seekTo(this.engine.audio.currentTime + offset);
            },
            seekto: (details) => {
                if (details?.seekTime !== undefined) {
                    this.engine.seekTo(details.seekTime);
                }
            }
        };

        Object.entries(handlers).forEach(([action, handler]) => {
            try { navigator.mediaSession.setActionHandler(action, handler); } 
            catch (error) {}
        });
    }

    _bindEngineEvents() {
        this.engine.addEventListener("trackLoaded", (e) => {
            const track = e.detail.track;
            
            // Sync MediaSession metadata
            if ("mediaSession" in navigator && typeof MediaMetadata === "function") {
                const artUrl = new URL(track.src.replace('/audio/', '/art/'), window.location.href).href;
                navigator.mediaSession.metadata = new MediaMetadata({
                    title: track.title,
                    artist: track.artist,
                    album: "Vibe",
                    artwork: [
                        { src: artUrl, sizes: "512x512", type: "image/jpeg" },
                        { src: new URL("./assets/icons/icon.svg", window.location.href).href, sizes: "any", type: "image/svg+xml" }
                    ]
                });
            }

            // Sync with Android Service
            if (window.VibeApp && window.VibeApp.updateMetadata) {
                window.VibeApp.updateMetadata(track.title, track.artist);
            }
        });

        this.engine.addEventListener("play", () => this._updateState(true));
        this.engine.addEventListener("pause", () => this._updateState(false));

        this.engine.addEventListener("favoriteChanged", (e) => {
            if (window.VibeApp && window.VibeApp.toggleFavorite) {
                const track = e.detail.track;
                // use synthetic ID or the synced device ID
                window.VibeApp.toggleFavorite(track.id || track.src.split('/').pop(), e.detail.isFavorite);
            }
        });
        
        const posUpdater = () => this._updatePositionState();
        this.engine.addEventListener("ratechange", posUpdater);
        this.engine.addEventListener("durationchange", posUpdater);
        this.engine.audio.addEventListener("timeupdate", () => {
            // only update native position state occasionally to avoid thrashing
            if (Math.random() < 0.05) posUpdater(); 
        });
    }

    _updateState(isPlaying) {
        if ("mediaSession" in navigator) {
            navigator.mediaSession.playbackState = isPlaying ? "playing" : "paused";
        }
        if (window.VibeApp && window.VibeApp.updatePlaybackState) {
            window.VibeApp.updatePlaybackState(isPlaying);
        }
        this._updatePositionState();
    }

    _updatePositionState() {
        const audio = this.engine.audio;
        if (!("mediaSession" in navigator) || !navigator.mediaSession.setPositionState || !Number.isFinite(audio.duration) || audio.duration <= 0) {
            return;
        }

        try {
            navigator.mediaSession.setPositionState({
                duration: audio.duration,
                playbackRate: audio.playbackRate || 1,
                position: audio.currentTime
            });
        } catch (error) {}
    }
}
