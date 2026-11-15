"use client";

import gsap from "gsap";
import { useLenis } from "lenis/react";
import { type RefObject, useEffect, useRef, useState } from "react";

type Phase = "fullscreen" | "revealing" | "cinema" | "returning";

export function useCinemaReveal(
  videoRef: RefObject<HTMLVideoElement | null>,
  onProgress: (progress: number) => void,
) {
  const lenis = useLenis();
  const stage = useRef<HTMLElement>(null);
  const [phase, setPhase] = useState<Phase>("fullscreen");

  useEffect(() => {
    const element = stage.current;
    if (!element || !lenis) return;

    const motion = window.matchMedia("(prefers-reduced-motion: reduce)");
    const events = new AbortController();
    const video = videoRef.current;
    const progress = { value: 0 };
    let current: Phase = "fullscreen";
    let introPending = true;
    let introTimer: number | undefined;
    let animation: gsap.core.Tween | undefined;
    let quietTimer: number | undefined;
    let lastGesture = -Infinity;
    let locked = false;
    let fartherDown = false;
    let touchY: number | undefined;

    function release() {
      window.clearTimeout(quietTimer);
      locked = false;
      lenis?.start();
    }

    function releaseWhenQuiet() {
      window.clearTimeout(quietTimer);
      const remaining = 180 - (performance.now() - lastGesture);
      if (remaining > 0) quietTimer = window.setTimeout(releaseWhenQuiet, remaining);
      else release();
    }

    function moveTo(destination: "fullscreen" | "cinema") {
      if (locked || !element || !lenis) return;
      locked = true;
      lenis.stop();
      current = destination === "cinema" ? "revealing" : "returning";
      setPhase(current);
      animation = gsap.to(progress, {
        value: destination === "cinema" ? 1 : 0,
        onUpdate() {
          element.style.setProperty("--reveal", String(progress.value));
          onProgress(progress.value);
        },
        duration: motion.matches ? 0 : 0.85,
        ease: "power2.inOut",
        onComplete() {
          current = destination;
          fartherDown = false;
          setPhase(current);
          releaseWhenQuiet();
        },
      });
    }

    function handleGesture(event: Event, delta: number) {
      if (delta === 0) return;
      if (locked) {
        lastGesture = performance.now();
        if (event.cancelable) event.preventDefault();
        return;
      }

      if (introPending && window.scrollY <= 1) {
        event.preventDefault();
        return;
      }
      if (window.scrollY > 1) return;
      if (current === "fullscreen" && delta > 0) {
        lastGesture = performance.now();
        event.preventDefault();
        moveTo("cinema");
      } else if (current === "cinema" && delta < 0) {
        lastGesture = performance.now();
        event.preventDefault();
        moveTo("fullscreen");
      }
    }

    function finishIntro() {
      introPending = false;
      moveTo("cinema");
    }

    function scheduleIntroAfterPlayback() {
      window.clearTimeout(introTimer);
      introTimer = window.setTimeout(finishIntro, 3000);
    }

    if (motion.matches || window.scrollY > 1 || window.location.hash) {
      finishIntro();
    } else {
      lenis.stop();
      if (video && !video.paused) {
        scheduleIntroAfterPlayback();
      } else {
        introTimer = window.setTimeout(finishIntro, 5000);
        video?.addEventListener("playing", scheduleIntroAfterPlayback, { once: true, signal: events.signal });
      }
    }

    const capture = { capture: true, passive: false, signal: events.signal };
    window.addEventListener("wheel", (event) => {
      if (!event.ctrlKey && !event.shiftKey && Math.abs(event.deltaY) >= Math.abs(event.deltaX)) {
        handleGesture(event, event.deltaY);
      }
    }, capture);
    window.addEventListener("touchstart", (event) => {
      touchY = event.touches.length === 1 ? event.touches[0].clientY : undefined;
    }, { passive: true, signal: events.signal });
    window.addEventListener("touchmove", (event) => {
      if (event.touches.length !== 1 || touchY === undefined) return;
      const nextY = event.touches[0].clientY;
      if (Math.abs(touchY - nextY) < 8) return;
      handleGesture(event, touchY - nextY);
      touchY = nextY;
    }, capture);
    window.addEventListener("keydown", (event) => {
      if (event.defaultPrevented || event.altKey || event.ctrlKey || event.metaKey) return;
      const target = event.target;
      if (target instanceof HTMLElement && target.closest("input, textarea, select, [contenteditable]")) return;
      if (event.code === "Space" && target instanceof HTMLElement && target.closest("button, summary, a")) return;

      if (["ArrowDown", "PageDown", "End"].includes(event.key) || (event.code === "Space" && !event.shiftKey)) {
        handleGesture(event, 1);
      } else if (["ArrowUp", "PageUp", "Home"].includes(event.key) || event.code === "Space") {
        handleGesture(event, -1);
      }
    }, capture);
    window.addEventListener("scroll", () => {
      if (current !== "cinema" || locked) return;
      if (window.scrollY > 1) fartherDown = true;
      else if (fartherDown) moveTo("fullscreen");
    }, { passive: true, signal: events.signal });
    motion.addEventListener("change", () => {
      if (!motion.matches) return;
      if (introPending) {
        window.clearTimeout(introTimer);
        finishIntro();
      } else {
        animation?.progress(1);
      }
    }, { signal: events.signal });

    return () => {
      events.abort();
      animation?.kill();
      if (locked || introPending) release();
      window.clearTimeout(introTimer);
      onProgress(0);
      element.style.removeProperty("--reveal");
    };
  }, [lenis, onProgress, videoRef]);

  return { stage, phase };
}
