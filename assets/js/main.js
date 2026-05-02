import { AudioEngine } from './AudioEngine.js';
import { UI } from './UI.js';
import { Bridge } from './Bridge.js';
import { hashString } from './utils.js';

const defaultTracks = [
    {
        title: "No One Noticed",
        artist: "The Marías",
        src: "./assets/audio/The Marías - No One Noticed.mp3",
        mood: "Late-night replay energy.",
        note: "A softer opener for a local collection that now stays in one clean track list.",
        accent: "#ff8a5b"
    },
    {
        title: "Illusionary Daytime x 室内系 TrackMaker [Extended Edit Version]",
        artist: "Reis",
        src: "./assets/audio/Illusionary Daytime x TrackMaker Extended Edit Version [8KI21GoNiuI].mp3",
        mood: "Fast, bright instrumental energy.",
        note: "Converted from the provided YouTube link and stored locally as an MP3 for Vibe.",
        accent: "#7ad7ff"
    },
    {
        title: "Me, Myself & I (Lyrics)",
        artist: "G-Eazy x Bebe Rexha",
        src: "./assets/audio/Me Myself and I [qiqllkchWTI].mp3",
        mood: "Hook-heavy late-night rap pop.",
        note: "Converted from the provided YouTube link and stored locally as an MP3 for Vibe.",
        accent: "#ffd166"
    },
    {
        title: "The Machine",
        artist: "Reed Wonder, Aurora Olivas",
        src: "./assets/audio/The Machine [BbgQ98LdIeM].mp3",
        mood: "Sharp, cinematic alt-pop energy.",
        note: "Converted from the provided YouTube link and stored locally as an MP3 for Vibe.",
        accent: "#90e0ef"
    },
    {
        title: "Cry",
        artist: "Cigarettes After Sex",
        src: "./assets/audio/Cry [3XqqkrJENB4].mp3",
        mood: "Slow, hazy midnight mood.",
        note: "Converted from the provided YouTube link and stored locally as an MP3 for Vibe.",
        accent: "#cdb4db"
    },
    {
        title: "Stitches (Lyrics)",
        artist: "Shawn Mendes",
        src: "./assets/audio/Stitches [a4MYXwA6oxo].mp3",
        mood: "Bright pop tension with a strong hook.",
        note: "Converted from the provided YouTube link and stored locally as an MP3 for Vibe.",
        accent: "#ffadad"
    },
    {
        title: "Treat You Better (Lyrics)",
        artist: "Shawn Mendes",
        src: "./assets/audio/Treat You Better [rspUJPuEwmg].mp3",
        mood: "Clean radio-pop singalong energy.",
        note: "Converted from the provided YouTube link and stored locally as an MP3 for Vibe.",
        accent: "#b8f2e6"
    },
    {
        title: "bye [Altare Remix]",
        artist: "Ariana Grande",
        src: "./assets/audio/bye Altare Remix [agneRtEe-t8].mp3",
        mood: "Glossy remix lift with late-night momentum.",
        note: "Converted from the provided YouTube link and stored locally as an MP3 for Vibe.",
        accent: "#f7cad0"
    },
    {
        title: "Self Aware (Lyrics)",
        artist: "Temper City",
        src: "./assets/audio/Self Aware [pGsgAOmkS40].mp3",
        mood: "Moody alt-pop with a tight pulse.",
        note: "Converted from the provided YouTube link and stored locally as an MP3 for Vibe.",
        accent: "#a9def9"
    },
    {
        title: "505",
        artist: "Arctic Monkeys",
        src: "./assets/audio/505 [MrmPDUvKyLs].mp3",
        mood: "Slow-build indie tension and release.",
        note: "Converted from the provided YouTube link and stored locally as an MP3 for Vibe.",
        accent: "#c3bef0"
    }
];

document.addEventListener("DOMContentLoaded", () => {
    const audioElement = document.getElementById("audio");
    if (!audioElement) return;

    audioElement.preload = "auto";
    audioElement.playsInline = true;
    audioElement.loop = false;
    audioElement.volume = 0.88;

    const engine = new AudioEngine(audioElement);
    const ui = new UI(engine);
    const bridge = new Bridge(engine, ui);

    const processedTracks = defaultTracks.map(t => ({
        ...t,
        id: hashString(t.title + t.artist)
    }));
    
    engine.setTracks(processedTracks);
    
    if (processedTracks.length > 0) {
        engine.loadTrack(0);
        engine.play();
    }

    if ("serviceWorker" in navigator && window.isSecureContext) {
        window.addEventListener("load", () => {
            navigator.serviceWorker.register("./sw.js").catch(() => {});
        });
    }
});
