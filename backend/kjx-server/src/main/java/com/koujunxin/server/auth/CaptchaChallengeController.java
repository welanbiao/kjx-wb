package com.koujunxin.server.auth;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.dromara.common.ratelimiter.annotation.RateLimiter;
import org.dromara.common.ratelimiter.enums.LimitType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.koujunxin.account.api.AccountException;
import com.koujunxin.account.api.port.CaptchaVerificationPort;
import com.koujunxin.account.web.AccountWebSupport;
import com.koujunxin.account.web.ApiBody;

import cloud.tianai.captcha.application.ImageCaptchaApplication;
import cloud.tianai.captcha.application.vo.CaptchaResponse;
import cloud.tianai.captcha.application.vo.ImageCaptchaVO;

@RestController
@RequestMapping("/app/auth")
@ConditionalOnBean(ImageCaptchaApplication.class)
public class CaptchaChallengeController {

    private static final Set<String> ALLOWED_TYPES = Set.of(
            "SLIDER", "ROTATE", "CONCAT", "WORD_IMAGE_CLICK");

    private final ImageCaptchaApplication captcha;
    private final KjxCaptchaProperties properties;
    private final CaptchaVerificationPort verificationPort;

    public CaptchaChallengeController(
            ImageCaptchaApplication captcha,
            KjxCaptchaProperties properties,
            CaptchaVerificationPort verificationPort) {
        this.captcha = captcha;
        this.properties = properties;
        this.verificationPort = verificationPort;
    }

    public record CheckBody(@NotBlank String captchaId, @NotNull Map<String, Object> captchaData) {
    }

    @GetMapping("/captcha")
    @RateLimiter(time = 60, count = 30, limitType = LimitType.IP)
    public ApiBody<CaptchaChallenge> captcha(
            @RequestParam(required = false) String type,
            HttpServletRequest request,
            HttpServletResponse response) {
        String requestId = AccountWebSupport.requestId(request);
        response.setHeader(AccountWebSupport.REQUEST_ID, requestId);
        response.setHeader("Cache-Control", "no-store");
        String requestedType = (type == null || type.isBlank()) ? properties.getType() : type;
        String captchaType = requestedType == null ? "" : requestedType.trim().toUpperCase(Locale.ROOT);
        if (!ALLOWED_TYPES.contains(captchaType)) {
            throw new AccountException(400, 400, "不支持的图形验证码类型");
        }
        CaptchaResponse<ImageCaptchaVO> generated;
        try {
            generated = captcha.generateCaptcha(captchaType);
        } catch (RuntimeException ex) {
            throw new AccountException(500, 500, "图形验证码生成失败");
        }
        if (generated == null || generated.getId() == null || generated.getCaptcha() == null) {
            throw new AccountException(500, 500, "图形验证码生成失败");
        }
        ImageCaptchaVO vo = generated.getCaptcha();
        CaptchaChallenge challenge = new CaptchaChallenge(
                generated.getId(),
                vo.getType(),
                vo.getBackgroundImage(),
                vo.getTemplateImage(),
                vo.getBackgroundImageTag(),
                vo.getTemplateImageTag(),
                vo.getBackgroundImageWidth(),
                vo.getBackgroundImageHeight(),
                vo.getTemplateImageWidth(),
                vo.getTemplateImageHeight(),
                vo.getData());
        return ApiBody.ok(challenge, requestId);
    }

    /** 松手即时校验：失败消耗挑战；成功写入短时 pass，供后续 send-code / 登录消费。 */
    @PostMapping("/captcha/check")
    @RateLimiter(time = 60, count = 60, limitType = LimitType.IP)
    public ApiBody<Map<String, Object>> check(
            @RequestBody CheckBody body,
            HttpServletRequest request,
            HttpServletResponse response) {
        String requestId = AccountWebSupport.requestId(request);
        response.setHeader(AccountWebSupport.REQUEST_ID, requestId);
        boolean ok;
        if (verificationPort instanceof TianaiCaptchaVerificationPort tianai) {
            ok = tianai.matchAndMark(body.captchaId(), body.captchaData());
        } else {
            ok = verificationPort.verify(body.captchaId(), body.captchaData());
        }
        if (!ok) {
            throw new AccountException(400, 400, "图形验证码错误或已过期");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("passed", true);
        return ApiBody.ok(data, requestId);
    }
}
