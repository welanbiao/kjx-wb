package com.koujunxin.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.TimeZone;

/**
 * 叩君芯唯一启动入口。本模块只做装配与启动，领域逻辑必须放在 kjx-modules。
 */
@SpringBootApplication(scanBasePackages = "com.koujunxin")
public class KjxServerApplication {

    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));
        SpringApplication.run(KjxServerApplication.class, args);
    }
}
