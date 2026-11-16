import {
  BoxGeometry,
  Group,
  Mesh,
  MeshBasicMaterial,
  MeshStandardMaterial,
  PlaneGeometry,
  SpotLight,
  SRGBColorSpace,
  type Texture,
} from "three";

export function createCinemaWallPoster(poster: Texture) {
  const width = 0.136;
  const height = 0.204;
  poster.colorSpace = SRGBColorSpace;
  if (poster.image instanceof HTMLImageElement) {
    const imageAspect = poster.image.naturalWidth / poster.image.naturalHeight;
    const frameAspect = width / height;
    const cropX = Math.min(1, frameAspect / imageAspect);
    const cropY = Math.min(1, imageAspect / frameAspect);
    poster.repeat.set(cropX, cropY);
    poster.offset.set((1 - cropX) / 2, (1 - cropY) / 2);
  }

  const group = new Group();
  const darkMetal = new MeshStandardMaterial({ color: 0x26201a, metalness: 0.45, roughness: 0.45 });
  const antiqueGold = new MeshStandardMaterial({ color: 0x806242, metalness: 0.52, roughness: 0.43 });
  const frame = new Mesh(new BoxGeometry(width + 0.012, height + 0.012, 0.014), darkMetal);
  const frameLip = new Mesh(new BoxGeometry(width + 0.006, height + 0.006, 0.004), antiqueGold);
  frameLip.position.z = 0.008;

  const artwork = new Mesh(new PlaneGeometry(width, height), new MeshStandardMaterial({ map: poster, roughness: 0.85 }));
  artwork.position.z = 0.011;
  const backing = new Mesh(new PlaneGeometry(width, height), darkMetal);
  backing.position.z = 0.0105;

  const lamp = new Group();
  lamp.position.y = 0.175;
  const wallMount = new Mesh(new BoxGeometry(0.026, 0.02, 0.01), darkMetal);
  const arm = new Mesh(new BoxGeometry(0.009, 0.008, 0.05), darkMetal);
  arm.position.z = 0.024;
  const shade = new Mesh(new BoxGeometry(0.10, 0.013, 0.02), antiqueGold);
  shade.position.z = 0.05;
  const diffuser = new Mesh(new BoxGeometry(0.092, 0.002, 0.014), new MeshBasicMaterial({ color: 0xf2c899, toneMapped: false }));
  diffuser.position.set(0, -0.0075, shade.position.z);
  lamp.add(wallMount, arm, shade, diffuser);

  const light = new SpotLight(0xffddba, 0.23, 0.43, 0.54, 0.72);
  light.position.set(0, lamp.position.y - 0.01, shade.position.z);
  light.target.position.set(0, -0.024, artwork.position.z);
  group.add(frame, frameLip, backing, artwork, lamp, light, light.target);

  function setMount(mount: "side-wall" | "front-panel") {
    const scale = mount === "side-wall" ? 1 : 1.3;
    group.scale.setScalar(scale);
    light.intensity = 0.23 * scale ** 2;
    light.distance = 0.43 * scale;

    if (mount === "side-wall") {
      group.position.set(1.112, 0.065, -1.036);
      group.rotation.y = -Math.PI / 2;
    } else {
      group.position.set(1.0131, 0.01, -0.11942);
      group.rotation.y = -2.298179;
      group.translateX(-0.024);
    }
  }

  setMount("side-wall");
  return { group, artwork, setMount };
}
