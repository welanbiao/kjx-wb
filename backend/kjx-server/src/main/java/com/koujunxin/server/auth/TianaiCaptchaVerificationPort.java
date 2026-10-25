package com.koujunxin.server.auth;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import com.koujunxin.account.api.port.CaptchaVerificationPort;

import cloud.tianai.captcha.application.ImageCaptchaApplication;
import cloud.tianai.captcha.common.response.ApiResponse;
import cloud.tianai.captcha.validator.common.model.dto.ImageCaptchaTrack;

/**
 * 将 send-code 冻结字段 captchaId/captchaData 转为 {@link ImageCaptchaTrack} 后调用 matching()。
 * matching 走 CacheStore.getAndRemoveCache，校验后一次性消费。
 * 若前端已在松手时通过 {@code /captcha/check}，则改认短时 pass 凭证。
 * <p>不使用 Spring ObjectMapper.convertValue：带 JavaTimeModule 时 ISO 时间字符串无法转成
 * {@code java.util.Date}，会导致 matching 从未执行、挑战缓存也不被消费。
 */
public final class TianaiCaptchaVerificationPort implements CaptchaVerificationPort {

    private final ImageCaptchaApplication captcha;
    private final CaptchaPassStore passStore;

    public TianaiCaptchaVerificationPort(
            ImageCaptchaApplication captcha, CaptchaPassStore passStore, Object ignoredMapper) {
        this.captcha = captcha;
        this.passStore = passStore;
    }

    @Override
    public boolean verify(String captchaId, Map<String, Object> captchaData) {
        if (captchaId == null || captchaId.isBlank()) {
            return false;
        }
        // Empty body is valid when /captcha/check already marked a short-lived pass.
        if (passStore.consumePassed(captchaId)) {
            return true;
        }
        if (captchaData == null || captchaData.isEmpty()) {
            return false;
        }
        ImageCaptchaTrack track = toTrack(captchaData);
        if (track == null || track.getTrackList() == null || track.getTrackList().isEmpty()) {
            return false;
        }
        ApiResponse<?> matched = captcha.matching(captchaId, track);
        return matched != null && matched.isSuccess();
    }

    boolean matchAndMark(String captchaId, Map<String, Object> captchaData) {
        if (captchaId == null || captchaId.isBlank() || captchaData == null || captchaData.isEmpty()) {
            return false;
        }
        ImageCaptchaTrack track = toTrack(captchaData);
        if (track == null || track.getTrackList() == null || track.getTrackList().isEmpty()) {
            return false;
        }
        ApiResponse<?> matched = captcha.matching(captchaId, track);
        if (matched == null || !matched.isSuccess()) {
            return false;
        }
        passStore.markPassed(captchaId);
        return true;
    }

    static ImageCaptchaTrack toTrack(Map<String, Object> captchaData) {
        Integer bgW = asInt(first(captchaData, "bgImageWidth", "bg_image_width"));
        Integer bgH = asInt(first(captchaData, "bgImageHeight", "bg_image_height"));
        if (bgW == null || bgW < 1) {
            return null;
        }
        Object rawTracks = first(captchaData, "trackList", "track");
        if (!(rawTracks instanceof List<?> list) || list.isEmpty()) {
            return null;
        }
        List<ImageCaptchaTrack.Track> tracks = new ArrayList<>(list.size());
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> point)) {
                continue;
            }
            Float x = asFloat(point.get("x"));
            Float y = asFloat(point.get("y"));
            Float t = asFloat(point.get("t"));
            if (x == null || y == null || t == null) {
                continue;
            }
            ImageCaptchaTrack.Track row = new ImageCaptchaTrack.Track();
            row.setX(x);
            row.setY(y);
            row.setT(t);
            Object type = point.get("type");
            if (type != null) {
                row.setType(String.valueOf(type));
            }
            tracks.add(row);
        }
        if (tracks.isEmpty()) {
            return null;
        }
        ImageCaptchaTrack track = new ImageCaptchaTrack();
        track.setBgImageWidth(bgW);
        track.setBgImageHeight(bgH);
        track.setTemplateImageWidth(asInt(first(captchaData, "templateImageWidth", "template_image_width")));
        track.setTemplateImageHeight(asInt(first(captchaData, "templateImageHeight", "template_image_height")));
        track.setStartTime(coerceDate(first(captchaData, "startTime", "start_time", "startSlidingTime")));
        track.setStopTime(coerceDate(first(captchaData, "stopTime", "stop_time", "endSlidingTime")));
        track.setTrackList(tracks);
        return track;
    }

    private static Object first(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            if (map.containsKey(key)) {
                return map.get(key);
            }
        }
        return null;
    }

    private static Integer asInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return (int) Math.round(Double.parseDouble(text.trim()));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static Float asFloat(Object value) {
        if (value instanceof Number number) {
            return number.floatValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Float.parseFloat(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static Date coerceDate(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Date date) {
            return date;
        }
        if (value instanceof Number number) {
            return new Date(number.longValue());
        }
        if (value instanceof String text) {
            String trimmed = text.trim();
            if (trimmed.isEmpty()) {
                return null;
            }
            try {
                return Date.from(Instant.parse(trimmed));
            } catch (DateTimeParseException ignored) {
                // fall through
            }
            try {
                return new Date(Long.parseLong(trimmed));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
