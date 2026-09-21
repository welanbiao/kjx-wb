import type { ImageCaptchaTrack, ImageCaptchaTrackPoint, TrackPointType } from "@/lib/captcha/types";

export function toIsoTime(ms: number): string {
  return new Date(ms).toISOString();
}

export function point(x: number, y: number, t: number, type: TrackPointType): ImageCaptchaTrackPoint {
  return { x, y, t, type };
}

export type SliderSample = { x: number; y: number; t: number };

const MIN_SLIDER_POINTS = 12;
const MIN_SLIDER_MS = 300;
const MAX_STEP = 45;

/**
 * Densify the user's actual path so tianai BasicCaptchaTrackValidator can accept it:
 * at least 10 points, longer than 300ms, first point near the origin, no step over 50px.
 * The final x is unchanged, so the gap percentage stays the user's release position.
 * Gestures shorter than 300ms are rejected instead of padding the clock.
 */
export function normalizeSliderTrack(samples: SliderSample[]): ImageCaptchaTrackPoint[] | null {
  if (samples.length < 2) {
    return null;
  }
  const end = samples[samples.length - 1];
  if (end.t < MIN_SLIDER_MS) {
    return null;
  }
  const originY = samples[0]?.y ?? 0;
  const rebased = samples.map((sample) => ({
    x: sample.x,
    y: sample.y - originY,
    t: sample.t,
  }));
  rebased[0] = { x: 0, y: 0, t: 0 };
  let dense = fillLargeSteps(rebased);
  dense = ensurePointCount(dense, MIN_SLIDER_POINTS);
  if (dense.length < 10 || dense[dense.length - 1].t < MIN_SLIDER_MS) {
    return null;
  }
  if (Math.abs(dense[0].x) > 10 || Math.abs(dense[0].y) > 10) {
    return null;
  }
  return dense.map((sample, index) =>
    point(sample.x, sample.y, sample.t, index === 0 ? "DOWN" : index === dense.length - 1 ? "UP" : "MOVE"),
  );
}

export function keyboardSliderSamples(offset: number): SliderSample[] {
  const count = 16;
  const duration = 640;
  const samples: SliderSample[] = [];
  for (let index = 0; index < count; index += 1) {
    const progress = index / (count - 1);
    const eased = 1 - (1 - progress) ** 2;
    samples.push({
      x: offset * eased,
      y: index === 0 ? 0 : index % 2 === 0 ? 1 : -1,
      t: duration * progress,
    });
  }
  return samples;
}

function fillLargeSteps(samples: SliderSample[]): SliderSample[] {
  const filled: SliderSample[] = [samples[0]];
  for (let index = 1; index < samples.length; index += 1) {
    const previous = filled[filled.length - 1];
    const next = samples[index];
    const distance = Math.max(Math.abs(next.x - previous.x), Math.abs(next.y - previous.y));
    const steps = Math.ceil(distance / MAX_STEP);
    if (steps <= 1) {
      filled.push(next);
      continue;
    }
    for (let step = 1; step <= steps; step += 1) {
      const progress = step / steps;
      filled.push({
        x: previous.x + (next.x - previous.x) * progress,
        y: previous.y + (next.y - previous.y) * progress,
        t: previous.t + (next.t - previous.t) * progress,
      });
    }
  }
  return filled;
}

function ensurePointCount(samples: SliderSample[], minimum: number): SliderSample[] {
  let current = samples;
  while (current.length < minimum) {
    let longest = 1;
    let longestDistance = -1;
    for (let index = 1; index < current.length; index += 1) {
      const distance = Math.hypot(current[index].x - current[index - 1].x, current[index].y - current[index - 1].y);
      if (distance > longestDistance) {
        longestDistance = distance;
        longest = index;
      }
    }
    const left = current[longest - 1];
    const right = current[longest];
    const middle: SliderSample = {
      x: (left.x + right.x) / 2,
      y: (left.y + right.y) / 2,
      t: (left.t + right.t) / 2,
    };
    current = [...current.slice(0, longest), middle, ...current.slice(longest)];
  }
  return current;
}

export function buildImageCaptchaTrack(input: {
  bgImageWidth: number;
  bgImageHeight: number;
  templateImageWidth: number;
  templateImageHeight: number;
  startTimeMs: number;
  stopTimeMs: number;
  trackList: ImageCaptchaTrackPoint[];
}): ImageCaptchaTrack {
  return {
    bgImageWidth: Math.round(input.bgImageWidth),
    bgImageHeight: Math.round(input.bgImageHeight),
    templateImageWidth: Math.round(input.templateImageWidth),
    templateImageHeight: Math.round(input.templateImageHeight),
    startTime: toIsoTime(input.startTimeMs),
    stopTime: toIsoTime(input.stopTimeMs),
    trackList: input.trackList.map((item) => ({
      x: item.x,
      y: item.y,
      t: item.t,
      type: item.type,
    })),
  };
}

export function buildSliderTrackAtPercentage(
  size: {
    backgroundImageWidth: number;
    backgroundImageHeight: number;
    templateImageWidth?: number | null;
    templateImageHeight?: number | null;
  },
  percentage: number,
  now = Date.now(),
): ImageCaptchaTrack {
  const width = size.backgroundImageWidth;
  const height = size.backgroundImageHeight;
  const x = width * percentage;
  return buildImageCaptchaTrack({
    bgImageWidth: width,
    bgImageHeight: height,
    templateImageWidth: size.templateImageWidth ?? 47,
    templateImageHeight: size.templateImageHeight ?? height,
    startTimeMs: now - 800,
    stopTimeMs: now,
    trackList: [point(0, 0, 0, "DOWN"), point(x / 2, 1, 300, "MOVE"), point(x, 1, 800, "UP")],
  });
}

export function trackLooksComplete(track: ImageCaptchaTrack | null | undefined): boolean {
  if (!track) {
    return false;
  }
  if (!track.bgImageWidth || !track.bgImageHeight || !track.trackList?.length) {
    return false;
  }
  const types = new Set(track.trackList.map((item) => item.type));
  return types.has("DOWN") && types.has("UP") && Boolean(track.startTime) && Boolean(track.stopTime);
}
