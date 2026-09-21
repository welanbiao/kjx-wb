package com.koujunxin.server.auth;

/**
 * C 端图形挑战。{@code id} 即 send-code 的 captchaId；其余字段交给 tianai-captcha-web-sdk 渲染。
 */
public record CaptchaChallenge(
        String id,
        String type,
        String backgroundImage,
        String templateImage,
        String backgroundImageTag,
        String templateImageTag,
        Integer backgroundImageWidth,
        Integer backgroundImageHeight,
        Integer templateImageWidth,
        Integer templateImageHeight,
        Object data
) {
}
