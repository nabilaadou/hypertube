export type ScreenRect = { x: number; y: number; width: number; height: number };

export function theaterFrame(width: number, height: number) {
  const scale = Math.max(width / 1440, height / 900 * (width / height < 0.9 ? 0.68 : 1));
  return {
    scale,
    x: (width - 1440 * scale) / 2,
    y: (height - 900 * scale) / 2,
  };
}

export function fallbackScreen(width: number, height: number): ScreenRect {
  const frame = theaterFrame(width, height);
  return {
    x: frame.x + 477 * frame.scale,
    y: frame.y + 294 * frame.scale,
    width: 512 * frame.scale,
    height: 233 * frame.scale,
  };
}
