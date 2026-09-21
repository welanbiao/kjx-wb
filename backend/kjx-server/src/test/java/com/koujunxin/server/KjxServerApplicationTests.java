package com.koujunxin.server;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import com.koujunxin.account.application.AccountApplicationService;
import com.koujunxin.account.api.port.CaptchaVerificationPort;
import com.koujunxin.account.api.port.SmsDispatchPort;

import cloud.tianai.captcha.application.ImageCaptchaApplication;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class KjxServerApplicationTests {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void contextLoads() {
        assertNotNull(applicationContext);
        assertNotNull(applicationContext.getBean(KjxServerApplication.class));
        assertFalse(applicationContext.getBeanNamesForType(AccountApplicationService.class).length > 0);
        assertFalse(applicationContext.getBeanNamesForType(ImageCaptchaApplication.class).length > 0);
        assertFalse(applicationContext.getBeanNamesForType(CaptchaVerificationPort.class).length > 0);
        assertFalse(applicationContext.getBeanNamesForType(SmsDispatchPort.class).length > 0);
    }
}
