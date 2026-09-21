import type { CaptchaChallenge, ImageCaptchaTrack } from "@/lib/captcha/types";
import { isMockToolsEnabled } from "@/lib/flags";

export type CaptchaTestDouble = {
  complete: (challenge: CaptchaChallenge) => ImageCaptchaTrack | Promise<ImageCaptchaTrack>;
};

declare global {
  interface Window {
    __KJX_CAPTCHA_TEST_DOUBLE__?: CaptchaTestDouble;
  }
}

export function readCaptchaTestDouble(): CaptchaTestDouble | null {
  if (!isMockToolsEnabled()) {
    return null;
  }
  if (typeof window === "undefined") {
    return null;
  }
  return window.__KJX_CAPTCHA_TEST_DOUBLE__ ?? null;
}
