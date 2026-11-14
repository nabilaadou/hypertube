import { copyFile, mkdir, stat } from "node:fs/promises";

const assets = [
  ["../../models/private-home-cinema-textured.glb", "../public/models/private-home-cinema-textured.glb"],
  ["../../design-assets/reel.mp4", "../public/media/reel.mp4"],
  ["../../design-assets/reel-poster.jpg", "../public/media/reel-poster.jpg"],
  ["../../design-assets/theater.png", "../public/media/theater.png"],
];

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
