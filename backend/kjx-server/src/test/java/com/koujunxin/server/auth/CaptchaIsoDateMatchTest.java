package com.koujunxin.server.auth;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import cloud.tianai.captcha.application.ImageCaptchaApplication;
import cloud.tianai.captcha.application.vo.CaptchaResponse;
import cloud.tianai.captcha.application.vo.ImageCaptchaVO;
import cloud.tianai.captcha.cache.CacheStore;
import cloud.tianai.captcha.common.AnyMap;
import cloud.tianai.captcha.common.constant.CaptchaTypeConstant;
import cloud.tianai.captcha.validator.common.constant.TrackTypeConstant;
import cloud.tianai.captcha.validator.common.model.dto.ImageCaptchaTrack;
import cloud.tianai.captcha.validator.impl.SimpleImageCaptchaValidator;

@Testcontainers(disabledWithoutDocker = true)
class CaptchaIsoDateMatchTest {

    @Container
    @SuppressWarnings("resource")
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:8-alpine"))
            .withExposedPorts(6379)
            .waitingFor(Wait.forLogMessage(".*Ready to accept connections.*", 1))
            .withStartupTimeout(Duration.ofMinutes(2));

    /** Mirrors Spring Boot's ObjectMapper (JavaTimeModule breaks raw ISO→Date convertValue). */
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private ImageCaptchaApplication app;
    private TianaiCaptchaVerificationPort port;
    private KjxCaptchaProperties properties;

    @BeforeEach
    void setUp() {
        Config config = new Config();
        config.useSingleServer().setAddress("redis://" + REDIS.getHost() + ":" + REDIS.getMappedPort(6379));
        RedissonClient redisson = Redisson.create(config);
        redisson.getKeys().flushdb();
        properties = new KjxCaptchaProperties();
        CacheStore store = new RedissonCaptchaCacheStore(redisson, mapper);
        app = TianaiCaptchaSupport.create(store, properties);
        port = new TianaiCaptchaVerificationPort(app, new CaptchaPassStore(redisson), mapper);
    }

    @Test
    void isoDateStringTrackMatchesLikeFrontend() {
        CaptchaResponse<ImageCaptchaVO> generated = app.generateCaptcha(CaptchaTypeConstant.SLIDER);
        ImageCaptchaVO vo = generated.getCaptcha();
        AnyMap cached = app.getCacheStore().getCache(properties.getPrefix() + ":" + generated.getId());
        assertNotNull(cached);
        Float percentage = cached.getFloat(SimpleImageCaptchaValidator.PERCENTAGE_KEY);
        assertNotNull(percentage);
        float targetX = percentage * vo.getBackgroundImageWidth();

        long start = System.currentTimeMillis() - 800;
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("bgImageWidth", vo.getBackgroundImageWidth());
        body.put("bgImageHeight", vo.getBackgroundImageHeight());
        body.put("templateImageWidth", vo.getTemplateImageWidth());
        body.put("templateImageHeight", vo.getTemplateImageHeight());
        body.put("startTime", new Date(start).toInstant().toString());
        body.put("stopTime", new Date(start + 800).toInstant().toString());
        List<Map<String, Object>> tracks = new ArrayList<>();
        tracks.add(point(0f, 0f, 0f, TrackTypeConstant.DOWN));
        tracks.add(point(targetX / 2f, 1f, 300f, TrackTypeConstant.MOVE));
        tracks.add(point(targetX, 1f, 800f, TrackTypeConstant.UP));
        body.put("trackList", tracks);

        ImageCaptchaTrack parsed = TianaiCaptchaVerificationPort.toTrack(body);
        assertNotNull(parsed);
        assertNotNull(parsed.getStartTime());
        assertNotNull(parsed.getStopTime());
        assertTrue(port.matchAndMark(generated.getId(), body), "ISO date string track should match");
    }

    @Test
    void epochMillisTrackMatches() {
        CaptchaResponse<ImageCaptchaVO> generated = app.generateCaptcha(CaptchaTypeConstant.SLIDER);
        ImageCaptchaVO vo = generated.getCaptcha();
        AnyMap cached = app.getCacheStore().getCache(properties.getPrefix() + ":" + generated.getId());
        Float percentage = cached.getFloat(SimpleImageCaptchaValidator.PERCENTAGE_KEY);
        float targetX = percentage * vo.getBackgroundImageWidth();
        long start = System.currentTimeMillis() - 800;
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("bgImageWidth", vo.getBackgroundImageWidth());
        body.put("bgImageHeight", vo.getBackgroundImageHeight());
        body.put("templateImageWidth", vo.getTemplateImageWidth());
        body.put("templateImageHeight", vo.getTemplateImageHeight());
        body.put("startTime", start);
        body.put("stopTime", start + 800);
        List<Map<String, Object>> tracks = new ArrayList<>();
        tracks.add(point(0f, 0f, 0f, TrackTypeConstant.DOWN));
        tracks.add(point(targetX / 2f, 1f, 300f, TrackTypeConstant.MOVE));
        tracks.add(point(targetX, 1f, 800f, TrackTypeConstant.UP));
        body.put("trackList", tracks);
        assertTrue(port.matchAndMark(generated.getId(), body), "epoch millis track should match");
    }

    private static Map<String, Object> point(float x, float y, float t, String type) {
        Map<String, Object> point = new LinkedHashMap<>();
        point.put("x", x);
        point.put("y", y);
        point.put("t", t);
        point.put("type", type);
        return point;
    }
}
