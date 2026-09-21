"use client";

import { useCallback, useEffect, useId, useRef, useState, type KeyboardEvent, type PointerEvent } from "react";
import { Button } from "@/components/product/Button";
import { ErrorBanner } from "@/components/product/Banner";
import { readCaptchaTestDouble } from "@/lib/captcha/test-double";
import { buildImageCaptchaTrack, keyboardSliderSamples, normalizeSliderTrack, trackLooksComplete, type SliderSample } from "@/lib/captcha/track";
import type { CaptchaChallenge, FrozenCaptcha } from "@/lib/captcha/types";
import { clientApi } from "@/lib/client-api";
import { COPY } from "@/lib/copy";

type Props = {
  value: FrozenCaptcha | null;
  onChange: (value: FrozenCaptcha | null) => void;
  disabled?: boolean;
  refreshToken?: number;
};

type PointerSample = SliderSample;

export function CaptchaField({ value, onChange, disabled, refreshToken = 0 }: Props) {
  const labelId = useId();
  const statusId = useId();
  const railRef = useRef<HTMLDivElement>(null);
  const stageRef = useRef<HTMLDivElement>(null);
  const onChangeRef = useRef(onChange);
  const startMs = useRef(0);
  const samples = useRef<PointerSample[]>([]);
  const [challenge, setChallenge] = useState<CaptchaChallenge | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<unknown>(null);
  const [offset, setOffset] = useState(0);
  const [dragging, setDragging] = useState(false);
  const [maxOffset, setMaxOffset] = useState(220);
  const [reloadKey, setReloadKey] = useState(0);
  const [gestureHint, setGestureHint] = useState<string | null>(null);

  useEffect(() => {
    onChangeRef.current = onChange;
  }, [onChange]);

  const freezeFromSamples = useCallback((endOffset: number, current: CaptchaChallenge) => {
    if (!stageRef.current) {
      return;
    }
    const stage = stageRef.current.getBoundingClientRect();
    const raw = samples.current.length
      ? samples.current
      : [
          { x: 0, y: 0, t: 0 },
          { x: endOffset, y: 1, t: 1 },
        ];
    const trackList = normalizeSliderTrack(raw);
    if (!trackList) {
      setGestureHint(COPY.captchaSlow);
      onChangeRef.current(null);
      return;
    }
    setGestureHint(null);
    const stop = Date.now();
    const sampledDuration = trackList[trackList.length - 1]?.t ?? 0;
    const elapsed = startMs.current ? stop - startMs.current : 0;
    const start = stop - Math.max(sampledDuration, elapsed);
    const scaleX = stage.width / (current.backgroundImageWidth || stage.width || 1);
    const scaleY = stage.height / (current.backgroundImageHeight || stage.height || 1);
    onChangeRef.current({
      captchaId: current.id,
      captchaData: buildImageCaptchaTrack({
        bgImageWidth: stage.width || current.backgroundImageWidth,
        bgImageHeight: stage.height || current.backgroundImageHeight,
        templateImageWidth: (current.templateImageWidth ?? 47) * scaleX,
        templateImageHeight: (current.templateImageHeight ?? current.backgroundImageHeight) * scaleY,
        startTimeMs: start,
        stopTimeMs: stop,
        trackList,
      }),
    });
  }, []);

  useEffect(() => {
    let cancelled = false;
    void clientApi
      .getCaptcha("SLIDER")
      .then(async (result) => {
        if (cancelled) {
          return;
        }
        startMs.current = 0;
        samples.current = [];
        setChallenge(result.data);
        setOffset(0);
        setDragging(false);
        setError(null);
        setGestureHint(null);
        setLoading(false);
        const testDouble = readCaptchaTestDouble();
        if (testDouble) {
          const track = await testDouble.complete(result.data);
          if (!cancelled && trackLooksComplete(track)) {
            onChangeRef.current({ captchaId: result.data.id, captchaData: track });
            return;
          }
        }
        onChangeRef.current(null);
      })
      .catch((err: unknown) => {
        if (cancelled) {
          return;
        }
        setChallenge(null);
        setError(err);
        setLoading(false);
        onChangeRef.current(null);
      });
    return () => {
      cancelled = true;
    };
  }, [refreshToken, reloadKey]);

  useEffect(() => {
    const rail = railRef.current;
    if (!rail || typeof ResizeObserver === "undefined") {
      return;
    }
    const observer = new ResizeObserver((entries) => {
      const width = entries[0]?.contentRect.width ?? 220;
      setMaxOffset(Math.max(8, width - 44));
    });
    observer.observe(rail);
    return () => observer.disconnect();
  }, [challenge]);

  function appendSample(clientX: number, clientY: number) {
    const rail = railRef.current?.getBoundingClientRect();
    if (!rail) {
      return 0;
    }
    const next = Math.min(maxOffset, Math.max(0, clientX - rail.left - 22));
    const now = Date.now();
    if (!startMs.current) {
      startMs.current = now;
    }
    samples.current.push({
      x: next,
      y: clientY - rail.top,
      t: now - startMs.current,
    });
    setOffset(next);
    return next;
  }

  function onPointerDown(event: PointerEvent<HTMLButtonElement>) {
    if (disabled || !challenge) {
      return;
    }
    event.currentTarget.setPointerCapture(event.pointerId);
    setDragging(true);
    onChangeRef.current(null);
    samples.current = [];
    startMs.current = Date.now();
    appendSample(event.clientX, event.clientY);
  }

  function onPointerMove(event: PointerEvent<HTMLButtonElement>) {
    if (!dragging) {
      return;
    }
    appendSample(event.clientX, event.clientY);
  }

  function onPointerUp(event: PointerEvent<HTMLButtonElement>) {
    if (!dragging || !challenge) {
      return;
    }
    const next = appendSample(event.clientX, event.clientY);
    setDragging(false);
    freezeFromSamples(next, challenge);
  }

  function onKeyDown(event: KeyboardEvent<HTMLButtonElement>) {
    if (disabled || !challenge) {
      return;
    }
    const step = 8;
    let next = offset;
    if (event.key === "ArrowRight") {
      event.preventDefault();
      next = Math.min(maxOffset, offset + step);
    } else if (event.key === "ArrowLeft") {
      event.preventDefault();
      next = Math.max(0, offset - step);
    } else if (event.key === "Home") {
      event.preventDefault();
      next = 0;
    } else if (event.key === "End") {
      event.preventDefault();
      next = maxOffset;
    } else if (event.key === "Enter" || event.key === " ") {
      event.preventDefault();
      samples.current = keyboardSliderSamples(offset);
      freezeFromSamples(offset, challenge);
      return;
    } else {
      return;
    }
    samples.current = keyboardSliderSamples(next);
    setOffset(next);
    freezeFromSamples(next, challenge);
  }

  const solved = Boolean(value);
  const percent = maxOffset > 0 ? Math.round((offset / maxOffset) * 100) : 0;

  return (
    <div className="kjx-field kjx-captcha">
      <span id={labelId}>{COPY.captcha}</span>
      {loading ? <p className="kjx-hint">{COPY.captchaLoading}</p> : null}
      {error ? <ErrorBanner error={error} onRetry={() => setReloadKey((value) => value + 1)} /> : null}
      {challenge ? (
        <div className="kjx-captcha-board">
          <div ref={stageRef} className="kjx-captcha-stage">
            {/* data URI from BFF; next/image is not used for captcha payloads */}
            {/* eslint-disable-next-line @next/next/no-img-element */}
            <img
              className="kjx-captcha-bg"
              src={challenge.backgroundImage}
              alt=""
              width={challenge.backgroundImageWidth}
              height={challenge.backgroundImageHeight}
              draggable={false}
            />
            {challenge.templateImage ? (
              // eslint-disable-next-line @next/next/no-img-element
              <img
                className="kjx-captcha-piece"
                src={challenge.templateImage}
                alt=""
                width={challenge.templateImageWidth ?? 47}
                height={challenge.templateImageHeight ?? challenge.backgroundImageHeight}
                style={{ transform: `translateX(${offset}px)` }}
                draggable={false}
              />
            ) : null}
          </div>
          <div ref={railRef} className="kjx-captcha-rail">
            <button
              type="button"
              className="kjx-captcha-handle"
              role="slider"
              aria-labelledby={labelId}
              aria-describedby={statusId}
              aria-valuemin={0}
              aria-valuemax={maxOffset}
              aria-valuenow={Math.round(offset)}
              aria-valuetext={`${COPY.captchaValue} ${percent}%`}
              disabled={disabled || loading}
              onPointerDown={onPointerDown}
              onPointerMove={onPointerMove}
              onPointerUp={onPointerUp}
              onPointerCancel={onPointerUp}
              onKeyDown={onKeyDown}
              style={{ transform: `translateX(${offset}px)` }}
            >
              {COPY.captchaHandle}
            </button>
          </div>
          <p id={statusId} className="kjx-hint" aria-live="polite">
            {gestureHint ?? (solved ? COPY.captchaDone : COPY.captchaHint)}
          </p>
          <div className="kjx-actions">
            <Button variant="ghost" onClick={() => setReloadKey((value) => value + 1)} disabled={disabled || loading}>
              {COPY.captchaRefresh}
            </Button>
          </div>
        </div>
      ) : null}
    </div>
  );
}
