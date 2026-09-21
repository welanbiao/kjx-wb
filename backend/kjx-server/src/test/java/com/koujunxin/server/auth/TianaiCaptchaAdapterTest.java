package com.koujunxin.server.auth;

import static org.junit.jupiter.api.Assertions.assertFalse;
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
import com.koujunxin.account.api.port.CaptchaVerificationPort;

import cloud.tianai.captcha.application.ImageCaptchaApplication;
import cloud.tianai.captcha.application.vo.CaptchaResponse;
import cloud.tianai.captcha.application.vo.ImageCaptchaVO;
import cloud.tianai.captcha.cache.CacheStore;
import cloud.tianai.captcha.common.AnyMap;
import cloud.tianai.captcha.common.constant.CaptchaTypeConstant;
import cloud.tianai.captcha.validator.common.constant.TrackTypeConstant;
import cloud.tianai.captcha.validator.impl.SimpleImageCaptchaValidator;

@Testcontainers(disabledWithoutDocker = true)
class TianaiCaptchaAdapterTest {

    @Container
    @SuppressWarnings("resource")
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:8-alpine"))
            .withExposedPorts(6379)
            .waitingFor(Wait.forLogMessage(".*Ready to accept connections.*", 1))
            .withStartupTimeout(Duration.ofMinutes(2));

    private static RedissonClient redisson;

    private final ObjectMapper mapper = new ObjectMapper();
    private KjxCaptchaProperties properties;
    private ImageCaptchaApplication app;
    private CaptchaVerificationPort port;

    static synchronized RedissonClient redis() {
        if (redisson == null) {
            Config config = new Config();
            config.useSingleServer().setAddress("redis://" + REDIS.getHost() + ":" + REDIS.getMappedPort(6379));
            redisson = Redisson.create(config);
        }
        return redisson;
    }

    @BeforeEach
    void setUp() {
        redis().getKeys().flushdb();
        properties = new KjxCaptchaProperties();
        properties.setExpireMs(5_000L);
        CacheStore store = new RedissonCaptchaCacheStore(redis(), mapper);
        app = TianaiCaptchaSupport.create(store, properties);
        port = new TianaiCaptchaVerificationPort(app, mapper);
    }

    @Test
    void generateReturnsShareableChallenge() {
        CaptchaResponse<ImageCaptchaVO> generated = app.generateCaptcha(CaptchaTypeConstant.SLIDER);
        assertNotNull(generated.getId());
        ImageCaptchaVO vo = generated.getCaptcha();
        assertNotNull(vo.getBackgroundImage());
        assertTrue(vo.getBackgroundImage().length() > 32);
        assertNotNull(vo.getBackgroundImageWidth());
        assertTrue(vo.getBackgroundImageWidth() > 0);
        assertTrue(app.getCacheStore().getCache(cacheKey(generated.getId())) != null);
    }

    @Test
    void correctTrackPassesAndIsConsumedOnce() {
        CaptchaResponse<ImageCaptchaVO> generated = app.generateCaptcha(CaptchaTypeConstant.SLIDER);
        Map<String, Object> track = sliderTrack(generated, 1.0f);
        assertTrue(port.verify(generated.getId(), track));
        assertFalse(port.verify(generated.getId(), track));
    }

    @Test
    void wrongTrackFails() {
        CaptchaResponse<ImageCaptchaVO> generated = app.generateCaptcha(CaptchaTypeConstant.SLIDER);
        Map<String, Object> track = sliderTrack(generated, 0.05f);
        assertFalse(port.verify(generated.getId(), track));
    }

    @Test
    void expiredChallengeFails() throws Exception {
        properties.setExpireMs(400L);
        CacheStore store = new RedissonCaptchaCacheStore(redis(), mapper);
        ImageCaptchaApplication shortLived = TianaiCaptchaSupport.create(store, properties);
        CaptchaVerificationPort shortPort = new TianaiCaptchaVerificationPort(shortLived, mapper);
        CaptchaResponse<ImageCaptchaVO> generated = shortLived.generateCaptcha(CaptchaTypeConstant.SLIDER);
        Map<String, Object> track = sliderTrack(shortLived, generated, 1.0f);
        Thread.sleep(700);
        assertFalse(shortPort.verify(generated.getId(), track));
    }

    @Test
    void secondInstanceCanVerifyFirstInstanceChallenge() {
        CacheStore shared = new RedissonCaptchaCacheStore(redis(), mapper);
        ImageCaptchaApplication other = TianaiCaptchaSupport.create(shared, properties);
        CaptchaResponse<ImageCaptchaVO> generated = app.generateCaptcha(CaptchaTypeConstant.SLIDER);
        Map<String, Object> track = sliderTrack(generated, 1.0f);
        CaptchaVerificationPort otherPort = new TianaiCaptchaVerificationPort(other, mapper);
        assertTrue(otherPort.verify(generated.getId(), track));
    }

    private String cacheKey(String id) {
        return properties.getPrefix() + ":" + id;
    }

    private Map<String, Object> sliderTrack(CaptchaResponse<ImageCaptchaVO> generated, float scale) {
        return sliderTrack(app, generated, scale);
    }

    private Map<String, Object> sliderTrack(ImageCaptchaApplication application,
                                            CaptchaResponse<ImageCaptchaVO> generated, float scale) {
        ImageCaptchaVO vo = generated.getCaptcha();
        AnyMap cached = application.getCacheStore().getCache(cacheKey(generated.getId()));
        assertNotNull(cached);
        Float percentage = cached.getFloat(SimpleImageCaptchaValidator.PERCENTAGE_KEY);
        assertNotNull(percentage);
        float targetX = percentage * vo.getBackgroundImageWidth() * scale;
        long start = System.currentTimeMillis() - 800;
        List<Map<String, Object>> tracks = new ArrayList<>();
        tracks.add(point(0f, 0f, 0f, TrackTypeConstant.DOWN));
        tracks.add(point(targetX / 2f, 1f, 300f, TrackTypeConstant.MOVE));
        tracks.add(point(targetX, 1f, 800f, TrackTypeConstant.UP));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("bgImageWidth", vo.getBackgroundImageWidth());
        body.put("bgImageHeight", vo.getBackgroundImageHeight());
        body.put("templateImageWidth", vo.getTemplateImageWidth());
        body.put("templateImageHeight", vo.getTemplateImageHeight());
        body.put("startTime", new Date(start));
        body.put("stopTime", new Date(start + 800));
        body.put("trackList", tracks);
        return body;
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
