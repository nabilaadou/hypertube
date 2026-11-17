"use client";

import { useEffect, useRef } from "react";

const tileSize = 256;

// Grain tile adapted from React Bits Noise. See client/third-party/react-bits.LICENSE.md.
function createGrainTile() {
  const canvas = document.createElement("canvas");
  canvas.width = tileSize;
  canvas.height = tileSize;
  const context = canvas.getContext("2d");
  if (!context) return;

  const image = context.createImageData(tileSize, tileSize);
  const { data } = image;
  let seed = 1013904223;

  function random() {
    seed = (seed + 0x6d2b79f5) | 0;
    let value = Math.imul(seed ^ (seed >>> 15), seed | 1);
    value ^= value + Math.imul(value ^ (value >>> 7), value | 61);
    return ((value ^ (value >>> 14)) >>> 0) / 4294967296;
  }

  for (let index = 0; index < data.length; index += 4) {
    const tone = (0.5 + (random() + random() - 1) * 1.16) * 255;
    data[index] = tone;
    data[index + 1] = tone;
    data[index + 2] = tone;
    data[index + 3] = 255;
  }

  context.putImageData(image, 0, 0);
  return canvas;
}

export function Noise({ animated }: { animated: boolean }) {
  const grain = useRef<HTMLSpanElement>(null);

  useEffect(() => {
    const layer = grain.current;
    if (!layer) return;

    let url: string | undefined;
    let disposed = false;
    createGrainTile()?.toBlob((blob) => {
      if (!blob || disposed) return;
      url = URL.createObjectURL(blob);
      layer.style.backgroundImage = `url("${url}")`;
    });

    return () => {
      disposed = true;
      if (url) URL.revokeObjectURL(url);
    };
  }, []);

  return (
    <div className="cinema-noise" data-animated={animated} aria-hidden="true">
      <span ref={grain} />
    </div>
  );
}
