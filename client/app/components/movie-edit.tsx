"use client";

import { useGSAP } from "@gsap/react";
import gsap from "gsap";
import { ScrollTrigger } from "gsap/ScrollTrigger";
import { useLenis } from "lenis/react";
import dynamic from "next/dynamic";
import Image from "next/image";
import { type CSSProperties, useCallback, useEffect, useRef, useState } from "react";
import { bestPictureWinners, cinemaMedia, type Movie } from "../data/movies";
import type { createCinemaPaperSurface, PaperPose } from "./cinema-paper-surface";
import { projectVideo, type ScreenCorners } from "./cinema-projection";
import { TrailerDialog } from "./trailer-dialog";
import { useCinemaReveal } from "./use-cinema-reveal";

const CinemaScene = dynamic(() => import("./cinema-scene"), { ssr: false });

const featured = bestPictureWinners[0];
const handoffViewports = 1.8;
const timelineHoldViewports = 0.5;
const filmViewports = 0.7;
const lastFilm = bestPictureWinners.length - 1;
const timelineStart = handoffViewports;
const paperLiftOff = 0.001;
const timelineCardHeights = [15, 37, 9, 32, 20];

gsap.registerPlugin(useGSAP, ScrollTrigger);

type Playback = "paused" | "playing" | "unavailable";

const control = "grid min-h-11 cursor-pointer place-items-center rounded-full border border-white/40 bg-black/65 px-4 font-mono text-xs uppercase";

export function MovieEdit() {
  const videoRef = useRef<HTMLVideoElement>(null);
  const videoLayer = useRef<HTMLDivElement>(null);
  const worldLayer = useRef<HTMLDivElement>(null);
  const paperCanvas = useRef<HTMLCanvasElement>(null);
  const paperSurface = useRef<ReturnType<typeof createCinemaPaperSurface>>(null);
  const paperPose = useRef<PaperPose>({ curl: 0, twist: 0, ripple: 0, turn: 0 });
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
  const [wallPosterCorners, setWallPosterCorners] = useState<ScreenCorners | null>(null);
  const [wallArtworkVisible, setWallArtworkVisible] = useState(true);
  const [filmIndex, setFilmIndex] = useState(0);
  const [timelineVisible, setTimelineVisible] = useState(false);
  const [trailer, setTrailer] = useState<Movie | null>(null);
  const trailerTrigger = useRef<HTMLButtonElement | null>(null);
  const resumeEdit = useRef(false);
  const [playback, setPlayback] = useState<Playback>("paused");
  const [muted, setMuted] = useState(true);
  const [lightsOn, setLightsOn] = useState(false);

  const wallPoster = wallPosterCorners && {
    left: Math.min(...wallPosterCorners.map(({ x }) => x)),
    right: Math.max(...wallPosterCorners.map(({ x }) => x)),
    top: Math.min(...wallPosterCorners.map(({ y }) => y)),
    bottom: Math.max(...wallPosterCorners.map(({ y }) => y)),
  };

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
          start: "top top",
          end: () => `+=${section.clientHeight * handoffViewports}`,
          scrub: true,
        },
      });

      animation.to(section, { "--wipe": 1, duration: 1, ease: reduced ? "steps(1)" : "none" });
      if (reduced) {
        animation.set(section, {
          "--poster-travel": 1,
          "--timeline-labels": 1,
          "--poster-caption": 1,
          "--featured-poster-opacity": 1,
          "--wall-poster-visibility": "hidden",
          "--featured-poster-visibility": "visible",
        }, 1);
      } else {
        const pose = paperPose.current;
        let onWall = true;
        animation.eventCallback("onUpdate", () => {
          const nextOnWall = animation.time() < paperLiftOff;
          if (nextOnWall !== onWall) {
            onWall = nextOnWall;
            setWallArtworkVisible(onWall);
          }
          section.style.setProperty("--poster-turn", String(pose.turn));
          section.style.setProperty("--poster-face", pose.turn > 90 && pose.turn < 270 ? "-1" : "1");
          if (section.style.getPropertyValue("--poster-paper") === "1") paperSurface.current?.draw(pose);
        });
        animation
          .set(section, { "--wall-poster-visibility": "hidden", "--featured-poster-visibility": "visible", "--featured-poster-opacity": 1, "--poster-paper": 1 }, paperLiftOff)
          .to(section, { "--poster-travel": 1, duration: 0.98, ease: "sine.out" }, paperLiftOff)
          .to(pose, { turn: 355, duration: 0.61, ease: "power2.inOut" }, 0.09)
          .to(section, { "--poster-lift": 10, "--poster-pitch": 14, "--poster-roll": -14, duration: 0.24, ease: "sine.out" }, paperLiftOff)
          .to(pose, { curl: 1, twist: 0.75, ripple: 0.7, duration: 0.24, ease: "sine.out" }, paperLiftOff)
          .to(section, { "--poster-lift": 4, "--poster-pitch": -13, "--poster-roll": 11, duration: 0.3, ease: "sine.inOut" }, 0.241)
          .to(pose, { curl: -0.92, twist: -0.85, ripple: -0.85, duration: 0.3, ease: "sine.inOut" }, 0.241)
          .to(section, { "--poster-lift": 5.5, "--poster-pitch": 6, "--poster-roll": -8, duration: 0.22, ease: "sine.inOut" }, 0.541)
          .to(pose, { curl: -0.25, twist: 0.35, ripple: 0.4, duration: 0.22, ease: "sine.inOut" }, 0.541)
          .to(section, { "--poster-lift": 0, "--poster-pitch": 0, "--poster-roll": 0, duration: 0.229, ease: "sine.inOut" }, 0.761)
          .to(pose, { curl: 0, twist: 0, ripple: 0, duration: 0.229, ease: "sine.inOut" }, 0.761)
          .to(section, { "--timeline-labels": 1, duration: 0.23, ease: "power1.out" }, 0.75)
          .to(section, { "--poster-caption": 1, duration: 0.06, ease: "power1.out" }, 0.94)
          .set(section, { "--poster-paper": 0 }, 0.99);
        return () => {
          section.style.removeProperty("--poster-turn");
          section.style.removeProperty("--poster-face");
          setWallArtworkVisible(true);
        };
      }
    });
    return () => {
      resize.disconnect();
      motion.revert();
    };
  }, { scope: runway });

  useEffect(() => {
    const canvas = paperCanvas.current;
    if (phase !== "cinema" || !canvas) return;

    const motion = window.matchMedia("(prefers-reduced-motion: reduce)");
    if (motion.matches) return;
    let active = true;
    let textureReady = false;

    function showSurface() {
      if (!active || !textureReady || !canvas) return;
      paperSurface.current?.draw(paperPose.current);
      canvas.dataset.ready = "true";
    }
    function hideSurface(event: Event) {
      event.preventDefault();
      if (canvas) delete canvas.dataset.ready;
    }

    void import("./cinema-paper-surface").then(({ createCinemaPaperSurface }) => {
      if (!active) return;
      paperSurface.current = createCinemaPaperSurface(canvas, featured.poster, () => {
        textureReady = true;
        showSurface();
      });
      canvas.addEventListener("webglcontextlost", hideSurface);
      canvas.addEventListener("webglcontextrestored", showSurface);
    }).catch(() => {});

    return () => {
      active = false;
      delete canvas.dataset.ready;
      canvas.removeEventListener("webglcontextlost", hideSurface);
      canvas.removeEventListener("webglcontextrestored", showSurface);
      paperSurface.current?.dispose();
      paperSurface.current = null;
    };
  }, [phase]);

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
    <div ref={runway} style={{ minHeight: `${(1 + timelineStart + timelineHoldViewports + filmViewports * lastFilm) * 100}svh` }}>
      <section id="opening" aria-labelledby="opening-title" ref={stage} data-phase={phase} data-timeline-active={timelineVisible} className="cinema-stage sticky top-0 isolate h-svh overflow-hidden bg-theater text-paper">
        <div ref={worldLayer} className="cinema-world" data-lights-on={lightsOn}>
          <div aria-hidden="true" className="cinema-room" style={{ backgroundImage: `url(${cinemaMedia.theaterImage})` }} />
          <CinemaScene
            videoRef={videoRef}
            active={phase !== "fullscreen" && playback !== "unavailable"}
            lightsOn={lightsOn}
            wallArtworkVisible={wallArtworkVisible}
            onProjection={handleProjection}
            onWallPosterProjection={setWallPosterCorners}
          />
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
          <h1 id="opening-title" className="cinema-stage-brand font-display text-3xl leading-none font-black tracking-wide uppercase md:text-4xl">Hypertube</h1>
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
          <h2 id="timeline-title" className="cinema-timeline-heading cinema-timeline-label">
            {featured.award}
          </h2>
          <div aria-hidden="true" className="cinema-year-track cinema-timeline-label">
            {bestPictureWinners.map((film, index) => (
              <span key={film.awardYear} style={{ "--index": index } as CSSProperties}>{film.awardYear}</span>
            ))}
          </div>
          <p aria-hidden="true" className="cinema-timeline-exit-copy hypertube-statement-title">Every film opens<br />another world.</p>
        </section>

        {phase === "cinema" && wallPoster && playback !== "unavailable" && (
          <button
            type="button"
            aria-label={`Watch the ${featured.title} trailer`}
            onClick={(event) => openTrailer(featured, event.currentTarget)}
            className="cinema-wall-poster-trigger absolute z-1 cursor-pointer rounded-sm"
            style={{
              left: wallPoster.left,
              top: wallPoster.top,
              width: wallPoster.right - wallPoster.left,
              height: wallPoster.bottom - wallPoster.top,
            }}
          />
        )}

        {phase === "cinema" && bestPictureWinners.map((movie, index) => (
          <figure
            key={movie.awardYear}
            className={`cinema-track-card ${index === 0 ? "cinema-featured-poster" : ""}`}
            style={{
              "--index": index,
              "--card-top": timelineCardHeights[index % timelineCardHeights.length],
              ...(index === 0 && wallPoster && {
                "--featured-left": `${wallPoster.left}px`,
                "--featured-bottom": `calc(100cqh - ${wallPoster.bottom}px)`,
                "--featured-width": `${wallPoster.right - wallPoster.left}px`,
              }),
            } as CSSProperties}
            inert={index !== 0 && ((!timelineVisible && index > 1) || Math.abs(filmIndex - index) > 2)}
          >
            <button
              type="button"
              tabIndex={index === filmIndex ? 0 : -1}
              aria-label={`Watch the ${movie.title} trailer`}
              onClick={(event) => openTrailer(movie, event.currentTarget)}
              className="group relative isolate aspect-[2/3] w-full cursor-pointer overflow-hidden rounded-card border border-gold bg-ink"
            >
              <span aria-hidden="true" className="cinema-card-fallback absolute inset-0 grid place-items-center p-2 text-center font-display text-2xl">{movie.title}</span>
              <Image
                src={movie.poster}
                alt=""
                width={240}
                height={360}
                unoptimized
                loading={index < 3 ? "eager" : "lazy"}
                onError={(event) => { event.currentTarget.hidden = true; }}
                className="cinema-card-artwork relative aspect-[2/3] h-auto w-full object-cover"
              />
              {index === 0 && <canvas ref={paperCanvas} inert className="cinema-paper-sheet" />}
              <span aria-hidden="true" className="absolute right-2 bottom-2 rounded-full bg-black/80 px-3 py-1 font-mono text-xs opacity-0 group-hover:opacity-100 group-focus-visible:opacity-100">Play trailer</span>
            </button>
            <figcaption className="cinema-card-caption">
              {movie.title}
              <span className="cinema-timeline-label text-muted">{movie.awardYear}</span>
            </figcaption>
          </figure>
        ))}

        {phase === "cinema" && timelineVisible && (
          <nav aria-label="Best Picture years" className="cinema-years-nav absolute inset-x-page bottom-[8%] z-[4] flex justify-start gap-1.5 overflow-x-auto md:justify-center md:gap-3" data-lenis-prevent-horizontal>
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
          <div className="cinema-controls absolute inset-x-0 bottom-0 z-[4] flex justify-between gap-2 px-page pb-6 md:pb-8">
            <button type="button" onClick={togglePlayback} className={control}>
              {playback === "playing" ? "Pause" : "Play"}
            </button>
            <div className="flex gap-2">
              {!timelineVisible && (
                <button
                  type="button"
                  aria-label="Cinema lights"
                  aria-pressed={lightsOn}
                  onClick={() => setLightsOn((on) => !on)}
                  className={`${control} aria-pressed:border-gold`}
                >
                  {lightsOn ? "Lights on" : "Lights off"}
                </button>
              )}
              <button type="button" aria-label="Sound" aria-pressed={!muted} onClick={toggleSound} className={control}>
                {muted ? "Sound off" : "Sound on"}
              </button>
            </div>
          </div>
        )}
      </section>
    </div>
  );
}
