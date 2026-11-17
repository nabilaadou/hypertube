"use client";

import gsap from "gsap";
import Image from "next/image";
import { useLayoutEffect, useRef } from "react";
import type { Movie } from "../data/movies";
import { TrailerPlayer } from "./trailer-player";

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
              className="pointer-events-auto absolute right-0 bottom-full grid size-10 cursor-pointer place-items-center text-xl leading-none opacity-70 hover:opacity-100"
            >
              <span aria-hidden="true">×</span>
            </button>
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
          <div ref={videoRef} className="absolute inset-0 bg-black shadow-2xl backface-hidden">
            <TrailerPlayer trailerId={movie.trailerId} title={movie.title} />
          </div>
        </div>
      </div>
    </dialog>
  );
}
