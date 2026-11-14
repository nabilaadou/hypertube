"use client";

import { useEffect, useRef, useState } from "react";
import { cinemaMedia } from "../data/movies";

type Playback = "paused" | "playing" | "unavailable";

const control = "grid min-h-11 cursor-pointer place-items-center rounded-full border border-white/40 bg-black/65 px-4 font-mono text-xs uppercase";

export function MovieEdit() {
  const videoRef = useRef<HTMLVideoElement>(null);
  const [playback, setPlayback] = useState<Playback>("paused");
  const [muted, setMuted] = useState(true);

  useEffect(() => {
    const video = videoRef.current;
    if (!video) return;

    const motion = window.matchMedia("(prefers-reduced-motion: reduce)");
    if (!motion.matches) void video.play().catch(() => {});
    function pauseForReducedMotion(event: MediaQueryListEvent) {
      if (event.matches) videoRef.current?.pause();
    }
    motion.addEventListener("change", pauseForReducedMotion);
    return () => motion.removeEventListener("change", pauseForReducedMotion);
  }, []);

  function togglePlayback() {
    const video = videoRef.current;
    if (!video) return;

    if (video.paused) void video.play().catch(() => {});
    else video.pause();
  }

  function toggleSound() {
    const video = videoRef.current;
    if (video) video.muted = !video.muted;
  }

  return (
    <section id="opening" aria-labelledby="opening-title" className="relative isolate min-h-svh overflow-hidden bg-theater text-paper">
      <video
        ref={videoRef}
        className="absolute inset-0 size-full object-cover object-center"
        src={cinemaMedia.edit}
        poster={cinemaMedia.poster}
        muted
        loop
        playsInline
        preload="metadata"
        aria-label="Hypertube movie edit"
        onPlay={() => setPlayback("playing")}
        onPause={() => setPlayback((state) => state === "unavailable" ? state : "paused")}
        onVolumeChange={(event) => setMuted(event.currentTarget.muted)}
        onError={() => setPlayback("unavailable")}
      />
      <div aria-hidden="true" className="pointer-events-none absolute inset-0 bg-linear-to-b from-black/75 via-transparent to-black/80" />
      {playback === "unavailable" && (
        <p role="status" className="absolute inset-0 grid place-items-center bg-theater/80 px-page text-center">
          The movie edit is unavailable. Please try again later.
        </p>
      )}

      <header className="absolute inset-x-0 top-0 z-10 grid grid-cols-[1fr_auto_1fr] items-center px-page py-6 md:py-8">
        <svg aria-hidden="true" viewBox="0 0 40 40" fill="none" className="size-8">
          <path d="M6 6v28m28-28v28M6 12l14 9 14-9M6 22l14 9 14-9" stroke="currentColor" strokeWidth="3.5" strokeLinecap="square" />
        </svg>
        <h1 id="opening-title" className="font-display text-3xl leading-none font-black tracking-wide uppercase md:text-4xl">Hypertube</h1>
        <details className="relative justify-self-end" onKeyDown={(event) => {
          if (event.key === "Escape" && event.currentTarget.open) {
            event.currentTarget.open = false;
            event.currentTarget.querySelector("summary")?.focus();
          }
        }}>
          <summary aria-label="Navigation" className="grid size-11 cursor-pointer list-none place-content-center gap-1.5 rounded-full bg-black/60 [&::-webkit-details-marker]:hidden">
            <span aria-hidden="true" className="h-px w-5 -skew-x-30 bg-current" />
            <span aria-hidden="true" className="ml-1 h-px w-5 -skew-x-30 bg-current" />
          </summary>
          <nav aria-label="Landing page" className="absolute top-full right-0 mt-3 min-w-44 rounded-card bg-ink p-2 shadow-lg">
            <a className="block rounded px-4 py-3 hover:bg-quote" href="#opening">Opening edit</a>
            <a className="block rounded px-4 py-3 hover:bg-quote" href="#api-status">API status</a>
          </nav>
        </details>
      </header>

      <div className="absolute inset-x-0 bottom-0 grid grid-cols-[1fr_auto_1fr] items-end gap-2 px-page pb-6 md:pb-8">
        <div>
          {playback !== "unavailable" && (
            <button type="button" onClick={togglePlayback} className={control}>
              {playback === "playing" ? "Pause" : "Play"}
            </button>
          )}
        </div>
        <a href="#api-status" className={`${control} gap-1 py-2`}>
          Scroll
          <span aria-hidden="true" className="size-2 rotate-45 border-r border-b" />
        </a>
        <div className="justify-self-end">
          {playback !== "unavailable" && (
            <button type="button" aria-label="Sound" aria-pressed={!muted} onClick={toggleSound} className={control}>
              {muted ? "Sound off" : "Sound on"}
            </button>
          )}
        </div>
      </div>
    </section>
  );
}
