/** OpenAPI CaptchaChallenge + tianai ImageCaptchaTrack. Do not add unpublished fields. */

export type CaptchaType = "SLIDER" | "ROTATE" | "CONCAT" | "WORD_IMAGE_CLICK";

export type CaptchaChallenge = {
  id: string;
  type: string;
  backgroundImage: string;
  templateImage: string | null;
  backgroundImageTag: string | null;
  templateImageTag: string | null;
  backgroundImageWidth: number;
  backgroundImageHeight: number;
  templateImageWidth: number | null;
  templateImageHeight: number | null;
  data: unknown;
};

export type TrackPointType = "DOWN" | "MOVE" | "UP";

export type ImageCaptchaTrackPoint = {
  x: number;
  y: number;
  t: number;
  type: TrackPointType;
};

export type ImageCaptchaTrack = {
  bgImageWidth: number;
  bgImageHeight: number;
  templateImageWidth: number;
  templateImageHeight: number;
  startTime: string;
  stopTime: string;
  trackList: ImageCaptchaTrackPoint[];
};

export type FrozenCaptcha = {
  captchaId: string;
  captchaData: ImageCaptchaTrack;
};
