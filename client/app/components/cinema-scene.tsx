"use client";

import { type RefObject, useEffect, useRef, useState } from "react";
import {
  ACESFilmicToneMapping,
  DirectionalLight,
  DoubleSide,
  Euler,
  HemisphereLight,
  MathUtils,
  Mesh,
  MeshBasicMaterial,
  type Object3D,
  PerspectiveCamera,
  PointLight,
  Quaternion,
  Scene,
  SRGBColorSpace,
  Texture,
  Vector3,
  VideoTexture,
  WebGLRenderer,
} from "three";
import { GLTFLoader } from "three/addons/loaders/GLTFLoader.js";
import { cinemaMedia } from "../data/movies";

function disposeModel(model: Object3D) {
  model.traverse((object) => {
    if (!(object instanceof Mesh)) return;
    object.geometry.dispose();
    const materials = Array.isArray(object.material) ? object.material : [object.material];
    for (const material of materials) {
      for (const value of Object.values(material)) {
        if (value instanceof Texture) value.dispose();
      }
      material.dispose();
    }
  });
}

export default function CinemaScene({
  videoRef,
  active,
}: {
  videoRef: RefObject<HTMLVideoElement | null>;
  active: boolean;
}) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const drawRef = useRef<(() => void) | null>(null);
  const [ready, setReady] = useState(false);

  useEffect(() => {
    const canvas = canvasRef.current;
    const video = videoRef.current;
    if (!canvas || !video) return;

    let renderer: WebGLRenderer;
    try {
      renderer = new WebGLRenderer({ canvas, antialias: true, alpha: true });
    } catch {
      return;
    }

    renderer.setPixelRatio(Math.min(window.devicePixelRatio, 1.5));
    renderer.toneMapping = ACESFilmicToneMapping;
    renderer.toneMappingExposure = 1.15;

    const scene = new Scene();
    const aspect = 1440 / 900;
    // Match the Blender camera after converting its Z-up coordinates to Y-up.
    const camera = new PerspectiveCamera(MathUtils.radToDeg(2 * Math.atan(36 / aspect / 42)), aspect, 0.01, 20);
    camera.position.set(-0.44, 0.28, -2.53);
    camera.quaternion.setFromEuler(new Euler(1.41247368, 0, -2.96940184, "ZYX"));
    camera.quaternion.premultiply(new Quaternion().setFromAxisAngle(new Vector3(1, 0, 0), -Math.PI / 2));

    scene.add(new HemisphereLight(0xffeddb, 0x65516a, 1.05));
    const fill = new DirectionalLight(0xffe5c3, 1.6);
    fill.position.set(-0.2, 0.3, -2.4);
    scene.add(fill);
    const screenSpill = new PointLight(0xffe8c7, 1.3);
    screenSpill.position.set(0, 0.02, -0.26);
    scene.add(screenSpill);

    const events = new AbortController();
    const texture = new VideoTexture(video);
    texture.colorSpace = SRGBColorSpace;
    texture.flipY = false;
    const screenAspect = 1.56083 / 0.68611;

    function fitVideoTexture() {
      if (!video?.videoHeight) return;
      const videoAspect = video.videoWidth / video.videoHeight;
      const cropX = Math.min(1, screenAspect / videoAspect);
      const cropY = Math.min(1, videoAspect / screenAspect);
      texture.repeat.set(-cropX, cropY);
      texture.offset.set((1 + cropX) / 2, (1 - cropY) / 2);
    }
    fitVideoTexture();
    video.addEventListener("loadedmetadata", fitVideoTexture, { signal: events.signal });
    const screenMaterial = new MeshBasicMaterial({ map: texture, side: DoubleSide, toneMapped: false });
    let model: Object3D | undefined;
    let firstFrame = true;

    function draw() {
      if (!model || !canvas || !video || video.readyState < HTMLMediaElement.HAVE_CURRENT_DATA) return;
      if (renderer.getContext().isContextLost()) return;
      renderer.render(scene, camera);
      if (firstFrame) {
        firstFrame = false;
        setReady(true);
      }
    }

    function resize() {
      if (!canvas?.clientWidth || !canvas.clientHeight) return;
      renderer.setSize(canvas.clientWidth, canvas.clientHeight, false);
      draw();
    }

    const observer = new ResizeObserver(resize);
    observer.observe(canvas);
    video.addEventListener("loadeddata", draw, { signal: events.signal });
    canvas.addEventListener("webglcontextlost", () => setReady(false), { signal: events.signal });
    canvas.addEventListener("webglcontextrestored", () => {
      firstFrame = true;
      draw();
    }, { signal: events.signal });

    void new GLTFLoader().loadAsync(cinemaMedia.theaterModel).then(({ scene: theater }) => {
      const screen = theater.getObjectByName("Projection_screen");
      if (events.signal.aborted || !(screen instanceof Mesh)) {
        disposeModel(theater);
        return;
      }

      const original = Array.isArray(screen.material) ? screen.material : [screen.material];
      for (const material of original) material.dispose();
      screen.material = screenMaterial;
      model = theater;
      scene.add(theater);
      resize();
      drawRef.current = draw;
    }).catch(() => {});

    return () => {
      events.abort();
      observer.disconnect();
      drawRef.current = null;
      if (model) disposeModel(model);
      else {
        texture.dispose();
        screenMaterial.dispose();
      }
      renderer.dispose();
    };
  }, [videoRef]);

  useEffect(() => {
    const video = videoRef.current;
    if (!active || !ready || !video) return;

    let frame: number;
    function renderFrame() {
      drawRef.current?.();
      if (!video) return;
      frame = video.requestVideoFrameCallback(renderFrame);
    }
    renderFrame();
    return () => video.cancelVideoFrameCallback(frame);
  }, [active, ready, videoRef]);

  return (
    <canvas
      ref={canvasRef}
      aria-label="Three-dimensional cinema"
      data-ready={ready}
      className="cinema-canvas"
      style={{ opacity: active && ready ? 1 : 0 }}
    />
  );
}
