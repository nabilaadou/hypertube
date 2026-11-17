"use client";

import { useEffect, useRef, useState } from "react";

type YouTubePlayer = {
  playVideo(): void;
  pauseVideo(): void;
  mute(): void;
  unMute(): void;
  isMuted(): boolean;
  seekTo(seconds: number, allowSeekAhead: boolean): void;
  getCurrentTime(): number;
  getDuration(): number;
  getIframe(): HTMLIFrameElement;
  destroy(): void;
};

type YouTubeEvent = { target: YouTubePlayer; data: number };
type YouTubeApi = {
  Player: new (element: HTMLElement, options: {
    host: string;
    videoId: string;
    width: string;
    height: string;
    playerVars: Record<string, string | number>;
    events: {
      onReady(event: YouTubeEvent): void;
      onStateChange(event: YouTubeEvent): void;
      onError(): void;
      onAutoplayBlocked(): void;
    };
  }) => YouTubePlayer;
};

declare global {
  interface Window {
    YT?: YouTubeApi;
    onYouTubeIframeAPIReady?: () => void;
  }
}

let apiPromise: Promise<YouTubeApi> | undefined;

function loadYouTubeApi(): Promise<YouTubeApi> {
  if (window.YT?.Player) return Promise.resolve(window.YT);
  if (apiPromise) return apiPromise;

  apiPromise = new Promise<YouTubeApi>((resolve, reject) => {
    const previous = window.onYouTubeIframeAPIReady;
    window.onYouTubeIframeAPIReady = () => {
      previous?.();
      if (window.YT?.Player) resolve(window.YT);
      else reject(new Error("YouTube player unavailable"));
    };
    const script = document.createElement("script");
    script.src = "https://www.youtube.com/iframe_api";
    script.onerror = () => reject(new Error("YouTube player unavailable"));
    document.head.appendChild(script);
  }).catch((error: unknown) => {
    apiPromise = undefined;
    throw error;
  });
  return apiPromise;
}

export function TrailerPlayer({ trailerId, title }: { trailerId: string; title: string }) {
  const mountRef = useRef<HTMLDivElement>(null);
  const playerRef = useRef<YouTubePlayer | null>(null);
  const fullscreenRef = useRef<HTMLDivElement>(null);
  const [ready, setReady] = useState(false);
  const [failed, setFailed] = useState(false);
  const [playing, setPlaying] = useState(false);
  const [muted, setMuted] = useState(false);
  const [currentTime, setCurrentTime] = useState(0);
  const [duration, setDuration] = useState(0);
  const [fullscreen, setFullscreen] = useState(false);

  useEffect(() => {
    let active = true;
    let player: YouTubePlayer | undefined;
    const mount = mountRef.current;
    if (!mount) return;
    const timer = window.setInterval(() => {
      if (!playerRef.current) return;
      setCurrentTime(playerRef.current.getCurrentTime());
      setDuration(playerRef.current.getDuration());
    }, 250);
    const timeout = window.setTimeout(() => {
      if (active && !playerRef.current) setFailed(true);
    }, 12000);

    void loadYouTubeApi().then((api) => {
      if (!active) return;
      const target = document.createElement("div");
      mount.appendChild(target);
      player = new api.Player(target, {
        host: "https://www.youtube-nocookie.com",
        videoId: trailerId,
        width: "100%",
        height: "100%",
        playerVars: { autoplay: 1, controls: 0, playsinline: 1, rel: 0, origin: window.location.origin },
        events: {
          onReady(event) {
            if (!active) return;
            playerRef.current = event.target;
            event.target.getIframe().title = `${title} trailer`;
            window.clearTimeout(timeout);
            setMuted(event.target.isMuted());
            setReady(true);
          },
          onStateChange(event) {
            if (active) setPlaying(event.data === 1);
          },
          onError() {
            if (active) setFailed(true);
          },
          onAutoplayBlocked() {
            if (active) setPlaying(false);
          },
        },
      });
    }).catch(() => {
      if (active) setFailed(true);
    });
    const onFullscreenChange = () => setFullscreen(document.fullscreenElement === fullscreenRef.current);
    document.addEventListener("fullscreenchange", onFullscreenChange);
    return () => {
      active = false;
      window.clearInterval(timer);
      window.clearTimeout(timeout);
      document.removeEventListener("fullscreenchange", onFullscreenChange);
      playerRef.current = null;
      player?.destroy();
    };
  }, [trailerId, title]);

  function toggleSound() {
    const player = playerRef.current;
    if (!player) return;
    if (player.isMuted()) player.unMute();
    else player.mute();
    setMuted(player.isMuted());
  }

  function toggleFullscreen() {
    if (document.fullscreenElement) void document.exitFullscreen();
    else if (fullscreenRef.current) void fullscreenRef.current.requestFullscreen();
  }

  return (
    <div ref={fullscreenRef} className="trailer-player">
      <div className="trailer-player-video">
        <div ref={mountRef} className="size-full" />
        {failed && (
          <div role="status" className="absolute inset-0 flex flex-col items-center justify-center gap-3 bg-black px-8 text-center">
            <p>The trailer is unavailable here.</p>
            <a href={`https://www.youtube.com/watch?v=${trailerId}`} target="_blank" rel="noreferrer" className="text-sm underline">Watch on YouTube</a>
          </div>
        )}
      </div>
      <div className="trailer-player-controls">
        <button type="button" aria-label={playing ? "Pause trailer" : "Play trailer"} disabled={!ready || failed} onClick={() => playing ? playerRef.current?.pauseVideo() : playerRef.current?.playVideo()}>
          <span aria-hidden="true" className="cinema-control-icon cinema-playback-icon" data-playing={playing} />
        </button>
        <button type="button" aria-label={muted ? "Unmute trailer" : "Mute trailer"} aria-pressed={!muted} disabled={!ready || failed} onClick={toggleSound}>
          <span aria-hidden="true" className="cinema-control-icon cinema-sound-icon" />
        </button>
        <input aria-label="Trailer progress" type="range" min={0} max={duration || 0} step="0.1" value={Math.min(currentTime, duration)} disabled={!ready || failed || !duration} onChange={(event) => {
          const time = Number(event.currentTarget.value);
          playerRef.current?.seekTo(time, true);
          setCurrentTime(time);
        }} />
        <button type="button" aria-label={fullscreen ? "Exit fullscreen" : "Enter fullscreen"} onClick={toggleFullscreen}>
          <svg aria-hidden="true" viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" strokeWidth="1.4"><path d="M4 9V4h5M15 4h5v5M20 15v5h-5M9 20H4v-5" /></svg>
        </button>
      </div>
    </div>
  );
}
