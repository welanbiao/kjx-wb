package com.koujunxin.server.auth;

import cloud.tianai.captcha.application.ImageCaptchaApplication;
import cloud.tianai.captcha.application.TACBuilder;
import cloud.tianai.captcha.cache.CacheStore;
import cloud.tianai.captcha.common.constant.CaptchaTypeConstant;
import cloud.tianai.captcha.resource.common.model.dto.Resource;

final class TianaiCaptchaSupport {

    private static final String DEFAULT_BG = "META-INF/cut-image/resource/1.jpg";
    private static final String[] SLIDER_BACKGROUNDS = {
            DEFAULT_BG,
            "captcha/bg-sky.jpg",
            "captcha/bg-cream.jpg",
            "captcha/bg-dusk.jpg",
            "captcha/bg-ink.jpg"
    };

    private TianaiCaptchaSupport() {
    }

    static ImageCaptchaApplication create(CacheStore cacheStore, KjxCaptchaProperties properties) {
        String prefix = properties.getPrefix() == null || properties.getPrefix().isBlank()
                ? "captcha"
                : properties.getPrefix();
        long expireMs = properties.getExpireMs() <= 0 ? 120_000L : properties.getExpireMs();
        TACBuilder builder = TACBuilder.builder().addDefaultTemplate();
        for (String background : SLIDER_BACKGROUNDS) {
            builder.addResource(CaptchaTypeConstant.SLIDER, new Resource("classpath", background));
        }
        builder.addResource(CaptchaTypeConstant.ROTATE, new Resource("classpath", DEFAULT_BG))
                .addResource(CaptchaTypeConstant.CONCAT, new Resource("classpath", DEFAULT_BG))
                .addResource(CaptchaTypeConstant.WORD_IMAGE_CLICK, new Resource("classpath", DEFAULT_BG));
        return builder
                .setCacheStore(cacheStore)
                .prefix(prefix)
                .expire("default", expireMs)
                .build();
    }
}
