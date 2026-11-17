"use client";

import { useEffect, useRef, useState } from "react";

const trailerFiles: Record<string, string> = {
  aQwVQzs9pHk: "dead-poets-society.mp4",
  "6iB21hsprAQ": "silence-of-the-lambs.mp4",
  "6MUcuqbGTxc": "before-sunrise.mp4",
  H2NhAmDv1dw: "taste-of-cherry.mp4",
  "1GiLxkDK8sI": "eternal-sunshine.mp4",
  PsD0NpFSADM: "500-days-of-summer.mp4",
  "5jaI1XOB-bs": "black-swan.mp4",
  "7OIFdWk83no": "about-time.mp4",
  "0pdqf4P9MB8": "la-la-land.mp4",
  "6zmKcUa4Xxk": "the-drama.mp4",
};

export function TrailerPlayer({ trailerId, title }: { trailerId: string; title: string }) {
  const videoRef = useRef<HTMLVideoElement>(null);
  const fullscreenRef = useRef<HTMLDivElement>(null);
  const [ready, setReady] = useState(false);
  const [failed, setFailed] = useState(false);
  const [playing, setPlaying] = useState(false);
  const [muted, setMuted] = useState(false);
  const [currentTime, setCurrentTime] = useState(0);
  const [duration, setDuration] = useState(0);
  const [fullscreen, setFullscreen] = useState(false);
  const trailer = trailerFiles[trailerId];

  useEffect(() => {
    const onFullscreenChange = () => setFullscreen(document.fullscreenElement === fullscreenRef.current);
    document.addEventListener("fullscreenchange", onFullscreenChange);
    return () => document.removeEventListener("fullscreenchange", onFullscreenChange);
  }, []);

  function togglePlayback() {
    const video = videoRef.current;
    if (!video) return;
    if (video.paused) void video.play().catch(() => setPlaying(false));
    else video.pause();
  }

  function toggleSound() {
    const video = videoRef.current;
    if (!video) return;
    video.muted = !video.muted;
  }

  function toggleFullscreen() {
    if (document.fullscreenElement === fullscreenRef.current) void document.exitFullscreen();
    else if (fullscreenRef.current?.requestFullscreen) void fullscreenRef.current.requestFullscreen().catch(() => {});
  }

  return (
    <div ref={fullscreenRef} className="trailer-player">
      <div className="trailer-player-video">
        {trailer && (
          // biome-ignore lint/a11y/useMediaCaption: These supplied trailers do not include caption tracks.
          <video
            ref={videoRef}
            src={`/media/trailers/${trailer}`}
            aria-label={`${title} trailer`}
            className="size-full object-contain"
            autoPlay
            playsInline
            preload="metadata"
            onLoadedMetadata={(event) => {
              setDuration(Number.isFinite(event.currentTarget.duration) ? event.currentTarget.duration : 0);
              setReady(true);
            }}
            onPlay={() => setPlaying(true)}
            onPause={() => setPlaying(false)}
            onEnded={() => setPlaying(false)}
            onVolumeChange={(event) => setMuted(event.currentTarget.muted)}
            onTimeUpdate={(event) => setCurrentTime(event.currentTarget.currentTime)}
            onError={() => setFailed(true)}
          />
        )}
        {(!trailer || failed) && (
          <p role="status" className="absolute inset-0 grid place-items-center bg-black px-8 text-center">
            This trailer is unavailable.
          </p>
        )}
      </div>
      <div className="trailer-player-controls">
        <button type="button" aria-label={playing ? "Pause trailer" : "Play trailer"} disabled={!ready || failed} onClick={togglePlayback}>
          <span aria-hidden="true" className="cinema-control-icon cinema-playback-icon" data-playing={playing} />
        </button>
        <button type="button" aria-label={muted ? "Unmute trailer" : "Mute trailer"} aria-pressed={!muted} disabled={!ready || failed} onClick={toggleSound}>
          <span aria-hidden="true" className="cinema-control-icon cinema-sound-icon" />
        </button>
        <input aria-label="Trailer progress" type="range" min={0} max={duration || 0} step="0.1" value={Math.min(currentTime, duration)} disabled={!ready || failed || !duration} onChange={(event) => {
          const time = Number(event.currentTarget.value);
          if (videoRef.current) videoRef.current.currentTime = time;
          setCurrentTime(time);
        }} />
        <button type="button" aria-label={fullscreen ? "Exit fullscreen" : "Enter fullscreen"} onClick={toggleFullscreen}>
          <svg aria-hidden="true" viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" strokeWidth="1.4"><path d="M4 9V4h5M15 4h5v5M20 15v5h-5M9 20H4v-5" /></svg>
        </button>
      </div>
    </div>
  );
}
