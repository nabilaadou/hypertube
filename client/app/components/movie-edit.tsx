"use client";

import { useState } from "react";
import { cinemaMedia } from "../data/movies";

export function MovieEdit() {
  const [unavailable, setUnavailable] = useState(false);

  return (
    <div className="relative mt-10 aspect-video w-full overflow-hidden rounded-card bg-ink">
      <video
        className="h-full w-full object-contain"
        src={cinemaMedia.edit}
        poster={cinemaMedia.poster}
        aria-label="Hypertube movie edit"
        controls
        muted
        playsInline
        preload="metadata"
        onError={() => setUnavailable(true)}
      />
      {unavailable && (
        <p role="status" className="absolute inset-0 grid place-items-center bg-theater/90 p-6 text-center">
          The movie edit is unavailable. Please try again later.
        </p>
      )}
    </div>
  );
}
