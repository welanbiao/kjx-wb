"use client";

import {
  useCallback,
  useEffect,
  useId,
  useRef,
  useState,
  type CSSProperties,
  type KeyboardEvent,
  type PointerEvent,
} from "react";
import { Button } from "@/components/product/Button";
import { ErrorBanner } from "@/components/product/Banner";
import { readCaptchaTestDouble } from "@/lib/captcha/test-double";
import { buildImageCaptchaTrack, keyboardSliderSamples, normalizeSliderTrack, trackLooksComplete, type SliderSample } from "@/lib/captcha/track";
import type { CaptchaChallenge, FrozenCaptcha } from "@/lib/captcha/types";
import { clientApi } from "@/lib/client-api";
import { COPY } from "@/lib/copy";
import { isApiError } from "@/lib/errors";

type Props = {
  value: FrozenCaptcha | null;
  onChange: (value: FrozenCaptcha | null) => void;
  disabled?: boolean;
  refreshToken?: number;
};

type PointerSample = SliderSample;

const HANDLE_SIZE = 44;
const FAIL_MARK_MS = 420;
const FAIL_REWIND_MS = 480;

function sleep(ms: number) {
  return new Promise<void>((resolve) => {
    window.setTimeout(resolve, ms);
  });
}

function prefersReducedMotion() {
  return typeof window !== "undefined" && window.matchMedia("(prefers-reduced-motion: reduce)").matches;
}

function preloadImage(src: string | null | undefined) {
  if (!src || typeof window === "undefined") {
    return Promise.resolve();
  }
  return new Promise<void>((resolve) => {
    const image = new window.Image();
    image.onload = () => resolve();
    image.onerror = () => resolve();
    image.src = src;
  });
}

async function preloadCaptchaImages(challenge: CaptchaChallenge) {
  await Promise.all([preloadImage(challenge.backgroundImage), preloadImage(challenge.templateImage)]);
}

export function CaptchaField({ value, onChange, disabled, refreshToken = 0 }: Props) {
  const labelId = useId();
  const statusId = useId();
  const railRef = useRef<HTMLDivElement>(null);
  const stageRef = useRef<HTMLDivElement>(null);
  const handleRef = useRef<HTMLButtonElement>(null);
  const onChangeRef = useRef(onChange);
  const startMs = useRef(0);
  const samples = useRef<PointerSample[]>([]);
  const failSeq = useRef(0);
  const [challenge, setChallenge] = useState<CaptchaChallenge | null>(null);
  const [loading, setLoading] = useState(true);
  const [checking, setChecking] = useState(false);
  const [failing, setFailing] = useState(false);
  const [showFailMark, setShowFailMark] = useState(false);
  const [rewinding, setRewinding] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const [offset, setOffset] = useState(0);
  const [dragging, setDragging] = useState(false);
  const [maxOffset, setMaxOffset] = useState(220);
  const [handleWidth, setHandleWidth] = useState(HANDLE_SIZE);
  const [reloadKey, setReloadKey] = useState(0);
  const [gestureHint, setGestureHint] = useState<string | null>(null);
  const [hintIsFail, setHintIsFail] = useState(false);

  useEffect(() => {
    onChangeRef.current = onChange;
  }, [onChange]);

  const busy = loading || checking || failing;

  const loadChallenge = useCallback(async () => {
    setLoading(true);
    setChallenge(null);
    setShowFailMark(false);
    setRewinding(false);
    setFailing(false);
    try {
      const result = await clientApi.getCaptcha("SLIDER");
      startMs.current = 0;
      samples.current = [];
      setChallenge(result.data);
      setOffset(0);
      setDragging(false);
      setError(null);
      setLoading(false);
      setGestureHint(null);
      setHintIsFail(false);

      const testDouble = readCaptchaTestDouble();
      if (testDouble) {
        const track = await testDouble.complete(result.data);
        if (trackLooksComplete(track)) {
          const frozen = { captchaId: result.data.id, captchaData: track };
          try {
            await clientApi.checkCaptcha(frozen);
            onChangeRef.current(frozen);
            setHintIsFail(false);
            setGestureHint(null);
          } catch {
            onChangeRef.current(null);
            setReloadKey((value) => value + 1);
          }
          return;
        }
      }
      onChangeRef.current(null);
    } catch (err: unknown) {
      setChallenge(null);
      setError(err);
      setLoading(false);
      onChangeRef.current(null);
    }
  }, []);

  const failAndReset = useCallback(async (hint: string) => {
    const seq = ++failSeq.current;
    setFailing(true);
    setChecking(false);
    setDragging(false);
    setHintIsFail(true);
    setGestureHint(hint);
    setShowFailMark(true);
    onChangeRef.current(null);
    samples.current = [];
    startMs.current = 0;

    const nextPromise = clientApi.getCaptcha("SLIDER");
    const reduced = prefersReducedMotion();
    if (!reduced) {
      await sleep(FAIL_MARK_MS);
    }
    if (seq !== failSeq.current) {
      return;
    }

    setRewinding(true);
    setOffset(0);
    if (!reduced) {
      await sleep(FAIL_REWIND_MS);
    }
    if (seq !== failSeq.current) {
      return;
    }

    try {
      const payload = await nextPromise;
      if (seq !== failSeq.current) {
        return;
      }
      await preloadCaptchaImages(payload.data);
      if (seq !== failSeq.current) {
        return;
      }
      startMs.current = 0;
      samples.current = [];
      setChallenge(payload.data);
      setOffset(0);
      setError(null);
      setGestureHint(hint);
      setHintIsFail(true);
      onChangeRef.current(null);
    } catch (err: unknown) {
      if (seq !== failSeq.current) {
        return;
      }
      setChallenge(null);
      setError(err);
    } finally {
      if (seq === failSeq.current) {
        setShowFailMark(false);
        setRewinding(false);
        setFailing(false);
      }
    }
  }, []);

  const pieceRatio = challenge
    ? (challenge.templateImageWidth ?? 47) / Math.max(1, challenge.backgroundImageWidth)
    : 0;

  const buildFrozen = useCallback((endOffset: number, current: CaptchaChallenge): FrozenCaptcha | null => {
    if (!stageRef.current) {
      return null;
    }
    const stage = stageRef.current.getBoundingClientRect();
    const stageW = stage.width || current.backgroundImageWidth;
    const stageH = stage.height || current.backgroundImageHeight;
    // tianai: percentage = randomX / nativeBgWidth; userPct = (lastX - firstX) / bgImageWidth.
    // Keep track in native image pixels so visual align (offset/stageW) matches the cached answer.
    const scaleX = current.backgroundImageWidth / Math.max(1, stageW);
    const scaleY = current.backgroundImageHeight / Math.max(1, stageH);
    const raw = samples.current.length
      ? samples.current.map((sample) => ({
          x: sample.x * scaleX,
          y: sample.y * scaleY,
          t: sample.t,
        }))
      : [
          { x: 0, y: 0, t: 0 },
          { x: endOffset * scaleX, y: 1, t: 1 },
        ];
    const trackList = normalizeSliderTrack(raw);
    if (!trackList) {
      return null;
    }
    const stop = Date.now();
    const sampledDuration = trackList[trackList.length - 1]?.t ?? 0;
    const elapsed = startMs.current ? stop - startMs.current : 0;
    const start = stop - Math.max(sampledDuration, elapsed);
    return {
      captchaId: current.id,
      captchaData: buildImageCaptchaTrack({
        bgImageWidth: current.backgroundImageWidth,
        bgImageHeight: current.backgroundImageHeight,
        templateImageWidth: current.templateImageWidth ?? 47,
        templateImageHeight: current.templateImageHeight ?? current.backgroundImageHeight,
        startTimeMs: start,
        stopTimeMs: stop,
        trackList,
      }),
    };
  }, []);

  const submitCheck = useCallback(
    async (endOffset: number, current: CaptchaChallenge) => {
      const frozen = buildFrozen(endOffset, current);
      if (!frozen || !trackLooksComplete(frozen.captchaData)) {
        void failAndReset(COPY.captchaSlow);
        return;
      }
      setChecking(true);
      setHintIsFail(false);
      setGestureHint(COPY.captchaChecking);
      try {
        await clientApi.checkCaptcha(frozen);
        setGestureHint(null);
        setHintIsFail(false);
        setOffset(endOffset);
        onChangeRef.current(frozen);
      } catch (err) {
        const msg = isApiError(err) ? err.message : COPY.captchaFail;
        await failAndReset(msg || COPY.captchaFail);
      } finally {
        setChecking(false);
      }
    },
    [buildFrozen, failAndReset],
  );

  useEffect(() => {
    failSeq.current += 1;
    void loadChallenge();
    return () => {
      failSeq.current += 1;
    };
  }, [refreshToken, reloadKey, loadChallenge]);

  useEffect(() => {
    const rail = railRef.current;
    const stage = stageRef.current;
    const handle = handleRef.current;
    if ((!rail && !stage) || typeof ResizeObserver === "undefined") {
      return;
    }
    const update = () => {
      const railWidth = rail?.getBoundingClientRect().width ?? 220;
      const stageWidth = stage?.getBoundingClientRect().width ?? railWidth;
      const nextHandle = handle?.getBoundingClientRect().width ?? HANDLE_SIZE;
      const pieceWidth = stageWidth * pieceRatio;
      setHandleWidth(nextHandle);
      // Piece and handle share one offset (tianai 1:1). Cap by both travels.
      const byHandle = railWidth - nextHandle;
      const byPiece = stageWidth - pieceWidth;
      setMaxOffset(Math.max(8, Math.min(byHandle, byPiece)));
    };
    const observer = new ResizeObserver(update);
    if (rail) {
      observer.observe(rail);
    }
    if (stage) {
      observer.observe(stage);
    }
    if (handle) {
      observer.observe(handle);
    }
    update();
    return () => observer.disconnect();
  }, [challenge, pieceRatio]);

  function appendSample(clientX: number, clientY: number) {
    const rail = railRef.current?.getBoundingClientRect();
    if (!rail) {
      return 0;
    }
    const next = Math.min(maxOffset, Math.max(0, clientX - rail.left - handleWidth / 2));
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
    if (disabled || !challenge || busy || value) {
      return;
    }
    event.currentTarget.setPointerCapture(event.pointerId);
    setDragging(true);
    onChangeRef.current(null);
    samples.current = [];
    startMs.current = Date.now();
    setGestureHint(null);
    setHintIsFail(false);
    setShowFailMark(false);
    appendSample(event.clientX, event.clientY);
  }

  function onPointerMove(event: PointerEvent<HTMLButtonElement>) {
    if (!dragging) {
      return;
    }
    appendSample(event.clientX, event.clientY);
  }

  function onPointerUp(event: PointerEvent<HTMLButtonElement>) {
    if (!dragging || !challenge || checking || failing) {
      return;
    }
    const next = appendSample(event.clientX, event.clientY);
    setDragging(false);
    void submitCheck(next, challenge);
  }

  function onKeyDown(event: KeyboardEvent<HTMLButtonElement>) {
    if (disabled || !challenge || busy || value) {
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
      void submitCheck(offset, challenge);
      return;
    } else {
      return;
    }
    samples.current = keyboardSliderSamples(next);
    setOffset(next);
  }

  const solved = Boolean(value);
  const percent = maxOffset > 0 ? Math.round((offset / maxOffset) * 100) : 0;
  const stageStyle = challenge
    ? ({
        aspectRatio: `${challenge.backgroundImageWidth} / ${Math.max(1, challenge.backgroundImageHeight)}`,
      } as CSSProperties)
    : undefined;
  const pieceStyle = challenge
    ? ({
        width: `${pieceRatio * 100}%`,
        height: "100%",
        transform: `translateX(${offset}px)`,
      } as CSSProperties)
    : undefined;
  const stageClass = [
    "kjx-captcha-stage",
    showFailMark ? "is-fail" : "",
    rewinding ? "is-rewinding" : "",
  ]
    .filter(Boolean)
    .join(" ");

  return (
    <div className="kjx-field kjx-captcha">
      {solved ? null : <span id={labelId}>{COPY.captcha}</span>}
      {loading && !challenge ? <p className="kjx-hint">{COPY.captchaLoading}</p> : null}
      {error && !challenge ? <ErrorBanner error={error} onRetry={() => setReloadKey((value) => value + 1)} /> : null}
      {challenge ? (
        <div className={solved ? "kjx-captcha-board is-solved" : "kjx-captcha-board"}>
          <div ref={stageRef} className={stageClass} style={stageStyle}>
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
                style={pieceStyle}
                draggable={false}
              />
            ) : null}
            {showFailMark ? (
              <span className="kjx-captcha-fail-layer" aria-hidden="true">
                <span className="kjx-captcha-mark is-fail">×</span>
              </span>
            ) : null}
          </div>
          {solved ? (
            <span className="kjx-captcha-mark is-ok" aria-hidden="true">
              ✓
            </span>
          ) : (
            <div ref={railRef} className="kjx-captcha-rail">
              <button
                ref={handleRef}
                type="button"
                className={rewinding ? "kjx-captcha-handle is-rewinding" : "kjx-captcha-handle"}
                role="slider"
                aria-label={COPY.captchaHandle}
                aria-labelledby={labelId}
                aria-describedby={statusId}
                aria-valuemin={0}
                aria-valuemax={maxOffset}
                aria-valuenow={Math.round(offset)}
                aria-valuetext={`${COPY.captchaValue} ${percent}%`}
                aria-busy={busy || undefined}
                disabled={disabled || busy}
                onPointerDown={onPointerDown}
                onPointerMove={onPointerMove}
                onPointerUp={onPointerUp}
                onPointerCancel={onPointerUp}
                onKeyDown={onKeyDown}
                style={{ transform: `translateX(${offset}px)` }}
              >
                <span aria-hidden="true">››</span>
              </button>
            </div>
          )}
          <p
            id={statusId}
            className={hintIsFail ? "kjx-hint kjx-captcha-fail" : "kjx-hint"}
            aria-live="polite"
          >
            {gestureHint ?? (solved ? COPY.captchaDone : COPY.captchaHint)}
          </p>
          {solved ? null : (
            <div className="kjx-actions">
              <Button
                variant="ghost"
                onClick={() => {
                  failSeq.current += 1;
                  setFailing(false);
                  setShowFailMark(false);
                  setRewinding(false);
                  setGestureHint(null);
                  setHintIsFail(false);
                  setReloadKey((value) => value + 1);
                }}
                disabled={disabled || busy}
              >
                {COPY.captchaRefresh}
              </Button>
            </div>
          )}
        </div>
      ) : null}
    </div>
  );
}
