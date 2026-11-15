"use client";

import { useGSAP } from "@gsap/react";
import gsap from "gsap";
import { ScrollTrigger } from "gsap/ScrollTrigger";
import { useLenis } from "lenis/react";
import dynamic from "next/dynamic";
import Image from "next/image";
import { type CSSProperties, useCallback, useEffect, useRef, useState } from "react";
import { bestPictureWinners, cinemaMedia, type Movie } from "../data/movies";
import { projectVideo, type ScreenCorners } from "./cinema-projection";
import { TrailerDialog } from "./trailer-dialog";
import { useCinemaReveal } from "./use-cinema-reveal";

const CinemaScene = dynamic(() => import("./cinema-scene"), { ssr: false });

const featured = bestPictureWinners[0];
const cinemaHoldViewports = 1;
const handoffViewports = 1.35;
const timelineHoldViewports = 0.5;
const filmViewports = 0.7;
const lastFilm = bestPictureWinners.length - 1;
const timelineStart = cinemaHoldViewports + handoffViewports;

gsap.registerPlugin(useGSAP, ScrollTrigger);

type Playback = "paused" | "playing" | "unavailable";

const control = "grid min-h-11 cursor-pointer place-items-center rounded-full border border-white/40 bg-black/65 px-4 font-mono text-xs uppercase";

export function MovieEdit() {
  const videoRef = useRef<HTMLVideoElement>(null);
  const videoLayer = useRef<HTMLDivElement>(null);
  const worldLayer = useRef<HTMLDivElement>(null);
  const corners = useRef<ScreenCorners | null>(null);
  const revealProgress = useRef(0);

  const updateProjection = useCallback((progress: number) => {
    revealProgress.current = progress;
    const layer = videoLayer.current;
    const section = layer?.parentElement;
    const world = worldLayer.current;
    if (layer && section && world) {
      projectVideo({ stage: section, layer, world, corners: corners.current, progress });
    }
  }, []);

  const handleProjection = useCallback((points: ScreenCorners | null) => {
    corners.current = points;
    updateProjection(revealProgress.current);
  }, [updateProjection]);

  const { stage, phase } = useCinemaReveal(videoRef, updateProjection);
  const runway = useRef<HTMLDivElement>(null);
  const lenis = useLenis();
  const [filmIndex, setFilmIndex] = useState(0);
  const [timelineVisible, setTimelineVisible] = useState(false);
  const [trailer, setTrailer] = useState<Movie | null>(null);
  const trailerTrigger = useRef<HTMLButtonElement | null>(null);
  const resumeEdit = useRef(false);
  const [playback, setPlayback] = useState<Playback>("paused");
  const [muted, setMuted] = useState(true);

  useGSAP(() => {
    const section = stage.current;
    const scrollArea = runway.current;
    if (!section || !scrollArea) return;

    const resize = new ResizeObserver(() => updateProjection(revealProgress.current));
    resize.observe(section);

    function syncTimeline({ progress, start }: ScrollTrigger) {
      setFilmIndex(Math.round(progress * lastFilm));
      setTimelineVisible(window.scrollY >= start);
    }
    gsap.to(section, {
      "--track-index": lastFilm,
      ease: "none",
      scrollTrigger: {
        trigger: scrollArea,
        start: () => `top+=${section.clientHeight * timelineStart} top`,
        end: () => `+=${section.clientHeight * filmViewports * lastFilm}`,
        scrub: true,
        onUpdate: syncTimeline,
        onRefresh: syncTimeline,
      },
    });

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
        animation.to(section, { "--poster-turn": 89, duration: 0.32, ease: "power1.in" }, 0.2);
        animation.to(section, { "--poster-turn": -5, duration: 0.41, ease: "power2.out" }, 0.52);
        animation.to(section, { "--timeline-labels": 1, duration: 0.34, ease: "power1.out" }, 0.57);
      }
    });
    return () => {
      resize.disconnect();
      motion.revert();
    };
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

  function goToFilm(index: number) {
    const scrollArea = runway.current;
    const section = stage.current;
    if (!scrollArea || !section) return;

    const distance = timelineStart + filmViewports * index;
    const top = window.scrollY + scrollArea.getBoundingClientRect().top + section.clientHeight * distance + 1;
    if (lenis) lenis.scrollTo(top);
    else window.scrollTo({ top });
  }

  function openTrailer(movie: Movie, trigger: HTMLButtonElement) {
    trailerTrigger.current = trigger;
    const video = videoRef.current;
    resumeEdit.current = Boolean(video && !video.paused);
    video?.pause();
    lenis?.stop();
    setTrailer(movie);
  }

  function closeTrailer() {
    setTrailer(null);
    lenis?.start();
    trailerTrigger.current?.focus({ preventScroll: true });
    if (resumeEdit.current) void videoRef.current?.play().catch(() => {});
  }

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
    <div ref={runway} style={{ minHeight: `${(1 + cinemaHoldViewports + handoffViewports + timelineHoldViewports + filmViewports * lastFilm) * 100}svh` }}>
      <section id="opening" aria-labelledby="opening-title" ref={stage} data-phase={phase} className="cinema-stage sticky top-0 isolate h-svh overflow-hidden bg-theater text-paper">
        <div ref={worldLayer} className="cinema-world">
          <div aria-hidden="true" className="cinema-room" style={{ backgroundImage: `url(${cinemaMedia.theaterImage})` }} />
          <CinemaScene videoRef={videoRef} active={phase !== "fullscreen" && playback !== "unavailable"} onProjection={handleProjection} />
        </div>
        <div ref={videoLayer} className="cinema-screen">
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

        <header className={`absolute inset-x-0 top-0 z-10 ${phase === "cinema" ? "grid" : "sr-only"} grid-cols-[1fr_auto_1fr] items-center px-page py-6 md:py-8`} inert={phase !== "cinema"}>
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
          <div aria-hidden="true" className="cinema-year-track cinema-timeline-label">
            {bestPictureWinners.map((film, index) => (
              <span key={film.awardYear} style={{ "--index": index } as CSSProperties}>{film.awardYear}</span>
            ))}
          </div>
        </section>

        {phase === "cinema" && bestPictureWinners.map((movie, index) => (
          <figure
            key={movie.awardYear}
            className={`cinema-track-card ${index === 0 ? "cinema-featured-poster" : ""}`}
            style={{ "--index": index } as CSSProperties}
            inert={index !== 0 && ((!timelineVisible && index > 1) || Math.abs(filmIndex - index) > 2)}
          >
            <button
              type="button"
              tabIndex={index === filmIndex ? 0 : -1}
              aria-label={`Watch the ${movie.title} trailer`}
              onClick={(event) => openTrailer(movie, event.currentTarget)}
              className="group relative isolate aspect-[2/3] w-full cursor-pointer overflow-hidden rounded-card border border-gold bg-ink"
            >
              <span aria-hidden="true" className="absolute inset-0 grid place-items-center p-2 text-center font-display text-2xl">{movie.title}</span>
              <Image
                src={movie.poster}
                alt=""
                width={240}
                height={360}
                unoptimized
                loading={index < 3 ? "eager" : "lazy"}
                onError={(event) => { event.currentTarget.hidden = true; }}
                className="relative aspect-[2/3] h-auto w-full object-cover"
              />
              <span aria-hidden="true" className="absolute right-2 bottom-2 rounded-full bg-black/80 px-3 py-1 font-mono text-xs opacity-0 group-hover:opacity-100 group-focus-visible:opacity-100">Play trailer</span>
            </button>
            <figcaption className="grid gap-1 text-center font-mono text-[clamp(0.625rem,1.1vw,0.875rem)] text-paper">
              {movie.title}
              <span className="cinema-timeline-label text-muted">{movie.awardYear}</span>
            </figcaption>
          </figure>
        ))}

        {phase === "cinema" && timelineVisible && (
          <nav aria-label="Best Picture years" className="absolute inset-x-page bottom-[8%] z-[4] flex justify-start gap-1.5 overflow-x-auto md:justify-center md:gap-3" data-lenis-prevent-horizontal>
            {bestPictureWinners.map((film, index) => (
              <button
                key={film.awardYear}
                type="button"
                aria-current={filmIndex === index ? "true" : undefined}
                aria-label={`${film.awardYear}, ${film.title}`}
                onClick={() => goToFilm(index)}
                className="min-h-11 shrink-0 cursor-pointer border-t border-paper/30 bg-ink px-2 font-mono text-xs text-paper/70 hover:text-paper aria-[current=true]:border-gold aria-[current=true]:text-paper"
              >
                {film.awardYear}
              </button>
            ))}
          </nav>
        )}

        {trailer && <TrailerDialog movie={trailer} onClose={closeTrailer} />}

        {phase === "cinema" && playback !== "unavailable" && (
          <div className="absolute inset-x-0 bottom-0 z-[4] flex justify-between gap-2 px-page pb-6 md:pb-8">
            <button type="button" onClick={togglePlayback} className={control}>
              {playback === "playing" ? "Pause" : "Play"}
            </button>
            <button type="button" aria-label="Sound" aria-pressed={!muted} onClick={toggleSound} className={control}>
              {muted ? "Sound off" : "Sound on"}
            </button>
          </div>
        )}
      </section>
    </div>
  );
}
