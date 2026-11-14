import gsap from "gsap";
import { ScrollTrigger } from "gsap/ScrollTrigger";
import Lenis from "lenis";
import { films } from "./films";
import { fallbackScreen, theaterFrame } from "./framing";

export type JourneyAction = (destination: string) => void;

const HOLD = 1;
const HANDOFF = 1.55;
const FILM_STEP = 0.82;
const STATEMENT = HOLD + HANDOFF + films.length * FILM_STEP;
const END = STATEMENT + 0.95;
const INPUT_QUIET = 180;
const clamp = gsap.utils.clamp(0, 1);
const mix = gsap.utils.interpolate;
const smooth = gsap.parseEase("power2.inOut");

export function setupJourney(
  root: HTMLElement,
  runway: HTMLElement,
  video: HTMLVideoElement,
  registerAction: (action: JourneyAction) => void,
) {
  const motion = matchMedia("(prefers-reduced-motion: reduce)");
  function element<T extends HTMLElement = HTMLElement>(selector: string): T {
    const match = runway.querySelector<T>(selector);
    if (!match) throw new Error(`Missing cinema element ${selector}`);
    return match;
  }
  const stage = element(".cinema-stage");
  const opening = element(".opening-copy");
  const reel = element(".reel");
  const theater = element(".theater");
  const room = element(".room-art");
  const canvas = element<HTMLCanvasElement>(".room-canvas");
  const awards = element(".awards-heading");
  const backdrop = element(".awards-backdrop");
  const years = element(".year-nav");
  const statement = element(".statement");
  const quote = element(".statement-copy");
  const quoteText = element("#statement-title > span");
  const controls = runway.querySelector<HTMLElement>(".reel-controls");
  const cards = Array.from(runway.querySelectorAll<HTMLElement>(".film-card"));
  const yearButtons = years.querySelectorAll("button");

  let stop = () => {};
  const configure = () => {
    stop();

    if (motion.matches) {
      video.pause();
      registerAction((destination) => document.getElementById(destination)?.scrollIntoView());
      return;
    }

    // Each motion preference change tears down the previous scroll and WebGL lifecycle.
    gsap.registerPlugin(ScrollTrigger);
    gsap.ticker.lagSmoothing(0);
    root.dataset.enhanced = "true";
    root.scrollTop = 0;
    runway.style.height = `${(END + 1) * 100}svh`;

    const lenis = new Lenis({ wrapper: root, content: runway, autoRaf: false, autoResize: false });
    const abort = new AbortController();
    const input = { signal: abort.signal, capture: true, passive: false };
    let state: "opening" | "revealing" | "cinema" = "opening";
    let distance = 0;
    let width = 1;
    let height = 1;
    let cardWidth = 1;
    let cardHeight = 1;
    let reveal: gsap.core.Tween | undefined;
    let timer: ReturnType<typeof setTimeout> | undefined;
    let roomScene: ReturnType<typeof import("./theater").createTheater> | undefined;
    let holdingInput = false;
    let touchY: number | undefined;
    let currentYear: number | undefined;
    let playbackAllowed = false;
    const progress = { pull: 0 };

    function syncPlayback() {
      const allowed = !document.hidden && distance < HOLD + HANDOFF && !root.querySelector("dialog[open]");
      if (allowed === playbackAllowed) return;
      playbackAllowed = allowed;
      if (allowed && video.dataset.paused !== "true") {
        void video.play().catch(() => {});
      } else {
        video.pause();
      }
    }

    const setReel = gsap.quickSetter(reel, "css");
    const setCards = cards.map((card) => gsap.quickSetter(card, "css"));
    const range = (value: number, start: number, end: number) => clamp((value - start) / (end - start));
    const setVisible = (element: HTMLElement, opacity: number, interactive = opacity > 0) => {
      element.style.opacity = String(opacity);
      element.inert = !interactive;
      element.style.visibility = opacity > 0 ? "visible" : "hidden";
    };

    function render() {
      const pull = progress.pull;
      const mobile = width < 720;
      const p = Math.max(0, distance - HOLD);
      const handoff = range(p, 0, HANDOFF);
      const travel = Math.min(films.length - 1, Math.max(0, p - HANDOFF) / FILM_STEP);
      const out = range(distance, STATEMENT, STATEMENT + 0.36);
      const modelReady = Boolean(roomScene?.ready);
      const screen = (modelReady ? roomScene?.screen : undefined) ?? fallbackScreen(width, height);
      const useTexture = modelReady && pull === 1 && video.readyState >= 2 && !video.error;
      const w = mix(width, screen.width, pull);
      const h = mix(height, screen.height, pull);
      setReel({
        x: mix(0, screen.x, pull),
        y: mix(0, screen.y, pull),
        scaleX: w / width,
        scaleY: h / height,
      });
      // Inverse-scale the video container so object-fit crops at its real aspect ratio.
      video.style.width = `${w}px`;
      video.style.height = `${h}px`;
      video.style.transform = `scale(${width / w}, ${height / h})`;

      const roomVisible = pull > 0 && handoff < 1;
      theater.style.visibility = roomVisible ? "visible" : "hidden";
      canvas.style.visibility = roomVisible && modelReady ? "visible" : "hidden";
      room.style.visibility = roomVisible && !modelReady ? "visible" : "hidden";
      reel.style.visibility = !useTexture && handoff < 1 ? "visible" : "hidden";
      reel.dataset.fullscreen = String(pull === 0);
      setVisible(opening, 1 - range(pull, 0, 0.35), state === "opening");

      const wipe = mix(103, -27, handoff);
      backdrop.style.clipPath = `polygon(0 ${wipe}%, 100% ${wipe - 23 * handoff}%, 100% 100%, 0 100%)`;
      const labels = range(handoff, 0.56, 0.95) * (1 - out);
      setVisible(awards, labels, labels > 0.75);
      setVisible(years, labels, labels > 0.75);
      const activeYear = Math.min(films.length - 1, Math.round(travel));
      if (currentYear !== activeYear) {
        yearButtons[currentYear ?? -1]?.removeAttribute("aria-current");
        yearButtons[activeYear]?.setAttribute("aria-current", "true");
        currentYear = activeYear;
      }

      const startScale = mobile ? 0.56 : 0.66;
      const targetX = width * (mobile ? 0.12 : 0.19);
      const gap = mobile ? Math.max(cardWidth + 20, width * 0.64) : width * 0.34;
      const posterTop = Math.min(height * (mobile ? 0.81 : 0.84) - cardHeight * startScale, height - cardHeight - 54);
      const trackTop = Math.min(height * (mobile ? 0.34 : 0.33), Math.max(100, height - cardHeight - 100));
      for (const [index, card] of cards.entries()) {
        const move = smooth(handoff);
        const x = (index ? targetX + index * gap : mix(width * 0.048, targetX, move)) - gap * travel;
        const y = index ? trackTop + (mobile ? 0 : (index % 3) * height * 0.045) : mix(posterTop, trackTop, move);
        const scale = index ? 1 : mix(startScale, 1, move);
        const opacity = (index ? range(handoff, 0.57, 1) : range(pull, 0.48, 0.96)) * (1 - out);
        const visible = opacity > 0 && x < width && x + cardWidth * scale > 0;
        const fullyVisible = x >= -1 && x + cardWidth * scale <= width + 1;
        setCards[index]({
          x,
          y,
          scale,
          rotate: index ? 0 : -7 * Math.sin(move * Math.PI),
          opacity,
          visibility: visible ? "visible" : "hidden",
        });
        card.inert = !visible || !fullyVisible || opacity < 0.72;
      }
      root.style.setProperty("--caption-alpha", String(range(handoff, 0.48, 0.97)));
      setVisible(statement, out, out === 1);
      const settle = range(distance, STATEMENT + 0.29, END);
      gsap.set(quote, { y: (1 - smooth(settle)) * 54 });
      quote.style.setProperty("--dot-alpha", String(0.8 * (1 - settle)));
      quoteText.style.opacity = String(mix(0.45, 1, settle));
      if (controls?.isConnected) setVisible(controls, 1 - range(handoff, 0, 0.22));
      root.dataset.scene = state !== "cinema" ? state : out > 0.5 ? "statement" : handoff >= 1 ? "films" : "theater";
      syncPlayback();
    }

    function resize() {
      width = stage.clientWidth;
      height = stage.clientHeight;
      if (width === 0 || height === 0) return;
      cardWidth = cards[0].offsetWidth;
      cardHeight = cards[0].offsetHeight;
      const frame = theaterFrame(width, height);
      room.style.backgroundSize = `${1440 * frame.scale}px ${900 * frame.scale}px`;
      const screen = fallbackScreen(width, height);
      room.style.setProperty("--screen-left", `${screen.x}px`);
      room.style.setProperty("--screen-top", `${screen.y}px`);
      room.style.setProperty("--screen-width", `${screen.width}px`);
      room.style.setProperty("--screen-height", `${screen.height}px`);
      roomScene?.resize(width, height);
      lenis.resize();
      render();
    }

    function releaseInput() {
      if (state !== "cinema" || touchY !== undefined) return;
      holdingInput = false;
      lenis.start();
    }

    function waitForQuiet() {
      clearTimeout(timer);
      timer = setTimeout(releaseInput, INPUT_QUIET);
    }

    function enterCinema() {
      if (state !== "opening") return;
      state = "revealing";
      holdingInput = true;
      if (opening.contains(document.activeElement)) {
        runway.focus({ preventScroll: true });
      }
      reveal = gsap.to(progress, {
        pull: 1,
        duration: 0.85,
        ease: "power1.inOut",
        onUpdate: render,
        onComplete: () => {
          state = "cinema";
          render();
          waitForQuiet();
        },
      });
      render();
    }

    function wheel(event: WheelEvent) {
      if (root.querySelector("dialog[open]")) return;
      if (event.ctrlKey || Math.abs(event.deltaX) > Math.abs(event.deltaY) || event.deltaY === 0) return;
      if (state === "cinema" && !holdingInput) return;
      event.preventDefault();
      if (event.deltaY > 0) enterCinema();
      if (holdingInput) waitForQuiet();
    }

    function touchStart(event: TouchEvent) {
      touchY = event.touches.length === 1 ? event.touches[0].clientY : undefined;
    }

    function touchMove(event: TouchEvent) {
      if (root.querySelector("dialog[open]")) return;
      if (touchY === undefined || event.touches.length !== 1 || (state === "cinema" && !holdingInput)) return;
      if (touchY - event.touches[0].clientY > 10) enterCinema();
      if (state !== "cinema" || holdingInput) event.preventDefault();
    }

    function touchEnd() {
      touchY = undefined;
      if (holdingInput) waitForQuiet();
    }

    function key(event: KeyboardEvent) {
      if (event.altKey || event.ctrlKey || event.metaKey || root.querySelector("dialog[open]")) return;
      if (event.target instanceof Element && event.target.closest("input, textarea, select, [contenteditable]")) return;
      if (event.key === " " && event.target instanceof Element && event.target.closest("button, a")) return;
      if (!["ArrowDown", "PageDown", "End", " "].includes(event.key) || event.shiftKey) return;
      if (state === "cinema" && !holdingInput) {
        event.preventDefault();
        if (event.repeat) return;
        // Give keyboard input the same bounded travel as a fresh scroll gesture.
        const step = event.key === "ArrowDown" ? 0.18 : 0.72;
        lenis.scrollTo(lenis.targetScroll + step * height, { duration: 0.42 });
        return;
      }
      event.preventDefault();
      if (!event.repeat) enterCinema();
      if (holdingInput) waitForQuiet();
    }

    const scroll = ScrollTrigger.create({
      scroller: root,
      trigger: runway,
      start: "top top",
      end: () => `+=${END * height}`,
      onUpdate: ({ progress: scrollProgress }) => {
        distance = scrollProgress * END;
        render();
      },
    });
    const updateScroll = lenis.on("scroll", ScrollTrigger.update);
    const tick = (time: number) => {
      lenis.raf(time * 1000);
      if (!document.hidden && distance < HOLD + HANDOFF && progress.pull > 0) {
        roomScene?.draw(progress.pull === 1);
      }
    };

    registerAction((destination) => {
      if (destination === "theater" && state !== "cinema") {
        enterCinema();
        return;
      }
      runway.focus({ preventScroll: true });
      clearTimeout(timer);
      reveal?.kill();
      holdingInput = false;
      state = destination === "opening" ? "opening" : "cinema";
      progress.pull = state === "opening" ? 0 : 1;
      lenis.start();
      const index = films.findIndex(({ year }) => String(year) === destination);
      const target = index >= 0 ? HOLD + HANDOFF + index * FILM_STEP : destination === "films" ? HOLD + HANDOFF : destination === "statement" ? END : 0;
      lenis.scrollTo(target * height, { immediate: true });
      if (state === "opening") lenis.stop();
      render();
    });

    lenis.stop();
    root.addEventListener("wheel", wheel, input);
    root.addEventListener("touchstart", touchStart, { signal: abort.signal, passive: true });
    root.addEventListener("touchmove", touchMove, input);
    root.addEventListener("touchend", touchEnd, { signal: abort.signal });
    root.addEventListener("touchcancel", touchEnd, { signal: abort.signal });
    document.addEventListener("keydown", key, { signal: abort.signal });
    document.addEventListener("visibilitychange", syncPlayback, { signal: abort.signal });
    video.addEventListener("loadeddata", render, { signal: abort.signal });
    video.addEventListener("error", render, { signal: abort.signal });
    const observer = new ResizeObserver(() => {
      resize();
      scroll.refresh();
    });
    observer.observe(stage);
    resize();
    scroll.refresh();
    gsap.ticker.add(tick);

    void import("./theater").then(({ createTheater }) => {
      if (abort.signal.aborted) return;
      try {
        roomScene = createTheater(canvas, video, render);
        roomScene.resize(width, height);
      } catch {
        // Keep the static theater if WebGL initialization is unavailable.
        render();
      }
    });

    stop = () => {
      clearTimeout(timer);
      abort.abort();
      reveal?.kill();
      observer.disconnect();
      gsap.ticker.remove(tick);
      updateScroll();
      scroll.kill();
      lenis.destroy();
      roomScene?.dispose();
      video.pause();
      root.removeAttribute("data-enhanced");
      root.removeAttribute("data-scene");
      root.scrollTop = 0;
      root.style.removeProperty("--caption-alpha");
      reel.removeAttribute("data-fullscreen");
      yearButtons[currentYear ?? -1]?.removeAttribute("aria-current");
      gsap.set([reel, quote, ...cards], { clearProps: "transform" });
      for (const element of [runway, opening, reel, video, theater, room, canvas, awards, backdrop, years, statement, quote, quoteText, controls, ...cards]) {
        if (element === room) {
          // Retain the source image declared by React.
          for (const property of ["visibility", "background-size", "--screen-left", "--screen-top", "--screen-width", "--screen-height"]) {
            room.style.removeProperty(property);
          }
        } else {
          element?.removeAttribute("style");
        }
        if (element !== canvas) element?.removeAttribute("inert");
      }
    };
  };

  configure();
  motion.addEventListener("change", configure);
  return () => {
    motion.removeEventListener("change", configure);
    stop();
  };
}
