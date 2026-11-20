export const compactLandingQuery = "(width < 48rem), (height < 32rem) and (pointer: coarse)";
export const cinemaCameraAspect = 1440 / 900;

export function cinemaFrame(width: number, height: number, compact: boolean) {
  const roomWidth = Math.max(width, height * cinemaCameraAspect);
  const roomHeight = roomWidth / cinemaCameraAspect;
  const zoom = Math.min(1, width / roomHeight / (compact ? 0.88 : 0.84));
  return { roomWidth, roomHeight, zoom };
}

export const projectionScreenAspect = 1.56083 / 0.68611;

type ScreenPoint = { x: number; y: number };

// Ordered clockwise from the upper left in stage coordinates.
export type ScreenCorners = readonly [ScreenPoint, ScreenPoint, ScreenPoint, ScreenPoint];

function screenTransform([a, b, c, d]: ScreenCorners) {
  // Project a unit rectangle onto the four corners of the screen.
  const x1 = b.x - c.x;
  const x2 = d.x - c.x;
  const x3 = a.x - b.x + c.x - d.x;
  const y1 = b.y - c.y;
  const y2 = d.y - c.y;
  const y3 = a.y - b.y + c.y - d.y;
  const determinant = x1 * y2 - x2 * y1;
  const g = (x3 * y2 - x2 * y3) / determinant;
  const h = (x1 * y3 - x3 * y1) / determinant;

  return new DOMMatrix([
    b.x - a.x + g * b.x, b.y - a.y + g * b.y, 0, g,
    d.x - a.x + h * d.x, d.y - a.y + h * d.y, 0, h,
    0, 0, 1, 0,
    a.x, a.y, 0, 1,
  ]);
}

export function projectVideo({
  stage,
  layer,
  world,
  corners,
  progress,
  compact,
}: {
  stage: HTMLElement;
  layer: HTMLElement;
  world: HTMLElement;
  corners: ScreenCorners | null;
  progress: number;
  compact: boolean;
}) {
  const width = stage.clientWidth;
  const height = stage.clientHeight;
  if (!width || !height) return;

  const frame = cinemaFrame(width, height, compact);
  const previewScale = compact ? frame.zoom : 1;
  const roomWidth = frame.roomWidth * previewScale;
  const roomHeight = frame.roomHeight * previewScale;
  if (compact) world.style.setProperty("--cinema-preview-width", `${roomWidth}px`);
  const left = (width - roomWidth) / 2 + roomWidth * 477 / 1440;
  const top = (height - roomHeight) / 2 + roomHeight * 294 / 900;
  const right = left + roomWidth * 512 / 1440;
  const bottom = top + roomHeight * 233 / 900;
  const destination: ScreenCorners = corners ?? [
    { x: left, y: top }, { x: right, y: top },
    { x: right, y: bottom }, { x: left, y: bottom },
  ];
  const mix = (from: number, to: number) => from + (to - from) * progress;
  const point = (x: number, y: number, target: ScreenPoint) => ({
    x: mix(x, target.x),
    y: mix(y, target.y),
  });
  const [a, b, c, d] = destination;
  const projected = screenTransform([
    point(0, 0, a), point(width, 0, b),
    point(width, height, c), point(0, height, d),
  ]);
  const targetWidth = Math.hypot(b.x - a.x, b.y - a.y);
  const sourceWidth = mix(width, targetWidth);
  const sourceHeight = mix(height, corners ? targetWidth / projectionScreenAspect : bottom - top);
  layer.style.width = `${sourceWidth}px`;
  layer.style.height = `${sourceHeight}px`;
  layer.style.transform = projected.scale(1 / sourceWidth, 1 / sourceHeight).toString();

  // The room and its projection surface move together until both reach their resting position.
  world.style.transform = progress === 1
    ? ""
    : projected.multiply(screenTransform(destination).inverse()).toString();
}
