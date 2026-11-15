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
  const width = 0.28;
  const height = 0.42;
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
  group.position.set(1.0131, 0.01, -0.11942);
  group.rotation.y = -2.298179;
  group.translateX(-0.024);

  const darkMetal = new MeshStandardMaterial({ color: 0x26201a, metalness: 0.45, roughness: 0.45 });
  const antiqueGold = new MeshStandardMaterial({ color: 0x806242, metalness: 0.52, roughness: 0.43 });
  const frame = new Mesh(new BoxGeometry(width + 0.018, height + 0.018, 0.014), darkMetal);
  const frameLip = new Mesh(new BoxGeometry(width + 0.008, height + 0.008, 0.004), antiqueGold);
  frameLip.position.z = 0.008;

  const artwork = new Mesh(new PlaneGeometry(width, height), new MeshStandardMaterial({ map: poster, roughness: 0.85 }));
  artwork.position.z = 0.011;

  const lamp = new Group();
  lamp.position.y = 0.255;
  const wallMount = new Mesh(new BoxGeometry(0.04, 0.027, 0.01), darkMetal);
  const arm = new Mesh(new BoxGeometry(0.012, 0.01, 0.10), darkMetal);
  arm.position.z = 0.049;
  const shade = new Mesh(new BoxGeometry(0.16, 0.016, 0.025), antiqueGold);
  shade.position.z = 0.102;
  const diffuser = new Mesh(new BoxGeometry(0.15, 0.002, 0.019), new MeshBasicMaterial({ color: 0xf2c899, toneMapped: false }));
  diffuser.position.set(0, -0.009, 0.102);
  lamp.add(wallMount, arm, shade, diffuser);

  const light = new SpotLight(0xffddba, 0.42, 0.58, 0.65, 0.68);
  light.position.set(0, 0.243, 0.104);
  light.target.position.set(0, -0.045, artwork.position.z);
  group.add(frame, frameLip, artwork, lamp, light, light.target);

  return { group, artwork };
}
