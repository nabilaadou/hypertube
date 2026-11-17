import { copyFile, mkdir, readdir, stat } from "node:fs/promises";

const repairedReel = new URL("../../design-assets/reel-with-sound.mp4", import.meta.url);
const reel = await stat(repairedReel).then(
  () => "../../design-assets/reel-with-sound.mp4",
  (error) => {
    if (error.code !== "ENOENT") throw error;
    return "../../design-assets/reel.mp4";
  },
);

const assets = [
  ["../../models/private-home-cinema-textured.glb", "../public/models/private-home-cinema-textured.glb"],
  [reel, "../public/media/reel.mp4"],
  ["../../design-assets/reel-poster.jpg", "../public/media/reel-poster.jpg"],
  ["../../design-assets/theater.png", "../public/media/theater.png"],
];

const trailerDirectory = new URL("../../assets/trailers-compatible/", import.meta.url);
const trailerFiles = await readdir(trailerDirectory).catch((error) => {
  if (error.code !== "ENOENT") throw error;
  console.warn(`Source unavailable: ${trailerDirectory.pathname}. Supply compatible trailers to enable playback.`);
  return [];
});

for (const file of trailerFiles) {
  if (file.endsWith(".mp4")) {
    assets.push([`../../assets/trailers-compatible/${file}`, `../public/media/trailers/${file}`]);
  }
}

for (const [sourcePath, targetPath] of assets) {
  const source = new URL(sourcePath, import.meta.url);
  const target = new URL(targetPath, import.meta.url);

  try {
    await stat(source);
  } catch (error) {
    if (error.code !== "ENOENT") throw error;
    console.warn(`Source unavailable: ${source.pathname}. Supply ${target.pathname} to enable this asset.`);
    continue;
  }

  await mkdir(new URL(".", target), { recursive: true });
  await copyFile(source, target);
  console.log(`Prepared ${target.pathname}`);
}
