"use client";

import { useEffect, useRef, useState } from "react";
import { SiteHeader } from "../site-header";
import { type Film, films, media } from "./films";
import { Screening } from "./screening";
import { setupJourney } from "./journey";

export function Landing() {
  const root = useRef<HTMLDivElement>(null);
  const stage = useRef<HTMLElement>(null);
  const video = useRef<HTMLVideoElement>(null);
  const trigger = useRef<HTMLButtonElement>(null);
  const [film, setFilm] = useState<Film | null>(null);
  const [playing, setPlaying] = useState(false);
  const [sound, setSound] = useState(false);
  const [reelFailed, setReelFailed] = useState(false);

  useEffect(() => {
    const container = root.current;
    const player = video.current;
    if (!container || !stage.current || !player) return;
    if (player.error) setReelFailed(true);
    return setupJourney(container, stage.current, player);
  }, []);

  useEffect(() => {
    if (!film) return;
    const player = video.current;
    const resume = player && !player.paused;
    player?.pause();
    return () => {
      if (resume && player && !document.hidden) void player.play().catch(() => {});
    };
  }, [film]);

  function toggleReel() {
    const player = video.current;
    if (!player) return;
    if (player.paused) {
      player.dataset.paused = "false";
      void player.play().catch(() => {});
    } else {
      player.dataset.paused = "true";
      player.pause();
    }
  }

  function toggleSound() {
    const player = video.current;
    if (!player) return;
    player.muted = !player.muted;
    setSound(!player.muted);
  }

  function closeScreening() {
    setFilm(null);
    const poster = trigger.current;
    requestAnimationFrame(() => poster?.focus({ preventScroll: true }));
  }

  return (
    <div className="landing" ref={root}>
      <SiteHeader />
      <main id="main-content" className="runway" ref={stage} tabIndex={-1}>
        <div className="cinema-stage">
          <section id="opening" className="opening" aria-labelledby="opening-title">
            <div className="reel">
              <video
                ref={video}
                src={media.reel}
                poster={media.still}
                crossOrigin="anonymous"
                preload="metadata"
                muted
                playsInline
                loop
                onPlay={() => setPlaying(true)}
                onPause={() => setPlaying(false)}
                onError={() => setReelFailed(true)}
                aria-label="Hypertube opening reel"
              />
            </div>
            <div className="opening-copy">
              <h1 id="opening-title" className="sr-only">Hypertube</h1>
              {reelFailed && <p role="status" className="reel-status">The opening reel is unavailable.</p>}
              <span className="sr-only">Scroll down to reveal the screening room.</span>
              <svg className="scroll-cue" width="24" height="24" viewBox="0 0 24 24" fill="none" aria-hidden="true">
                <path d="M12 3v17m-7-7 7 7 7-7" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
              </svg>
            </div>
            {!reelFailed && (
              <fieldset className="reel-controls" aria-label="Reel controls">
                <button className="reel-button" type="button" aria-label={playing ? "Pause reel" : "Play reel"} onClick={toggleReel}>
                  <svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
                    {playing ? <path d="M6 4h4v16H6zm8 0h4v16h-4z" /> : <path d="M8 4v16l13-8z" />}
                  </svg>
                </button>
                <button className="reel-button" type="button" aria-label="Sound" aria-pressed={sound} onClick={toggleSound}>
                  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                    <path d="M3 9h4l5-4v14l-5-4H3z" />
                    {sound ? <path d="M15 9a4 4 0 0 1 0 6m3-9a8 8 0 0 1 0 12" /> : <path d="m16 9 5 6m0-6-5 6" />}
                  </svg>
                </button>
              </fieldset>
            )}
          </section>

          <section id="theater" className="theater" aria-labelledby="theater-title">
            <h2 id="theater-title" className="sr-only">Screening room</h2>
            <div className="room-art" style={{ backgroundImage: `url("${media.room}")` }}>
              <div className="room-screen" />
            </div>
            <canvas className="room-canvas" inert />
          </section>

          <section id="films" className="awards" aria-labelledby="awards-title">
            <div className="awards-backdrop" aria-hidden="true" />
            <div className="awards-heading">
              <h2 id="awards-title" className="display">Best Picture</h2>
              <p className="mono">Academy Award winners · 2024–2020</p>
            </div>
            <ol className="ceremony-years" aria-label="Ceremony years">
              {films.map(({ year }) => <li key={year} className="mono">{year}</li>)}
            </ol>
            <ul className="film-list">
              {films.map((entry) => (
                <li key={entry.year} id={String(entry.year)} className="film-card">
                  <button type="button" aria-label={`${entry.title}, ${entry.year}. Film details and trailer`} onClick={(event) => {
                    trigger.current = event.currentTarget;
                    setFilm(entry);
                  }}>
                    <span className="poster">
                      <span aria-hidden="true" className="display poster-fallback">{entry.title}</span>
                      {/* The existing TMDB poster is optional and keeps its text fallback if the CDN fails. */}
                      {/* biome-ignore lint/performance/noImgElement: the small fixed-size poster comes from the prototype CDN. */}
                      <img
                        src={`https://image.tmdb.org/t/p/w500/${entry.poster}`}
                        alt=""
                        width={500}
                        height={750}
                        onError={(event) => { event.currentTarget.hidden = true; }}
                      />
                    </span>
                    <span className="film-caption">
                      <span>{entry.title}</span><span className="mono">{entry.year}</span>
                    </span>
                  </button>
                </li>
              ))}
            </ul>
          </section>

          <section id="statement" className="statement" aria-labelledby="statement-title">
            <div className="statement-copy">
              <h2 id="statement-title" className="display">
                <span>Every film opens<br />another world.</span>
                <span className="statement-dots" aria-hidden="true">Every film opens<br />another world.</span>
              </h2>
              <p>Discover unforgettable stories. Revisit the films that stay with you.</p>
            </div>
          </section>
        </div>
      </main>
      {film && <Screening film={film} onClose={closeScreening} />}
    </div>
  );
}
