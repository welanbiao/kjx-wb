import { describe, expect, it } from "vitest";
import { buildImageCaptchaTrack, keyboardSliderSamples, normalizeSliderTrack, point, trackLooksComplete } from "@/lib/captcha/track";
import { challengeLeaksSecret, createMockCaptcha, MOCK_SLIDER_PERCENTAGE, verifyMockCaptchaTrack } from "@/lib/mock/captcha";
import { displayErrorMessage, ERROR_MESSAGES } from "@/lib/errors";

describe("mock slider captcha", () => {
  it("returns a SLIDER challenge without leaking the secret percentage", () => {
    const { challenge, record } = createMockCaptcha();
    expect(challenge.type).toBe("SLIDER");
    expect(challenge.id).toBe(record.id);
    expect(challenge.backgroundImage.startsWith("data:image/svg+xml")).toBe(true);
    expect(challengeLeaksSecret(challenge)).toBe(false);
    expect(JSON.stringify(challenge)).not.toContain(String(MOCK_SLIDER_PERCENTAGE));
  });

  it("accepts a complete ImageCaptchaTrack near the secret offset and consumes nothing here", () => {
    const { challenge, record } = createMockCaptcha();
    const x = challenge.backgroundImageWidth * MOCK_SLIDER_PERCENTAGE;
    const track = buildImageCaptchaTrack({
      bgImageWidth: challenge.backgroundImageWidth,
      bgImageHeight: challenge.backgroundImageHeight,
      templateImageWidth: challenge.templateImageWidth ?? 47,
      templateImageHeight: challenge.templateImageHeight ?? challenge.backgroundImageHeight,
      startTimeMs: Date.now() - 500,
      stopTimeMs: Date.now(),
      trackList: [point(0, 0, 0, "DOWN"), point(x / 2, 1, 200, "MOVE"), point(x, 1, 500, "UP")],
    });
    expect(trackLooksComplete(track)).toBe(true);
    expect(verifyMockCaptchaTrack(record, track)).toBe(true);
    expect(verifyMockCaptchaTrack(record, { ...track, trackList: [point(1, 0, 0, "DOWN"), point(2, 0, 10, "UP")] })).toBe(false);
  });

  it("rejects expired or empty tracks", () => {
    const { record } = createMockCaptcha();
    expect(verifyMockCaptchaTrack({ ...record, expireAt: Date.now() - 1 }, { trackList: [] })).toBe(false);
    expect(verifyMockCaptchaTrack(undefined, {})).toBe(false);
  });
});

describe("tianai slider track shape", () => {
  it("rejects a flick shorter than 300ms", () => {
    expect(normalizeSliderTrack([
      { x: 0, y: 0, t: 0 },
      { x: 120, y: 1, t: 180 },
    ])).toBeNull();
  });

  it("keeps the release position while filling the basic track rules", () => {
    const track = normalizeSliderTrack([
      { x: 12, y: 18, t: 0 },
      { x: 280, y: 20, t: 420 },
    ]);
    expect(track).not.toBeNull();
    expect(track!.length).toBeGreaterThanOrEqual(12);
    expect(track![0]).toMatchObject({ x: 0, y: 0, t: 0, type: "DOWN" });
    expect(track![track!.length - 1]).toMatchObject({ x: 280, type: "UP" });
    expect(track![track!.length - 1].t).toBeGreaterThanOrEqual(300);
    for (let index = 1; index < track!.length; index += 1) {
      expect(track![index].x - track![index - 1].x).toBeLessThanOrEqual(50);
      expect(Math.abs(track![index].y - track![index - 1].y)).toBeLessThanOrEqual(50);
    }
  });

  it("turns keyboard steps into a complete track ending at the chosen offset", () => {
    const track = normalizeSliderTrack(keyboardSliderSamples(117.6));
    expect(track).not.toBeNull();
    expect(track![track!.length - 1].x).toBeCloseTo(117.6);
    expect(track!.some((item) => item.type === "MOVE")).toBe(true);
  });
});

describe("friendly error catalog", () => {
  it("keeps 1001/1002/1003/1004/1014/1090 user-facing", () => {
    expect(displayErrorMessage(1001, "")).toBe(ERROR_MESSAGES[1001]);
    expect(displayErrorMessage(1002, "")).toMatch(/已被使用/);
    expect(displayErrorMessage(1003, "")).toMatch(/只能绑定一个/);
    expect(displayErrorMessage(1004, "")).toMatch(/30 分钟/);
    expect(displayErrorMessage(1014, "")).toMatch(/注销/);
    expect(displayErrorMessage(1090, "")).toMatch(/重复请求/);
  });
});
