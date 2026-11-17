"use client";

import gsap from "gsap";
import Image from "next/image";
import { useLayoutEffect, useRef, useState } from "react";
import type { Movie } from "../data/movies";

type TrailerDialogProps = {
  movie: Movie;
  origin: DOMRectReadOnly;
  onClose: () => void;
};

export function TrailerDialog({ movie, origin, onClose }: TrailerDialogProps) {
  const dialogRef = useRef<HTMLDialogElement>(null);
  const panelRef = useRef<HTMLDivElement>(null);
  const posterRef = useRef<HTMLDivElement>(null);
  const videoRef = useRef<HTMLDivElement>(null);
  const controlsRef = useRef<HTMLDivElement>(null);
  const scrimRef = useRef<HTMLDivElement>(null);
  const animationRef = useRef<gsap.core.Timeline | null>(null);
  const openingViewport = useRef({ width: 0, height: 0 });
  const [unavailable, setUnavailable] = useState(false);

  useLayoutEffect(() => {
    const dialog = dialogRef.current;
    const panel = panelRef.current;
    const poster = posterRef.current;
    const video = videoRef.current;
    const controls = controlsRef.current;
    const scrim = scrimRef.current;
    if (!dialog || !panel || !poster || !video || !controls || !scrim) return;

    dialog.showModal();
    openingViewport.current = { width: window.innerWidth, height: window.innerHeight };

    let animation: gsap.core.Timeline | undefined;
    if (!window.matchMedia("(prefers-reduced-motion: reduce)").matches) {
      const destination = panel.getBoundingClientRect();
      const scaleX = origin.width / destination.width;
      const scaleY = origin.height / destination.height;

      animation = gsap.timeline({
        defaults: { duration: 0.65, ease: "power2.inOut" },
        onReverseComplete: () => dialog.close(),
      })
        .from(panel, {
          x: origin.left - destination.left + (origin.width - destination.width) / 2,
          y: origin.top - destination.top + (origin.height - destination.height) / 2,
          scaleX,
          scaleY,
        }, 0)
        .to(poster, { rotationY: 180 }, 0)
        .from(video, { rotationY: -180 }, 0)
        .from(scrim, { opacity: 0 }, 0)
        .from(controls, { opacity: 0, duration: 0.3 }, 0.35);

      animationRef.current = animation;
    }

    return () => {
      animationRef.current = null;
      animation?.revert();
      dialog.close();
    };
  }, [origin]);

  function dismiss() {
    const animation = animationRef.current;
    const viewport = openingViewport.current;
    if (animation && animation.time() > 0 && viewport.width === window.innerWidth && viewport.height === window.innerHeight) animation.reverse();
    else dialogRef.current?.close();
  }

  return (
    <dialog
      ref={dialogRef}
      aria-labelledby="trailer-title"
      onCancel={(event) => {
        event.preventDefault();
        dismiss();
      }}
      onClose={(event) => {
        if (!event.currentTarget.open) onClose();
      }}
      className="fixed inset-0 m-0 size-full max-h-none max-w-none overflow-hidden bg-transparent text-paper backdrop:bg-transparent"
      data-lenis-prevent
    >
      <h2 id="trailer-title" className="sr-only">{movie.title}</h2>
      <div ref={scrimRef} aria-hidden="true" className="absolute inset-0 bg-black/15" />
      <div className="absolute inset-0 grid place-items-center">
        <div ref={panelRef} className="relative aspect-video w-[min(92vw,140svh)] perspective-[1100px] md:w-[min(70vw,140svh)]">
          <div ref={controlsRef} className="pointer-events-none absolute inset-0 z-10">
            <button
              type="button"
              aria-label="Close trailer"
              onClick={dismiss}
              className="pointer-events-auto absolute top-2 right-2 grid size-10 cursor-pointer place-items-center rounded-full bg-black/80 text-2xl leading-none hover:bg-black"
            >
              <span aria-hidden="true">×</span>
            </button>
            <a
              href={`https://www.youtube.com/watch?v=${movie.trailerId}`}
              target="_blank"
              rel="noreferrer"
              className="pointer-events-auto absolute top-full right-0 mt-2 whitespace-nowrap rounded bg-black/80 px-2 py-1 text-xs underline"
            >
              Watch trailer on YouTube
            </a>
          </div>
          <div
            ref={posterRef}
            aria-hidden="true"
            className="pointer-events-none absolute inset-0 grid place-items-center overflow-hidden rounded-card bg-ink text-center font-display text-2xl backface-hidden motion-reduce:hidden"
          >
            <span>{movie.title}</span>
            <Image
              src={movie.poster}
              alt=""
              fill
              unoptimized
              loading="eager"
              onError={(event) => { event.currentTarget.hidden = true; }}
              className="object-fill"
            />
          </div>
          <div ref={videoRef} className="absolute inset-0 overflow-hidden rounded-card bg-black shadow-2xl backface-hidden">
            {unavailable ? (
              <p role="status" className="grid size-full place-items-center px-12 text-center">
                The embedded trailer could not load. Open it on YouTube below.
              </p>
            ) : (
              <iframe
                title={`${movie.title} trailer`}
                src={`https://www.youtube-nocookie.com/embed/${movie.trailerId}?rel=0&autoplay=1`}
                onError={() => setUnavailable(true)}
                allow="autoplay; encrypted-media; picture-in-picture; fullscreen"
                referrerPolicy="strict-origin-when-cross-origin"
                className="block size-full"
              />
            )}
          </div>
        </div>
      </div>
    </dialog>
  );
}
