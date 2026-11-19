const dotSpacing = 2.6;
const dotRadius = 0.62;
const opacityBands = 6;
// Canvas rotation is clockwise from the right. The CSS gradient is 145deg from the top.
const fillX = Math.cos((55 * Math.PI) / 180);
const fillY = Math.sin((55 * Math.PI) / 180);

type Particle = {
  x: number;
  y: number;
  scatterX: number;
  scatterY: number;
  scatterSize: number;
};

type ParticleLayout = {
  particles: Particle[];
  titleWidth: number;
  titleHeight: number;
  centerX: number;
  centerY: number;
  width: number;
  height: number;
  pixelRatio: number;
  ink: string;
};

function clamp(value: number) {
  return Math.max(0, Math.min(value, 1));
}

function smooth(value: number) {
  const t = clamp(value);
  return t * t * (3 - 2 * t);
}

export function createQuoteParticleSurface(canvas: HTMLCanvasElement, title: HTMLHeadingElement) {
  const context = canvas.getContext("2d");
  const mask = document.createElement("canvas");
  const maskContext = mask.getContext("2d", { willReadFrequently: true });
  if (!context || !maskContext) return null;

  let layout: ParticleLayout | undefined;
  let currentProgress = 0;
  let currentScale = 1;
  let currentFill = -25;
  let hasFrame = false;
  let disposed = false;

  const draw = (progress: number, scale: number, fill: number) => {
    currentProgress = progress;
    currentScale = scale;
    currentFill = fill;
    if (!layout) return;

    const { particles, titleWidth, titleHeight, centerX, centerY, width, height, pixelRatio, ink } = layout;
    if (hasFrame) context.clearRect(0, 0, width, height);
    hasFrame = false;
    if (progress < 0.25 || fill >= 100) return;

    const entry = smooth((progress - 0.25) / 0.3);
    const front = width * (1.15 - 1.51 * clamp((progress - 0.27) / 0.29));
    const halfRun = (titleWidth * fillX + titleHeight * fillY) * scale / 2;
    const fillEdge = halfRun * ((fill / 100) * 2 - 1);
    const bands = Array.from({ length: opacityBands }, () => new Path2D());

    for (const particle of particles) {
      const destinationX = centerX + (particle.x - titleWidth / 2) * scale + width * 0.8 * (1 - entry);
      const distance = destinationX - front;
      const incoming = smooth((distance + width * 0.14) / (width * 0.22));
      if (incoming === 0) continue;

      const aligned = smooth(distance / (width * 0.13));
      const spread = 1 - aligned;
      const x = destinationX + width * (particle.scatterX * 0.11 - 0.035) * spread;
      const y = centerY + (particle.y - titleHeight / 2) * scale + height * (particle.scatterY * 0.21 + 0.07 * (1 - entry)) * spread;
      const radius = dotRadius * scale * (aligned + spread * particle.scatterSize);
      if (x < -radius || x > width + radius || y < -radius || y > height + radius) continue;
      if (fill >= 0 && (x - centerX) * fillX + (y - centerY) * fillY < fillEdge) continue;

      const opacity = incoming * (0.22 + 0.78 * aligned);
      const band = bands[Math.round(opacity * opacityBands) - 1];
      if (!band) continue;
      band.moveTo(x + radius, y);
      band.arc(x, y, radius, 0, Math.PI * 2);
    }

    context.setTransform(pixelRatio, 0, 0, pixelRatio, 0, 0);
    context.fillStyle = ink;
    bands.forEach((band, index) => {
      context.globalAlpha = (index + 1) / opacityBands;
      context.fill(band);
    });
    context.globalAlpha = 1;
    hasFrame = true;
  };

  const refresh = () => {
    if (disposed) return;
    const style = window.getComputedStyle(title);
    const font = `${style.fontWeight} ${style.fontSize} ${style.fontFamily}`;
    if (!document.fonts.check(font, title.textContent ?? "")) return;

    const titleWidth = title.offsetWidth;
    const titleHeight = title.offsetHeight;
    const titleRect = title.getBoundingClientRect();
    const canvasRect = canvas.getBoundingClientRect();
    if (!titleWidth || !titleHeight || !canvasRect.width || !canvasRect.height) return;

    mask.width = titleWidth;
    mask.height = titleHeight;
    maskContext.font = font;
    maskContext.fontStretch = "condensed";
    maskContext.direction = "rtl";
    maskContext.textAlign = "center";

    const range = document.createRange();
    const scaleX = titleRect.width / titleWidth;
    const scaleY = titleRect.height / titleHeight;
    for (const line of title.querySelectorAll<HTMLSpanElement>(":scope > span")) {
      const text = line.textContent?.trim();
      if (!text) continue;
      const metrics = maskContext.measureText(text);
      const lineRect = line.getBoundingClientRect();
      const lineTop = (lineRect.top - titleRect.top) / scaleY;
      const lineHeight = lineRect.height / scaleY;
      const ascent = metrics.fontBoundingBoxAscent ?? metrics.actualBoundingBoxAscent;
      const descent = metrics.fontBoundingBoxDescent ?? metrics.actualBoundingBoxDescent;
      const baseline = lineTop + (lineHeight + ascent - descent) / 2;

      range.selectNodeContents(line);
      const lineWidth = range.getBoundingClientRect().width / scaleX;
      if (!lineWidth || !metrics.width) continue;
      maskContext.setTransform(lineWidth / metrics.width, 0, 0, 1, titleWidth / 2, 0);
      maskContext.fillText(text, 0, baseline);
    }

    const { data } = maskContext.getImageData(0, 0, titleWidth, titleHeight);
    const particles: Particle[] = [];
    let seed = 48271;
    const next = () => {
      seed = (Math.imul(seed, 1664525) + 1013904223) >>> 0;
      return seed / 4294967296;
    };
    for (let y = dotSpacing / 2; y < titleHeight; y += dotSpacing) {
      for (let x = dotSpacing / 2; x < titleWidth; x += dotSpacing) {
        const alpha = data[(Math.floor(y) * titleWidth + Math.floor(x)) * 4 + 3];
        if (alpha < 128) continue;
        particles.push({ x, y, scatterX: next() * 2 - 1, scatterY: next() * 2 - 1, scatterSize: 0.18 + next() * 0.8 });
      }
    }
    if (!particles.length) return;

    const { width, height } = canvasRect;
    const pixelRatio = Math.min(window.devicePixelRatio || 1, 2);
    canvas.width = Math.round(width * pixelRatio);
    canvas.height = Math.round(height * pixelRatio);
    layout = {
      particles,
      titleWidth,
      titleHeight,
      centerX: titleRect.left + titleRect.width / 2 - canvasRect.left,
      centerY: titleRect.top + titleRect.height / 2 - canvasRect.top,
      width,
      height,
      pixelRatio,
      ink: style.getPropertyValue("--quote-ink").trim() || "#b6ae9f",
    };
    canvas.dataset.ready = "true";
    draw(currentProgress, currentScale, currentFill);
  };

  const observer = new ResizeObserver(refresh);
  observer.observe(canvas);
  observer.observe(title);
  void document.fonts.ready.then(refresh);
  refresh();

  return {
    draw,
    dispose() {
      disposed = true;
      observer.disconnect();
      delete canvas.dataset.ready;
    },
  };
}
