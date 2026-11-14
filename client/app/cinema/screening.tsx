"use client";

import { useEffect, useRef, useState } from "react";
import type { Film } from "./films";

export function Screening({ film, onClose }: { film: Film; onClose: () => void }) {
  const dialog = useRef<HTMLDialogElement>(null);
  const [play, setPlay] = useState(false);

  useEffect(() => {
    const element = dialog.current;
    element?.showModal();
    return () => element?.close();
  }, []);

  return (
    <dialog
      ref={dialog}
      className="screening"
      data-lenis-prevent
      aria-labelledby="screening-title"
      onCancel={(event) => {
        event.preventDefault();
        onClose();
      }}
    >
      <div className="flex items-start justify-between gap-4">
        <div>
          <p className="mono">{film.award} · {film.year}</p>
          <h2 className="display" id="screening-title">{film.title}</h2>
        </div>
        <button className="outline-button" type="button" onClick={onClose}>Close</button>
      </div>
      <p className="screening-description">{film.description}</p>
      <div className="screening-player">
        {play ? (
          <iframe
            title={`${film.title} trailer`}
            src={`https://www.youtube-nocookie.com/embed/${film.trailer}?autoplay=1`}
            allow="autoplay; encrypted-media; picture-in-picture"
            referrerPolicy="strict-origin-when-cross-origin"
            allowFullScreen
          />
        ) : (
          <button className="outline-button" type="button" onClick={() => setPlay(true)}>Play trailer</button>
        )}
      </div>
      <a className="screening-link" href={`https://www.youtube.com/watch?v=${film.trailer}`} target="_blank" rel="noreferrer">
        Watch on YouTube if playback is unavailable ↗
      </a>
    </dialog>
  );
}
