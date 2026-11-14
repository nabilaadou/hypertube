"use client";

import { useEffect, useRef, useState } from "react";
import { SiteHeader } from "../site-header";
import { type Film, films, media } from "./films";
import { Screening } from "./screening";
import { type JourneyAction, setupJourney } from "./journey";

export function Landing() {
  const root = useRef<HTMLDivElement>(null);
  const stage = useRef<HTMLElement>(null);
  const video = useRef<HTMLVideoElement>(null);
  const journey = useRef<JourneyAction>(null);
  const trigger = useRef<HTMLButtonElement>(null);
  const [menu, setMenu] = useState(false);
  const [film, setFilm] = useState<Film | null>(null);
  const [playing, setPlaying] = useState(false);
  const [sound, setSound] = useState(false);
  const [reelFailed, setReelFailed] = useState(false);

  useEffect(() => {
    const container = root.current;
    const player = video.current;
    if (!container || !stage.current || !player) return;
    if (player.error) setReelFailed(true);
    const cleanup = setupJourney(container, stage.current, player, (action) => {
      journey.current = action;
    });
    return () => {
      journey.current = null;
      cleanup();
    };
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

  function navigate(destination: string) {
    setMenu(false);
    if (journey.current) {
      journey.current(destination);
      return;
    }
    document.getElementById(destination)?.scrollIntoView();
  }

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
      <SiteHeader onOpening={() => navigate("opening")} />
      <fieldset className="journey-nav" aria-label="Section menu" onKeyDown={(event) => {
        if (event.key !== "Escape" || !menu) return;
        setMenu(false);
        document.getElementById("journey-menu")?.focus();
      }}>
        <button id="journey-menu" className="outline-button" type="button" aria-expanded={menu} aria-controls="journey-links" onClick={() => setMenu(!menu)}>
          Menu
        </button>
        <nav id="journey-links" aria-label="Explore Hypertube" hidden={!menu}>
          <button type="button" onClick={() => navigate("opening")}>Opening reel</button>
          <button type="button" onClick={() => navigate("theater")}>Screening room</button>
          <button type="button" onClick={() => navigate("films")}>Best Picture</button>
          <button type="button" onClick={() => navigate("statement")}>About Hypertube</button>
        </nav>
      </fieldset>
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
              <h1 id="opening-title" className="display">Hypertube</h1>
              {reelFailed && <p role="status" className="reel-status">The opening reel is unavailable.</p>}
              <button className="scroll-cue mono" type="button" onClick={() => navigate("theater")}>
                Enter the cinema <span aria-hidden="true">↓</span>
              </button>
            </div>
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
            <fieldset className="year-nav" aria-label="Jump to ceremony year">
              {films.map(({ year }) => (
                <button key={year} className="mono" type="button" onClick={() => navigate(String(year))}>{year}</button>
              ))}
            </fieldset>
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

          {!reelFailed && (
            <fieldset className="reel-controls" aria-label="Reel controls">
              <button className="outline-button" type="button" onClick={toggleReel}>{playing ? "Pause reel" : "Play reel"}</button>
              <button className="outline-button" type="button" aria-pressed={sound} onClick={toggleSound}>
                Sound <span aria-hidden="true">{sound ? "on" : "off"}</span>
              </button>
            </fieldset>
          )}
        </div>
      </main>
      {film && <Screening film={film} onClose={closeScreening} />}
    </div>
  );
}
