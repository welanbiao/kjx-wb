package com.koujunxin.server.auth;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import cloud.tianai.captcha.application.ImageCaptchaApplication;
import cloud.tianai.captcha.application.vo.CaptchaResponse;
import cloud.tianai.captcha.application.vo.ImageCaptchaVO;

class CaptchaChallengeControllerTest {

    private ImageCaptchaApplication captcha;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        captcha = Mockito.mock(ImageCaptchaApplication.class);
        KjxCaptchaProperties properties = new KjxCaptchaProperties();
        mockMvc = MockMvcBuilders.standaloneSetup(new CaptchaChallengeController(captcha, properties))
                .setControllerAdvice(new AuthAdapterExceptionAdvice())
                .build();
    }

    @Test
    void generateWrapsChallengeInApiBody() throws Exception {
        ImageCaptchaVO vo = new ImageCaptchaVO();
        vo.setType("SLIDER");
        vo.setBackgroundImage("data:image/jpeg;base64,abc");
        vo.setTemplateImage("data:image/png;base64,def");
        vo.setBackgroundImageWidth(600);
        vo.setBackgroundImageHeight(360);
        vo.setTemplateImageWidth(110);
        vo.setTemplateImageHeight(360);
        when(captcha.generateCaptcha(anyString())).thenReturn(CaptchaResponse.of("challenge-1", vo));

        mockMvc.perform(get("/app/auth/captcha").header("X-Request-Id", "req-captcha-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.requestId").value("req-captcha-1"))
                .andExpect(jsonPath("$.data.id").value("challenge-1"))
                .andExpect(jsonPath("$.data.type").value("SLIDER"))
                .andExpect(jsonPath("$.data.backgroundImage").value("data:image/jpeg;base64,abc"))
                .andExpect(jsonPath("$.data.backgroundImageWidth").value(600));
    }

    @Test
    void rejectsUnsupportedCaptchaTypeBeforeCallingLibrary() throws Exception {
        mockMvc.perform(get("/app/auth/captcha")
                        .queryParam("type", "unknown")
                        .header("X-Request-Id", "req-captcha-invalid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.requestId").value("req-captcha-invalid"));

        Mockito.verifyNoInteractions(captcha);
    }
}
