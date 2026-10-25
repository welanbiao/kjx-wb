import type { CaptchaChallenge, ImageCaptchaTrack } from "@/lib/captcha/types";

/** Mock-only secret. Never serialized on GET /app/auth/captcha. */
export const MOCK_SLIDER_PERCENTAGE = 0.42;
export const MOCK_CAPTCHA_BG_WIDTH = 280;
export const MOCK_CAPTCHA_BG_HEIGHT = 140;
export const MOCK_CAPTCHA_PIECE_WIDTH = 47;
export const MOCK_CAPTCHA_PIECE_HEIGHT = 140;
export const MOCK_CAPTCHA_TTL_MS = 120_000;
export const MOCK_SLIDER_TOLERANCE = 0.03;

export type MockCaptchaRecord = {
  id: string;
  type: "SLIDER";
  percentage: number;
  bgWidth: number;
  bgHeight: number;
  pieceWidth: number;
  pieceHeight: number;
  expireAt: number;
  /** 松手校验已通过，供 send-code / 登录二次消费 */
  passed?: boolean;
};

function svgDataUri(svg: string): string {
  return `data:image/svg+xml;utf8,${encodeURIComponent(svg)}`;
}

function backgroundSvg(gapX: number): string {
  return svgDataUri(`<svg xmlns="http://www.w3.org/2000/svg" width="${MOCK_CAPTCHA_BG_WIDTH}" height="${MOCK_CAPTCHA_BG_HEIGHT}" viewBox="0 0 ${MOCK_CAPTCHA_BG_WIDTH} ${MOCK_CAPTCHA_BG_HEIGHT}" role="img" aria-hidden="true">
  <defs>
    <linearGradient id="sky" x1="0" y1="0" x2="1" y2="1">
      <stop offset="0" stop-color="#1387c0"/>
      <stop offset="1" stop-color="#061923"/>
    </linearGradient>
  </defs>
  <rect width="100%" height="100%" fill="url(#sky)"/>
  <rect x="0" y="118" width="100%" height="22" fill="#faedd1" opacity="0.88"/>
  <rect x="${gapX}" y="18" width="${MOCK_CAPTCHA_PIECE_WIDTH}" height="100" rx="8" fill="#061923" opacity="0.55"/>
  <text x="14" y="36" fill="#faedd1" font-size="13" font-family="Georgia, serif">分幕通行证</text>
</svg>`);
}

function pieceSvg(): string {
  return svgDataUri(`<svg xmlns="http://www.w3.org/2000/svg" width="${MOCK_CAPTCHA_PIECE_WIDTH}" height="${MOCK_CAPTCHA_PIECE_HEIGHT}" viewBox="0 0 ${MOCK_CAPTCHA_PIECE_WIDTH} ${MOCK_CAPTCHA_PIECE_HEIGHT}" role="img" aria-hidden="true">
  <rect x="0" y="18" width="${MOCK_CAPTCHA_PIECE_WIDTH}" height="100" rx="8" fill="#f4520d"/>
  <rect x="8" y="40" width="31" height="8" rx="2" fill="#faedd1"/>
</svg>`);
}

export function createMockCaptcha(): { record: MockCaptchaRecord; challenge: CaptchaChallenge } {
  const id = `mock-captcha-${crypto.randomUUID()}`;
  const gapX = MOCK_SLIDER_PERCENTAGE * MOCK_CAPTCHA_BG_WIDTH;
  const record: MockCaptchaRecord = {
    id,
    type: "SLIDER",
    percentage: MOCK_SLIDER_PERCENTAGE,
    bgWidth: MOCK_CAPTCHA_BG_WIDTH,
    bgHeight: MOCK_CAPTCHA_BG_HEIGHT,
    pieceWidth: MOCK_CAPTCHA_PIECE_WIDTH,
    pieceHeight: MOCK_CAPTCHA_PIECE_HEIGHT,
    expireAt: Date.now() + MOCK_CAPTCHA_TTL_MS,
  };
  const challenge: CaptchaChallenge = {
    id,
    type: "SLIDER",
    backgroundImage: backgroundSvg(gapX),
    templateImage: pieceSvg(),
    backgroundImageTag: null,
    templateImageTag: null,
    backgroundImageWidth: MOCK_CAPTCHA_BG_WIDTH,
    backgroundImageHeight: MOCK_CAPTCHA_BG_HEIGHT,
    templateImageWidth: MOCK_CAPTCHA_PIECE_WIDTH,
    templateImageHeight: MOCK_CAPTCHA_PIECE_HEIGHT,
    data: null,
  };
  return { record, challenge };
}

function asTime(value: unknown): number | null {
  if (typeof value === "number" && Number.isFinite(value)) {
    return value;
  }
  if (typeof value === "string" && value.trim()) {
    const parsed = Date.parse(value);
    return Number.isNaN(parsed) ? null : parsed;
  }
  return null;
}

function lastX(data: Record<string, unknown>): number | null {
  const list = data.trackList;
  if (!Array.isArray(list) || list.length === 0) {
    return null;
  }
  const last = list[list.length - 1] as { x?: unknown; type?: unknown };
  if (typeof last.x !== "number" || !Number.isFinite(last.x)) {
    return null;
  }
  return last.x;
}

export function verifyMockCaptchaTrack(
  record: MockCaptchaRecord | undefined,
  captchaData: unknown,
  now = Date.now(),
): boolean {
  if (!record || record.expireAt < now) {
    return false;
  }
  if (!captchaData || typeof captchaData !== "object" || Array.isArray(captchaData)) {
    return false;
  }
  const data = captchaData as Record<string, unknown> & Partial<ImageCaptchaTrack>;
  const tracks = data.trackList;
  if (!Array.isArray(tracks) || tracks.length < 2) {
    return false;
  }
  const types = new Set(tracks.map((item) => (item as { type?: string }).type));
  if (!types.has("DOWN") || !types.has("UP")) {
    return false;
  }
  const start = asTime(data.startTime);
  const stop = asTime(data.stopTime);
  if (start == null || stop == null || stop < start) {
    return false;
  }
  const width = typeof data.bgImageWidth === "number" ? data.bgImageWidth : record.bgWidth;
  if (!width) {
    return false;
  }
  const x = lastX(data);
  if (x == null) {
    return false;
  }
  const ratio = x / width;
  return Math.abs(ratio - record.percentage) <= MOCK_SLIDER_TOLERANCE;
}

export function challengeLeaksSecret(challenge: CaptchaChallenge): boolean {
  const blob = JSON.stringify(challenge).toLowerCase();
  return /percentage|secret|answer|targetx|gapx/.test(blob);
}
