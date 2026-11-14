"use client";

import { useGSAP } from "@gsap/react";
import gsap from "gsap";
import { ScrollTrigger } from "gsap/ScrollTrigger";
import { useRef } from "react";

gsap.registerPlugin(useGSAP, ScrollTrigger);

export function HypertubeStatement() {
  const section = useRef<HTMLElement>(null);

  useGSAP(() => {
    const motion = gsap.matchMedia();
    motion.add("(prefers-reduced-motion: no-preference)", () => {
      gsap.from("[data-statement-copy]", {
        y: 42,
        opacity: 0.55,
        ease: "power2.out",
        scrollTrigger: {
          trigger: section.current,
          start: "top 90%",
          end: "top 20%",
          scrub: true,
        },
      });
    });
    return () => motion.revert();
  }, { scope: section });

  return (
    <section
      ref={section}
      id="hypertube-statement"
      aria-labelledby="statement-title"
      className="hypertube-statement relative isolate grid min-h-svh place-items-center overflow-hidden px-page py-24 text-center text-paper"
    >
      <div data-statement-copy className="relative">
        <span aria-hidden="true" className="mb-8 block font-display text-[clamp(4rem,9vw,8rem)] leading-[0.6] font-black text-paper/65">“ ”</span>
        <h2 id="statement-title" className="hypertube-statement-title font-display text-[clamp(2.75rem,10.3vw,12rem)] leading-[0.87] font-black uppercase">
          Every film opens<br />another world.
        </h2>
        <p className="mx-auto mt-9 max-w-lg text-base leading-relaxed text-paper/90 md:mt-12 md:text-lg">
          Discover unforgettable stories. Revisit the films that stay with you.
        </p>
      </div>
    </section>
  );
}
