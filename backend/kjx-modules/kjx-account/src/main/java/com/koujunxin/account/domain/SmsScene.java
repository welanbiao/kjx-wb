package com.koujunxin.account.domain;

import java.util.Locale;
import java.util.Set;

public enum SmsScene {
    REGISTER("register"),
    LOGIN("login"),
    RESET_PWD("reset_pwd"),
    CANCEL("cancel");

    private static final Set<String> NAMES = Set.of("register", "login", "reset_pwd", "cancel");

    private final String wire;

    SmsScene(String wire) {
        this.wire = wire;
    }

    public String wire() {
        return wire;
    }

    public static SmsScene fromWire(String value) {
        if (value == null) {
            throw new IllegalArgumentException("scene is required");
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (!NAMES.contains(normalized)) {
            throw new IllegalArgumentException("unsupported scene");
        }
        return switch (normalized) {
            case "register" -> REGISTER;
            case "login" -> LOGIN;
            case "reset_pwd" -> RESET_PWD;
            case "cancel" -> CANCEL;
            default -> throw new IllegalArgumentException("unsupported scene");
        };
    }
}
