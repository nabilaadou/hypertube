import { createHash } from "node:crypto";
import { createReadStream, createWriteStream } from "node:fs";
import { mkdir, mkdtemp, rename, rm } from "node:fs/promises";
import { dirname, join } from "node:path";
import { Readable } from "node:stream";
import { pipeline } from "node:stream/promises";

const assets = [
  { path: "media/reel.mp4", sha256: "fcc79ea56f80d47e3badb8d4235038b0aec103d8527f8bfe9da055df65241c2d" },
  { path: "media/reel-poster.jpg", sha256: "e68fbe275f272e9abeea4313eb0d3c8a32f1d26720bfb98e33ecfbbeeeaac37d" },
  { path: "media/theater.png", sha256: "9f77ba5ca47512df550deab82f6fd0013a0d13e4f97e62c58ae5ac52701d0468" },
  { path: "models/private-home-cinema-textured.glb", sha256: "2cae3303bc416cd3e07d44274f7c82f6b35d2f7926ef56b1beb087d045578e5b" },
];

const baseUrl = process.env.ASSET_BASE_URL;
const directory = process.env.ASSET_DIRECTORY ?? "/assets";
if (!baseUrl) throw new Error("ASSET_BASE_URL is required.");

async function checksum(path) {
  const hash = createHash("sha256");
  try {
    for await (const chunk of createReadStream(path)) hash.update(chunk);
    return hash.digest("hex");
  } catch (error) {
    if (error.code === "ENOENT") return null;
    throw error;
  }
}

for (const asset of assets) {
  const target = join(directory, asset.path);
  if (await checksum(target) === asset.sha256) {
    console.log(`Using verified asset: ${asset.path}`);
    continue;
  }

  const url = new URL(asset.path, `${baseUrl.replace(/\/+$/, "")}/`);
  await mkdir(dirname(target), { recursive: true });
  const temporaryDirectory = await mkdtemp(join(dirname(target), ".asset-"));
  const temporaryFile = join(temporaryDirectory, "download");
  try {
    console.log(`Downloading asset: ${asset.path}`);
    const response = await fetch(url);
    if (!response.ok || !response.body) {
      throw new Error(`Download failed for ${url}: HTTP ${response.status}`);
    }
    await pipeline(Readable.fromWeb(response.body), createWriteStream(temporaryFile));
    if (await checksum(temporaryFile) !== asset.sha256) {
      throw new Error(`Checksum mismatch for ${asset.path}`);
    }
    await rename(temporaryFile, target);
  } finally {
    await rm(temporaryDirectory, { recursive: true, force: true });
  }
}
