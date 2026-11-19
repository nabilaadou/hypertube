import {
  DoubleSide,
  Mesh,
  PerspectiveCamera,
  PlaneGeometry,
  Scene,
  ShaderMaterial,
  SRGBColorSpace,
  TextureLoader,
  Vector2,
  WebGLRenderer,
} from "three";

export type PaperPose = { curl: number; twist: number; ripple: number; turn: number };

const vertexShader = `
  uniform float curl;
  uniform float twist;
  uniform float ripple;
  uniform vec2 imageScale;
  varying vec2 artworkUv;
  varying vec3 paperNormal;

  vec3 deform(vec3 point) {
    float x = point.x;
    float y = point.y;
    float arc = 2.3 * curl * x;
    float arcSquared = arc * arc;
    float spread = 1.0 - arcSquared / 6.0 + arcSquared * arcSquared / 120.0;
    float angle = twist * y * 0.85;
    float edge = 0.4 + 0.6 * smoothstep(0.0, 0.5, abs(x));
    float wave = sin(7.0 * y - 3.0 * x + 2.0 * ripple);
    return vec3(
      x * spread * cos(angle),
      y + 0.08 * curl * x + 0.055 * ripple * edge * wave,
      1.25 * curl * (x * x - 0.08) + x * sin(angle) + 0.095 * ripple * edge * wave
    );
  }

  void main() {
    vec3 point = deform(position);
    vec3 dx = deform(position + vec3(0.004, 0.0, 0.0)) - point;
    vec3 dy = deform(position + vec3(0.0, 0.004, 0.0)) - point;
    paperNormal = normalMatrix * normalize(cross(dx, dy));
    artworkUv = (uv - 0.5) * imageScale + 0.5;
    gl_Position = projectionMatrix * modelViewMatrix * vec4(point, 1.0);
  }
`;

const fragmentShader = `
  uniform sampler2D artwork;
  varying vec2 artworkUv;
  varying vec3 paperNormal;

  void main() {
    vec3 normal = normalize(gl_FrontFacing ? paperNormal : -paperNormal);
    vec3 light = normalize(vec3(-0.65, 0.55, 0.65));
    float shade = clamp(1.0 + 0.38 * (dot(normal, light) - light.z), 0.62, 1.13);
    vec2 printUv = gl_FrontFacing ? artworkUv : vec2(1.0 - artworkUv.x, artworkUv.y);
    vec4 ink = texture2D(artwork, printUv);
    gl_FragColor = vec4(ink.rgb * shade, ink.a);
    #include <colorspace_fragment>
  }
`;

export function createCinemaPaperSurface(canvas: HTMLCanvasElement, posterUrl: string, onReady: () => void) {
  let renderer: WebGLRenderer;
  try {
    renderer = new WebGLRenderer({ canvas, alpha: true, antialias: true });
  } catch {
    return null;
  }

  renderer.setSize(512, 768, false);
  const scene = new Scene();
  const camera = new PerspectiveCamera(2 * Math.atan(0.975 / 3.5) * 180 / Math.PI, 2 / 3, 0.1, 10);
  camera.position.z = 3.5;
  const geometry = new PlaneGeometry(1, 1.5, 30, 38);
  const uniforms = {
    artwork: { value: null },
    imageScale: { value: new Vector2(1, 1) },
    curl: { value: 0 },
    twist: { value: 0 },
    ripple: { value: 0 },
  };
  const material = new ShaderMaterial({ uniforms, vertexShader, fragmentShader, side: DoubleSide });
  const sheet = new Mesh(geometry, material);
  scene.add(sheet);

  let disposed = false;
  let loaded = false;
  const texture = new TextureLoader().load(posterUrl, () => {
    if (disposed) return;
    const image = texture.image;
    if (image instanceof HTMLImageElement) {
      const aspect = image.naturalWidth / image.naturalHeight;
      uniforms.imageScale.value.set(Math.min(1, (2 / 3) / aspect), Math.min(1, aspect / (2 / 3)));
    }
    material.uniforms.artwork.value = texture;
    loaded = true;
    if (!renderer.getContext().isContextLost()) onReady();
  });
  texture.colorSpace = SRGBColorSpace;

  function draw(pose: PaperPose) {
    if (!loaded || renderer.getContext().isContextLost()) return;
    uniforms.curl.value = pose.curl;
    uniforms.twist.value = pose.twist;
    uniforms.ripple.value = pose.ripple;
    sheet.rotation.y = pose.turn * Math.PI / 180;
    renderer.render(scene, camera);
  }

  return {
    draw,
    dispose() {
      disposed = true;
      texture.dispose();
      geometry.dispose();
      material.dispose();
      renderer.dispose();
    },
  };
}
