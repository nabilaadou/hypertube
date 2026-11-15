"use client";

import { type RefObject, useEffect, useRef, useState } from "react";
import {
  ACESFilmicToneMapping,
  Box3,
  DoubleSide,
  Euler,
  MathUtils,
  Mesh,
  MeshBasicMaterial,
  MeshStandardMaterial,
  type Object3D,
  PerspectiveCamera,
  Quaternion,
  RectAreaLight,
  Scene,
  SRGBColorSpace,
  Texture,
  TextureLoader,
  Vector3,
  VideoTexture,
  WebGLRenderer,
} from "three";
import { RectAreaLightUniformsLib } from "three/addons/lights/RectAreaLightUniformsLib.js";
import { GLTFLoader } from "three/addons/loaders/GLTFLoader.js";
import { bestPictureWinners, cinemaMedia } from "../data/movies";
import { projectionScreenAspect, type ScreenCorners } from "./cinema-projection";
import { createCinemaWallPoster } from "./cinema-wall-poster";

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
  lightsOn,
  onProjection,
  onWallPosterProjection,
}: {
  videoRef: RefObject<HTMLVideoElement | null>;
  active: boolean;
  lightsOn: boolean;
  onProjection: (corners: ScreenCorners | null) => void;
  onWallPosterProjection: (corners: ScreenCorners | null) => void;
}) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const drawRef = useRef<(() => void) | null>(null);
  const lightsOnRef = useRef(lightsOn);
  const [ready, setReady] = useState(false);

  useEffect(() => {
    lightsOnRef.current = lightsOn;
    drawRef.current?.();
  }, [lightsOn]);

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
    const minimumCinemaAspect = 0.84;
    // Match the Blender camera after converting its Z-up coordinates to Y-up.
    const camera = new PerspectiveCamera(MathUtils.radToDeg(2 * Math.atan(36 / aspect / 42)), aspect, 0.01, 20);
    camera.position.set(-0.44, 0.28, -2.53);
    camera.quaternion.setFromEuler(new Euler(1.41247368, 0, -2.96940184, "ZYX"));
    camera.quaternion.premultiply(new Quaternion().setFromAxisAngle(new Vector3(1, 0, 0), -Math.PI / 2));

    RectAreaLightUniformsLib.init();
    const screenLight = new RectAreaLight(0xfff2e2, 1.5);
    const ceilingLight = new RectAreaLight(0xffe3ba, 0);
    const screenTint = screenLight.color.clone();
    const targetTint = screenTint.clone();
    let targetIntensity = screenLight.intensity;
    let sampledTime = -Infinity;
    const sample = document.createElement("canvas");
    sample.width = 8;
    sample.height = 4;
    const sampleContext = sample.getContext("2d", { willReadFrequently: true });

    const events = new AbortController();
    const texture = new VideoTexture(video);
    texture.colorSpace = SRGBColorSpace;
    texture.flipY = false;

    function fitVideoTexture() {
      if (!video?.videoHeight) return;
      const videoAspect = video.videoWidth / video.videoHeight;
      const cropX = Math.min(1, projectionScreenAspect / videoAspect);
      const cropY = Math.min(1, videoAspect / projectionScreenAspect);
      texture.repeat.set(-cropX, cropY);
      texture.offset.set((1 + cropX) / 2, (1 - cropY) / 2);
    }
    fitVideoTexture();
    video.addEventListener("loadedmetadata", fitVideoTexture, { signal: events.signal });
    const screenMaterial = new MeshBasicMaterial({ map: texture, side: DoubleSide, toneMapped: false });
    let model: Object3D | undefined;
    let screenMesh: Mesh | undefined;
    let wallPoster: ReturnType<typeof createCinemaWallPoster> | undefined;
    let ceilingMaterial: MeshStandardMaterial | undefined;
    let firstFrame = true;

    function draw() {
      if (!model || !canvas || !video || video.readyState < HTMLMediaElement.HAVE_CURRENT_DATA) return;
      if (renderer.getContext().isContextLost()) return;

      if (sampleContext && Math.abs(video.currentTime - sampledTime) >= 0.15) {
        sampledTime = video.currentTime;
        const width = video.videoWidth * Math.abs(texture.repeat.x);
        const height = video.videoHeight * texture.repeat.y;
        const left = (video.videoWidth - width) / 2;
        const top = (video.videoHeight - height) / 2;
        sampleContext.drawImage(video, left, top, width, height, 0, 0, sample.width, sample.height);
        const pixels = sampleContext.getImageData(0, 0, sample.width, sample.height).data;
        let red = 0;
        let green = 0;
        let blue = 0;
        for (let pixel = 0; pixel < pixels.length; pixel += 4) {
          red += pixels[pixel];
          green += pixels[pixel + 1];
          blue += pixels[pixel + 2];
        }
        const luminance = (0.2126 * red + 0.7152 * green + 0.0722 * blue) / (sample.width * sample.height * 255);
        targetIntensity = Math.min(2.6, 4 * luminance);
        const peak = Math.max(red, green, blue, 1);
        targetTint.setRGB(red / peak, green / peak, blue / peak, SRGBColorSpace).lerp(screenTint, 0.65);
      }
      screenLight.intensity = MathUtils.lerp(screenLight.intensity, targetIntensity, firstFrame ? 1 : 0.15);
      screenLight.color.lerp(targetTint, firstFrame ? 1 : 0.15);
      if (ceilingMaterial) {
        ceilingLight.intensity = lightsOnRef.current ? 0.95 : 0;
        ceilingMaterial.emissiveIntensity = lightsOnRef.current ? 3 : 0;
      }
      renderer.render(scene, camera);
      if (firstFrame) {
        firstFrame = false;
        setReady(true);
      }
    }

    function resize() {
      if (!canvas?.clientWidth || !canvas.clientHeight) return;
      const width = canvas.clientWidth;
      const height = canvas.clientHeight;
      const stage = canvas.parentElement;
      const stageWidth = stage?.clientWidth ?? width;
      const stageHeight = stage?.clientHeight ?? height;
      renderer.setSize(width, height, false);
      camera.aspect = width / height;
      camera.zoom = Math.min(1, stageWidth / height / minimumCinemaAspect);
      camera.updateProjectionMatrix();
      if (screenMesh && stage) {
        camera.updateMatrixWorld();
        function project(mesh: Mesh, index: number) {
          const point = new Vector3().fromBufferAttribute(mesh.geometry.getAttribute("position"), index);
          mesh.localToWorld(point).project(camera);
          return { x: (stageWidth + point.x * width) / 2, y: (stageHeight - point.y * height) / 2 };
        }
        onProjection([project(screenMesh, 3), project(screenMesh, 2), project(screenMesh, 0), project(screenMesh, 1)]);
        if (wallPoster) {
          const { artwork, setMount } = wallPoster;
          const projectArtwork = (): ScreenCorners => [project(artwork, 0), project(artwork, 1), project(artwork, 3), project(artwork, 2)];
          const inset = Math.min(6, stageWidth * 0.015);
          const fits = (corners: ScreenCorners) => corners.every(({ x, y }) => (
            x >= inset && x <= stageWidth - inset && y >= 0 && y <= stageHeight
          ));

          setMount("side-wall");
          let corners = projectArtwork();
          if (!fits(corners)) {
            setMount("front-panel");
            corners = projectArtwork();
          }
          onWallPosterProjection(fits(corners) ? corners : null);
        }
      }
      draw();
    }

    const observer = new ResizeObserver(resize);
    observer.observe(canvas);
    if (canvas.parentElement) observer.observe(canvas.parentElement);
    video.addEventListener("loadeddata", draw, { signal: events.signal });
    canvas.addEventListener("webglcontextlost", (event) => {
      event.preventDefault();
      setReady(false);
    }, { signal: events.signal });
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

      const screenBounds = new Box3().setFromObject(screen);
      const screenSize = screenBounds.getSize(new Vector3());
      screenLight.width = screenSize.x;
      screenLight.height = screenSize.y;
      screenBounds.getCenter(screenLight.position);
      screenLight.position.z -= 0.01;

      theater.traverse((object) => {
        if (!(object instanceof Mesh) || object === screen) return;
        const materials = Array.isArray(object.material) ? object.material : [object.material];
        for (const material of materials) {
          if (!(material instanceof MeshStandardMaterial)) continue;
          material.emissiveIntensity = 0;
          if (material.name === "Warm dimmed light panels") {
            material.color.set(0x241f1b);
            material.roughness = 0.9;
          }
        }
      });

      const ceiling = theater.getObjectByName("Ceiling_light_panels");
      if (ceiling instanceof Mesh && ceiling.material instanceof MeshStandardMaterial) {
        ceilingMaterial = ceiling.material.clone();
        ceiling.material = ceilingMaterial;

        const bounds = new Box3().setFromObject(ceiling);
        const size = bounds.getSize(new Vector3());
        ceilingLight.width = size.x;
        ceilingLight.height = size.z;
        bounds.getCenter(ceilingLight.position);
        ceilingLight.position.y -= 0.015;
        ceilingLight.rotation.x = -Math.PI / 2;
        scene.add(ceilingLight);
      }

      const original = Array.isArray(screen.material) ? screen.material : [screen.material];
      for (const material of original) material.dispose();
      screen.material = screenMaterial;
      screenMesh = screen;
      model = theater;
      scene.add(theater, screenLight);
      resize();
      drawRef.current = draw;

      void new TextureLoader().loadAsync(bestPictureWinners[0].poster).then((poster) => {
        if (events.signal.aborted) {
          poster.dispose();
          return;
        }

        wallPoster = createCinemaWallPoster(poster);
        theater.add(wallPoster.group);
        resize();
      }).catch(() => {});
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
      onProjection(null);
      onWallPosterProjection(null);
    };
  }, [videoRef, onProjection, onWallPosterProjection]);

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
      data-live={active && ready}
      className="cinema-canvas"
      style={{ visibility: active && ready ? "visible" : "hidden" }}
    />
  );
}
