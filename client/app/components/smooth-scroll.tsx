"use client";

import { useGSAP } from "@gsap/react";
import gsap from "gsap";
import { ScrollTrigger } from "gsap/ScrollTrigger";
import { ReactLenis, useLenis } from "lenis/react";
import { type ReactNode, useEffect } from "react";

gsap.registerPlugin(useGSAP, ScrollTrigger);

const options = { autoRaf: false, anchors: true, respectReducedMotion: true };

function ScrollDriver() {
  const lenis = useLenis();

  useEffect(() => {
    if (!lenis) return;

    const update = (seconds: number) => lenis.raf(seconds * 1000);
    const unsubscribe = lenis.on("scroll", ScrollTrigger.update);

    gsap.ticker.lagSmoothing(0);
    gsap.ticker.add(update);
    ScrollTrigger.refresh();

    return () => {
      gsap.ticker.remove(update);
      unsubscribe();
    };
  }, [lenis]);

  return null;
}

export function SmoothScroll({ children }: { children: ReactNode }) {
  return (
    <ReactLenis root options={options}>
      <ScrollDriver />
      {children}
    </ReactLenis>
  );
}
