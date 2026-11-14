import {
  AmbientLight,
  Box3,
  DirectionalLight,
  DoubleSide,
  Mesh,
  MeshBasicMaterial,
  PerspectiveCamera,
  PointLight,
  Scene,
  SRGBColorSpace,
  Texture,
  Vector3,
  VideoTexture,
  WebGLRenderer,
  type Object3D,
} from "three";
import { GLTFLoader } from "three/addons/loaders/GLTFLoader.js";
import { media } from "./films";

import { type ScreenRect, theaterFrame } from "./framing";

function disposeModel(model: Object3D) {
  const textures = new Set<Texture>();
  model.traverse((object) => {
    if (!(object instanceof Mesh)) return;
    object.geometry.dispose();
    const materials = Array.isArray(object.material) ? object.material : [object.material];
    for (const material of materials) {
      for (const value of Object.values(material)) {
        if (value instanceof Texture) textures.add(value);
      }
      material.dispose();
    }
  });
  for (const texture of textures) {
    texture.dispose();
    if (texture.source.data instanceof ImageBitmap) texture.source.data.close();
  }
}

export function createTheater(canvas: HTMLCanvasElement, video: HTMLVideoElement, onChange: () => void) {
  const renderer = new WebGLRenderer({ canvas, antialias: true, alpha: false });
  renderer.setClearColor(0x100d15);
  const scene = new Scene();
  const camera = new PerspectiveCamera(64, 1440 / 900, 0.025, 12);
  camera.position.set(0, -0.17, -2.25);
  camera.lookAt(0, -0.17, 0);
  camera.updateMatrixWorld();
  scene.add(new AmbientLight(0xffe7c8, 1.35));
  const fill = new DirectionalLight(0xffefe1, 2.1);
  fill.position.set(0.1, 0.65, -0.2);
  scene.add(fill);
  const rear = new PointLight(0xffd4a0, 3.8, 6);
  rear.position.set(0, 0.25, -2.1);
  scene.add(rear);

  let model: Object3D | undefined;
  let screen: Box3 | undefined;
  let videoTexture: VideoTexture | undefined;
  let screenMaterial: MeshBasicMaterial | undefined;
  let available = true;
  let dirty = true;
  let lastTime = -1;
  let disposed = false;
  let width = 1;
  let height = 1;

  function setCrop() {
    if (!videoTexture || !video.videoWidth || !screen) return;
    const videoAspect = video.videoWidth / video.videoHeight;
    const screenAspect = (screen.max.x - screen.min.x) / (screen.max.y - screen.min.y);
    const repeatX = Math.min(1, screenAspect / videoAspect);
    const repeatY = Math.min(1, videoAspect / screenAspect);
    videoTexture.repeat.set(repeatX, repeatY);
    videoTexture.offset.set((1 - repeatX) / 2, (1 - repeatY) / 2);
    dirty = true;
  }

  function contextLost(event: Event) {
    event.preventDefault();
    available = false;
    onChange();
  }

  function contextRestored() {
    available = true;
    dirty = true;
    onChange();
  }

  canvas.addEventListener("webglcontextlost", contextLost);
  canvas.addEventListener("webglcontextrestored", contextRestored);
  video.addEventListener("loadedmetadata", setCrop);
  video.addEventListener("seeked", setCrop);

  new GLTFLoader().loadAsync(media.model).then(({ scene: room }) => {
    const projection = room.getObjectByName("Projection_screen");
    if (disposed || !(projection instanceof Mesh) || !projection.geometry.getAttribute("uv")) {
      disposeModel(room);
      return;
    }

    room.updateMatrixWorld(true);
    screen = new Box3().setFromObject(projection);
    const geometry = projection.geometry;
    const position = geometry.getAttribute("position");
    const uv = geometry.getAttribute("uv");
    const vertex = new Vector3();
    for (let index = 0; index < position.count; index++) {
      vertex.fromBufferAttribute(position, index).applyMatrix4(projection.matrixWorld);
      // The camera looks toward +Z, so world +X is the left edge of the screen.
      uv.setXY(
        index,
        (screen.max.x - vertex.x) / (screen.max.x - screen.min.x),
        (vertex.y - screen.min.y) / (screen.max.y - screen.min.y),
      );
    }
    uv.needsUpdate = true;

    videoTexture = new VideoTexture(video);
    videoTexture.colorSpace = SRGBColorSpace;
    screenMaterial = new MeshBasicMaterial({ side: DoubleSide, color: 0x100d15, toneMapped: false });
    const originalMaterials = Array.isArray(projection.material) ? projection.material : [projection.material];
    for (const material of originalMaterials) material.dispose();
    projection.material = screenMaterial;
    setCrop();
    model = room;
    scene.add(room);
    dirty = true;
    onChange();
  }).catch(() => {
    // The image and DOM video remain available when the model cannot be loaded.
  });

  return {
    resize(nextWidth: number, nextHeight: number) {
      width = nextWidth;
      height = nextHeight;
      const frame = theaterFrame(width, height);
      renderer.setPixelRatio(Math.min(window.devicePixelRatio, 1.75));
      renderer.setSize(width, height, false);
      camera.setViewOffset(1440, 900, -frame.x / frame.scale, -frame.y / frame.scale, width / frame.scale, height / frame.scale);
      dirty = true;
    },
    get ready() {
      return available && Boolean(model);
    },
    get screen(): ScreenRect | undefined {
      if (!available || !screen) return undefined;
      const topLeft = new Vector3(screen.max.x, screen.max.y, screen.min.z).project(camera);
      const bottomRight = new Vector3(screen.min.x, screen.min.y, screen.min.z).project(camera);
      return {
        x: (topLeft.x + 1) * width / 2,
        y: (1 - topLeft.y) * height / 2,
        width: (bottomRight.x - topLeft.x) * width / 2,
        height: (topLeft.y - bottomRight.y) * height / 2,
      };
    },
    draw(useVideo: boolean) {
      if (!available || !model || !screenMaterial) return;
      const texture = useVideo && video.readyState >= 2 ? videoTexture : null;
      if (screenMaterial.map !== texture) {
        screenMaterial.map = texture ?? null;
        screenMaterial.color.setHex(texture ? 0xffffff : 0x100d15);
        screenMaterial.needsUpdate = true;
        dirty = true;
      }
      if (!dirty && (!texture || video.currentTime === lastTime)) return;
      renderer.render(scene, camera);
      lastTime = video.currentTime;
      dirty = false;
    },
    dispose() {
      disposed = true;
      canvas.removeEventListener("webglcontextlost", contextLost);
      canvas.removeEventListener("webglcontextrestored", contextRestored);
      video.removeEventListener("loadedmetadata", setCrop);
      video.removeEventListener("seeked", setCrop);
      if (model) disposeModel(model);
      renderer.dispose();
      renderer.forceContextLoss();
    },
  };
}
