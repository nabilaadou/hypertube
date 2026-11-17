"use client";

import { useGSAP } from "@gsap/react";
import gsap from "gsap";
import { ScrollTrigger } from "gsap/ScrollTrigger";
import { useLenis } from "lenis/react";
import dynamic from "next/dynamic";
import Image from "next/image";
import { type CSSProperties, type PointerEvent, useCallback, useEffect, useRef, useState } from "react";
import { movies, cinemaMedia, type Movie } from "../data/movies";
import type { createCinemaPaperSurface, PaperPose } from "./cinema-paper-surface";
import { projectVideo, type ScreenCorners } from "./cinema-projection";
import { HypertubeStatement } from "./hypertube-statement";
import { Noise } from "./noise";
import { createQuoteParticleSurface } from "./quote-particle-surface";
import { TrailerDialog } from "./trailer-dialog";
import { useCinemaReveal } from "./use-cinema-reveal";

const CinemaScene = dynamic(() => import("./cinema-scene"), { ssr: false });

const featured = movies[0];
const handoffViewports = 1.8;
const timelineExitViewports = 2.35;
const quoteHoldViewports = 0.6;
const filmViewports = 0.66;
const lastFilm = movies.length - 1;
const timelineStart = handoffViewports;
const timelineEnd = timelineStart + filmViewports * lastFilm;
const paperLiftOff = 0.001;
const timelineCardHeights = [15, 37, 9, 32, 20];

gsap.registerPlugin(useGSAP, ScrollTrigger);

type Playback = "paused" | "playing" | "unavailable";
type TrailerSelection = { movie: Movie; origin: DOMRectReadOnly; fromTimeline: boolean };

const control = "grid size-11 shrink-0 cursor-pointer place-items-center text-paper/65 hover:text-paper focus-visible:text-paper";

function tiltTimelinePoster(event: PointerEvent<HTMLElement>) {
  if (event.pointerType !== "mouse") return;
  const card = event.currentTarget;
  const { left, top, width, height } = card.getBoundingClientRect();
  const x = gsap.utils.clamp(0, 1, (event.clientX - left) / width);
  const y = gsap.utils.clamp(0, 1, (event.clientY - top) / height);
  card.style.setProperty("--cursor-yaw", String((x - 0.5) * 24));
  card.style.setProperty("--cursor-pitch", String((0.5 - y) * 18));
  card.style.setProperty("--cursor-light", `${x * 100}%`);
}

function resetTimelinePoster(event: PointerEvent<HTMLElement>) {
  const style = event.currentTarget.style;
  style.removeProperty("--cursor-yaw");
  style.removeProperty("--cursor-pitch");
  style.removeProperty("--cursor-light");
}

export function MovieEdit() {
  const videoRef = useRef<HTMLVideoElement>(null);
  const videoLayer = useRef<HTMLDivElement>(null);
  const worldLayer = useRef<HTMLDivElement>(null);
  const paperCanvas = useRef<HTMLCanvasElement>(null);
  const quoteCanvas = useRef<HTMLCanvasElement>(null);
  const quoteTitle = useRef<HTMLHeadingElement>(null);
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
  const [timelineLeaving, setTimelineLeaving] = useState(false);
  const [trailer, setTrailer] = useState<TrailerSelection | null>(null);
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
  const openTimelineYear = trailer?.fromTimeline ? trailer.movie.releaseYear : undefined;

  function trailerSide(year: number) {
    if (openTimelineYear === undefined) return undefined;
    if (year === openTimelineYear) return "selected";
    return year < openTimelineYear ? "before" : "after";
  }

  useGSAP(() => {
    const section = stage.current;
    const scrollArea = runway.current;
    if (!section || !scrollArea) return;

    const resize = new ResizeObserver(() => updateProjection(revealProgress.current));
    resize.observe(section);

    function syncTimeline({ start }: ScrollTrigger) {
      setTimelineVisible(window.scrollY >= start);
    }

    const motion = gsap.matchMedia();
    motion.add({
      standard: "(prefers-reduced-motion: no-preference)",
      reduced: "(prefers-reduced-motion: reduce)",
    }, ({ conditions }) => {
      const reduced = conditions?.reduced;
      const scrub = reduced ? true : 0.3;
      const quoteParticles = !reduced && CSS.supports("background-clip", "text") && quoteCanvas.current && quoteTitle.current
        ? createQuoteParticleSurface(quoteCanvas.current, quoteTitle.current)
        : null;
      gsap.to(section, {
        "--track-index": lastFilm,
        ease: "none",
        onUpdate(this: gsap.core.Tween) {
          setFilmIndex(Math.round(this.progress() * lastFilm));
        },
        scrollTrigger: {
          trigger: scrollArea,
          start: () => `top+=${section.clientHeight * timelineStart} top`,
          end: () => `top+=${section.clientHeight * timelineEnd} top`,
          scrub,
          onUpdate: syncTimeline,
          onRefresh: syncTimeline,
        },
      });
      const quoteTransition = gsap.timeline({
        onUpdate() {
          const progress = this.time();
          setTimelineLeaving(progress >= (reduced ? 0.42 : 0.27));
          quoteParticles?.draw(
            progress,
            Number(section.style.getPropertyValue("--quote-scale")) || 1,
            Number.parseFloat(section.style.getPropertyValue("--quote-fill") || "-25%"),
          );
        },
        scrollTrigger: {
          trigger: scrollArea,
          start: () => `top+=${section.clientHeight * timelineEnd} top`,
          end: () => `+=${section.clientHeight * timelineExitViewports}`,
          scrub,
        },
      });
      if (reduced) {
        quoteTransition.set(section, {
          "--timeline-content-opacity": 0,
          "--timeline-field-opacity": 0,
          "--quote-visibility": "visible",
          "--quote-background": 1,
          "--quote-scale": 1,
          "--quote-opacity": 1,
          "--quote-fill": "100%",
          "--quote-marks-y": "0cqh",
          "--quote-credit-y": "0cqh",
          "--quote-credit-opacity": 0.85,
        }, 0.42).set(section, { "--quote-credit-opacity": 0.85 }, 1);
      } else {
        quoteTransition
          .to(section, { "--timeline-exit": 1.85, duration: 0.5, ease: "none" }, 0)
          .to(section, { "--timeline-field-opacity": 0, duration: 0.29, ease: "none" }, 0.18)
          .to(section, { "--timeline-content-opacity": 0, duration: 0.08, ease: "none" }, 0.43)
          .set(section, { "--quote-visibility": "visible" }, 0.25)
          .to(section, { "--quote-opacity": 1, duration: 0.32, ease: "sine.inOut" }, 0.25)
          .to(section, { "--quote-scale": 3.75, duration: 0.25, ease: "none" }, 0.25)
          .to(section, { "--quote-scale": 2.15, duration: 0.2, ease: "none" }, 0.5)
          .to(section, { "--quote-scale": 1.15, duration: 0.2, ease: "sine.out" }, 0.7)
          .to(section, { "--quote-scale": 1, duration: 0.1, ease: "sine.out" }, 0.9)
          .to(section, { "--quote-background": 1, duration: 0.34, ease: "sine.inOut" }, 0.65)
          .to(section, { "--quote-fill": "100%", duration: 0.25, ease: "none" }, 0.7)
          .to(section, { "--quote-marks-y": "0cqh", duration: 0.29, ease: "power2.out" }, 0.65)
          .to(section, { "--quote-credit-y": "0cqh", "--quote-credit-opacity": 0.85, duration: 0.28, ease: "sine.out" }, 0.72);
      }

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
          .fromTo(section, { "--timeline-entry": 1.65 }, { "--timeline-entry": 0, duration: 1, ease: "none" }, 0)
          .set(section, { "--wall-poster-visibility": "hidden", "--featured-poster-visibility": "visible", "--featured-poster-opacity": 1, "--poster-paper": 1 }, paperLiftOff)
          .to(section, { "--poster-travel": 1, duration: 0.98, ease: "sine.out" }, paperLiftOff)
          .to(pose, { turn: 360, duration: 0.61, ease: "power2.inOut" }, 0.09)
          .to(section, { "--poster-lift": 10, "--poster-pitch": 14, "--poster-roll": -14, duration: 0.24, ease: "sine.out" }, paperLiftOff)
          .to(pose, { curl: 1, twist: 0.75, ripple: 0.7, duration: 0.24, ease: "sine.out" }, paperLiftOff)
          .to(section, { "--poster-lift": 4, "--poster-pitch": -13, "--poster-roll": 11, duration: 0.3, ease: "sine.inOut" }, 0.241)
          .to(pose, { curl: -0.92, twist: -0.85, ripple: -0.85, duration: 0.3, ease: "sine.inOut" }, 0.241)
          .to(section, { "--poster-lift": 5.5, "--poster-pitch": 6, "--poster-roll": -8, duration: 0.22, ease: "sine.inOut" }, 0.541)
          .to(pose, { curl: -0.25, twist: 0.35, ripple: 0.4, duration: 0.22, ease: "sine.inOut" }, 0.541)
          .to(section, { "--poster-lift": 0, "--poster-pitch": 0, "--poster-roll": 0, duration: 0.229, ease: "sine.inOut" }, 0.761)
          .to(pose, { curl: 0, twist: 0, ripple: 0, duration: 0.229, ease: "sine.inOut" }, 0.761)
          .to(section, { "--timeline-labels": 1, duration: 0.34, ease: "power1.out" }, 0.58)
          .to(section, { "--poster-caption": 1, duration: 0.06, ease: "power1.out" }, 0.94)
          .set(section, { "--poster-paper": 0 }, 0.99);
        return () => {
          quoteParticles?.dispose();
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

  useEffect(() => {
    if (trailer || !trailerTrigger.current) return;
    trailerTrigger.current.focus({ preventScroll: true });
    trailerTrigger.current = null;
  }, [trailer]);

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
    const origin = trigger.getBoundingClientRect();
    trailerTrigger.current = trigger;
    const video = videoRef.current;
    resumeEdit.current = Boolean(video && !video.paused);
    video?.pause();
    lenis?.stop();
    setTrailer({ movie, origin, fromTimeline: trigger.closest(".cinema-track-card") !== null });
  }

  function closeTrailer() {
    setTrailer(null);
    lenis?.start();
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

  const playbackAction = playback === "playing" ? "Pause" : "Play";

  return (
    <div ref={runway} style={{ minHeight: `${(1 + timelineEnd + timelineExitViewports + quoteHoldViewports) * 100}svh` }}>
      <section id="opening" aria-labelledby="opening-title" ref={stage} data-phase={phase} data-timeline-active={timelineVisible} className="cinema-stage sticky top-0 isolate h-svh overflow-clip bg-theater text-paper">
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
            <summary aria-label="Navigation" className={`${control} list-none [&::-webkit-details-marker]:hidden`}>
              <span aria-hidden="true" className="grid gap-[3px]">
                <span className="h-px w-3.5 -skew-x-30 bg-current" />
                <span className="ml-[3px] h-px w-3.5 -skew-x-30 bg-current" />
              </span>
            </summary>
            <nav aria-label="Landing page" className="absolute top-full right-0 mt-3 min-w-44 rounded-card bg-ink p-2 shadow-lg">
              <a className="block rounded px-4 py-3 hover:bg-quote" href="#opening">Opening edit</a>
            </nav>
          </details>
        </header>

        <section aria-labelledby="timeline-title" className="cinema-timeline absolute inset-0">
          {phase === "cinema" && <Noise animated={timelineVisible && !timelineLeaving} />}
          <div className="cinema-timeline-grid absolute inset-0" aria-hidden="true" />
          <h2 id="timeline-title" className="cinema-timeline-heading cinema-timeline-label">
            Selected Films
          </h2>
          <nav aria-label="Movie release years" className="cinema-year-track cinema-timeline-label" inert={!timelineVisible || timelineLeaving}>
            {movies.map((film, index) => (
              <button
                key={film.releaseYear}
                type="button"
                aria-current={filmIndex === index ? "true" : undefined}
                aria-label={`${film.releaseYear}, ${film.title}`}
                data-trailer-side={trailerSide(film.releaseYear)}
                tabIndex={Math.abs(filmIndex - index) <= 1 ? 0 : -1}
                onClick={() => goToFilm(index)}
                style={{ "--index": index } as CSSProperties}
              >
                <span>{film.releaseYear}</span>
              </button>
            ))}
          </nav>
          <HypertubeStatement canvasRef={quoteCanvas} titleRef={quoteTitle} />
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

        {phase === "cinema" && movies.map((movie, index) => (
          <figure
            key={movie.releaseYear}
            data-trailer-side={trailerSide(movie.releaseYear)}
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
            inert={timelineLeaving || (index !== 0 && ((!timelineVisible && index > 1) || Math.abs(filmIndex - index) > 2))}
            onPointerMove={tiltTimelinePoster}
            onPointerLeave={resetTimelinePoster}
          >
            <button
              type="button"
              tabIndex={index === filmIndex ? 0 : -1}
              aria-label={`Watch the ${movie.title} trailer`}
              onClick={(event) => openTrailer(movie, event.currentTarget)}
              className="group relative isolate aspect-[2/3] w-full cursor-pointer overflow-hidden bg-ink"
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
              <span aria-hidden="true" className="absolute right-2 bottom-2 z-[3] rounded-full bg-black/80 px-3 py-1 font-mono text-xs opacity-0 group-hover:opacity-100 group-focus-visible:opacity-100">Play trailer</span>
            </button>
            <figcaption className="cinema-card-caption">{movie.title}</figcaption>
          </figure>
        ))}

        {trailer && <TrailerDialog movie={trailer.movie} origin={trailer.origin} onClose={closeTrailer} />}

        {phase === "cinema" && playback !== "unavailable" && (
          <div className="cinema-controls absolute inset-x-0 bottom-0 z-[4] flex justify-between gap-2 px-page pb-6 md:pb-8">
            <button type="button" aria-label={playbackAction} title={playbackAction} onClick={togglePlayback} className={control}>
              <span aria-hidden="true" className="cinema-control-icon cinema-playback-icon" data-playing={playback === "playing"} />
            </button>
            <div className="flex gap-2">
              {!timelineVisible && (
                <button
                  type="button"
                  aria-label="Cinema lights"
                  aria-pressed={lightsOn}
                  title="Cinema lights"
                  onClick={() => setLightsOn((on) => !on)}
                  className={`${control} aria-pressed:text-gold`}
                >
                  <span aria-hidden="true" className="cinema-control-icon cinema-lights-icon" />
                </button>
              )}
              <button type="button" aria-label="Sound" aria-pressed={!muted} title="Sound" onClick={toggleSound} className={control}>
                <span aria-hidden="true" className="cinema-control-icon cinema-sound-icon" />
              </button>
            </div>
          </div>
        )}
      </section>
    </div>
  );
}
