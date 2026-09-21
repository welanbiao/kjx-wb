import { MOCK_SLIDER_PERCENTAGE } from "../lib/mock/captcha";

export const captchaTestDoubleInit = `window.__KJX_CAPTCHA_TEST_DOUBLE__ = {
  complete(challenge) {
    const width = challenge.backgroundImageWidth;
    const height = challenge.backgroundImageHeight;
    const x = width * ${MOCK_SLIDER_PERCENTAGE};
    const start = Date.now() - 800;
    return {
      bgImageWidth: width,
      bgImageHeight: height,
      templateImageWidth: challenge.templateImageWidth ?? 47,
      templateImageHeight: challenge.templateImageHeight ?? height,
      startTime: new Date(start).toISOString(),
      stopTime: new Date(start + 800).toISOString(),
      trackList: [
        { x: 0, y: 0, t: 0, type: "DOWN" },
        { x: x / 2, y: 1, t: 300, type: "MOVE" },
        { x: x, y: 1, t: 800, type: "UP" }
      ]
    };
  }
};`;
