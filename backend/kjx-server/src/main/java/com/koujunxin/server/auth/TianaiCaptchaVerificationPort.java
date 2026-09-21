package com.koujunxin.server.auth;

import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.koujunxin.account.api.port.CaptchaVerificationPort;

import cloud.tianai.captcha.application.ImageCaptchaApplication;
import cloud.tianai.captcha.common.response.ApiResponse;
import cloud.tianai.captcha.validator.common.model.dto.ImageCaptchaTrack;

/**
 * 将 send-code 冻结字段 captchaId/captchaData 转为 {@link ImageCaptchaTrack} 后调用 matching()。
 * matching 走 CacheStore.getAndRemoveCache，校验后一次性消费。
 */
public final class TianaiCaptchaVerificationPort implements CaptchaVerificationPort {

    private final ImageCaptchaApplication captcha;
    private final ObjectMapper mapper;

    public TianaiCaptchaVerificationPort(ImageCaptchaApplication captcha, ObjectMapper mapper) {
        this.captcha = captcha;
        this.mapper = mapper.copy()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);
    }

    @Override
    public boolean verify(String captchaId, Map<String, Object> captchaData) {
        if (captchaId == null || captchaId.isBlank() || captchaData == null || captchaData.isEmpty()) {
            return false;
        }
        ImageCaptchaTrack track;
        try {
            track = mapper.convertValue(normalize(captchaData), ImageCaptchaTrack.class);
        } catch (IllegalArgumentException ex) {
            return false;
        }
        if (track == null || track.getTrackList() == null || track.getTrackList().isEmpty()) {
            return false;
        }
        ApiResponse<?> matched = captcha.matching(captchaId, track);
        return matched != null && matched.isSuccess();
    }

    private static Map<String, Object> normalize(Map<String, Object> captchaData) {
        Map<String, Object> copy = new LinkedHashMap<>(captchaData);
        if (!copy.containsKey("trackList") && copy.containsKey("track")) {
            copy.put("trackList", copy.get("track"));
        }
        return copy;
    }
}
