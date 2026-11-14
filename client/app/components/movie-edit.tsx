"use client";

import { useGSAP } from "@gsap/react";
import gsap from "gsap";
import { ScrollTrigger } from "gsap/ScrollTrigger";
import dynamic from "next/dynamic";
import Image from "next/image";
import { useEffect, useRef, useState } from "react";
import { cinemaMedia, draftMovies } from "../data/movies";
import { useCinemaReveal } from "./use-cinema-reveal";

const CinemaScene = dynamic(() => import("./cinema-scene"), { ssr: false });

const featured = draftMovies[0];
const cinemaHoldViewports = 1;
const handoffViewports = 1.35;
const timelineHoldViewports = 0.5;

gsap.registerPlugin(useGSAP, ScrollTrigger);

type Playback = "paused" | "playing" | "unavailable";

const control = "grid min-h-11 cursor-pointer place-items-center rounded-full border border-white/40 bg-black/65 px-4 font-mono text-xs uppercase";

export function MovieEdit() {
  const { stage, phase, reveal } = useCinemaReveal();
  const runway = useRef<HTMLDivElement>(null);
  const videoRef = useRef<HTMLVideoElement>(null);
  const [playback, setPlayback] = useState<Playback>("paused");
  const [muted, setMuted] = useState(true);

  useGSAP(() => {
    const section = stage.current;
    const scrollArea = runway.current;
    if (!section || !scrollArea) return;

    const motion = gsap.matchMedia();
    motion.add({
      standard: "(prefers-reduced-motion: no-preference)",
      reduced: "(prefers-reduced-motion: reduce)",
    }, ({ conditions }) => {
      const reduced = conditions?.reduced;
      const animation = gsap.timeline({
        scrollTrigger: {
          trigger: scrollArea,
          start: () => `top+=${section.clientHeight * cinemaHoldViewports} top`,
          end: () => `+=${section.clientHeight * handoffViewports}`,
          scrub: true,
        },
      });

      animation.to(section, { "--wipe": 1, duration: 1, ease: reduced ? "steps(1)" : "none" });
      if (reduced) {
        animation.set(section, { "--poster-travel": 1, "--timeline-labels": 1 }, 1);
      } else {
        animation.to(section, { "--poster-travel": 1, duration: 0.73, ease: "power2.inOut" }, 0.2);
        animation.to(section, { "--timeline-labels": 1, duration: 0.34, ease: "power1.out" }, 0.57);
      }
    });
    return () => motion.revert();
  }, { scope: runway });

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
    <div ref={runway} style={{ minHeight: `${(1 + cinemaHoldViewports + handoffViewports + timelineHoldViewports) * 100}svh` }}>
      <section id="opening" aria-labelledby="opening-title" ref={stage} data-phase={phase} className="cinema-stage sticky top-0 isolate h-svh overflow-hidden bg-theater text-paper">
        <div aria-hidden="true" className="cinema-room" style={{ backgroundImage: `url(${cinemaMedia.theaterImage})` }} />
        <div className="cinema-screen">
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

          {playback === "unavailable" && (
            <p role="status" className="absolute inset-0 grid place-items-center bg-theater/80 px-page text-center">
              The movie edit is unavailable. Please try again later.
            </p>
          )}
        </div>
        <CinemaScene videoRef={videoRef} active={phase === "cinema" && playback !== "unavailable"} />
        <div aria-hidden="true" className="cinema-shade pointer-events-none absolute inset-0 bg-linear-to-b from-black/75 via-transparent to-black/80" />

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

        <section aria-labelledby="timeline-title" className="cinema-timeline absolute inset-0">
          <div className="cinema-timeline-grid absolute inset-0" aria-hidden="true" />
          <h2 id="timeline-title" className="cinema-timeline-label absolute top-[10%] left-page font-display text-[clamp(3.5rem,14vw,15rem)] leading-none font-black whitespace-nowrap uppercase text-timeline">
            {featured.award}
          </h2>
          <p className="cinema-timeline-label absolute bottom-[10%] left-page font-display text-[clamp(3rem,7vw,7rem)] leading-none font-bold text-paper/35">
            {featured.awardYear}
          </p>
        </section>

        {phase === "cinema" && (
          <figure className="cinema-featured-poster absolute grid gap-2 drop-shadow-lg">
            <Image
              src={featured.poster}
              alt=""
              width={176}
              height={264}
              unoptimized
              className="aspect-[2/3] h-auto w-full rounded-card border border-gold bg-ink object-cover"
            />
            <figcaption className="font-mono text-[clamp(0.625rem,1.1vw,0.875rem)] text-paper">
              {featured.title}
            </figcaption>
          </figure>
        )}

        <div className="absolute inset-x-0 bottom-0 grid grid-cols-[1fr_auto_1fr] items-end gap-2 px-page pb-6 md:pb-8">
          <div>
            {playback !== "unavailable" && (
              <button type="button" onClick={togglePlayback} className={control}>
                {playback === "playing" ? "Pause" : "Play"}
              </button>
            )}
          </div>
          <div>
            {phase !== "cinema" && (
              <button type="button" aria-disabled={phase !== "fullscreen"} onClick={reveal} className={`${control} cinema-cue gap-1 py-2`}>
                Scroll
                <span aria-hidden="true" className="size-2 rotate-45 border-r border-b" />
              </button>
            )}
          </div>
          <div className="justify-self-end">
            {playback !== "unavailable" && (
              <button type="button" aria-label="Sound" aria-pressed={!muted} onClick={toggleSound} className={control}>
                {muted ? "Sound off" : "Sound on"}
              </button>
            )}
          </div>
        </div>
      </section>
    </div>
  );
}
