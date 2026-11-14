"use client";

import { useEffect, useRef, useState } from "react";
import type { Movie } from "../data/movies";

export function TrailerDialog({ movie, onClose }: { movie: Movie; onClose: () => void }) {
  const dialogRef = useRef<HTMLDialogElement>(null);
  const [unavailable, setUnavailable] = useState(false);

  useEffect(() => {
    const dialog = dialogRef.current;
    dialog?.showModal();
    return () => dialog?.close();
  }, []);

  return (
    <dialog
      ref={dialogRef}
      aria-labelledby="trailer-title"
      onClose={(event) => {
        if (!event.currentTarget.open) onClose();
      }}
      className="m-auto w-[min(92vw,58rem)] max-h-[90svh] overflow-auto rounded-card bg-ink text-paper backdrop:bg-black/85"
      data-lenis-prevent
    >
      <div className="flex items-center justify-between gap-4 p-4">
        <h2 id="trailer-title" className="font-display text-3xl font-bold">{movie.title}</h2>
        <form method="dialog">
          <button type="submit" className="min-h-11 cursor-pointer rounded-full border border-paper/40 px-4">Close</button>
        </form>
      </div>
      {unavailable ? (
        <p role="status" className="p-4">The embedded trailer could not load. Open it on YouTube below.</p>
      ) : (
        <iframe
          title={`${movie.title} trailer`}
          src={`https://www.youtube-nocookie.com/embed/${movie.trailerId}?rel=0`}
          onError={() => setUnavailable(true)}
          allow="encrypted-media; picture-in-picture; fullscreen"
          referrerPolicy="strict-origin-when-cross-origin"
          className="aspect-video w-full bg-black"
        />
      )}
      <p className="p-4 text-sm">
        <a href={`https://www.youtube.com/watch?v=${movie.trailerId}`} target="_blank" rel="noreferrer" className="underline">
          Watch trailer on YouTube
        </a>
      </p>
    </dialog>
  );
}
