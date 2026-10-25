package com.koujunxin.account.web;

import java.util.Map;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public final class AccountRequests {

    private AccountRequests() {
    }

    public record SendCodeRequest(
            @NotBlank String phone,
            @NotBlank String scene,
            @NotBlank String captchaId,
            @NotNull Map<String, Object> captchaData
    ) {
    }

    public record RegisterRequest(
            @NotBlank String phone,
            @NotBlank String password,
            @NotBlank String code,
            @NotNull Boolean agreeTerms
    ) {
    }

    public record LoginRequest(
            @NotBlank String phone,
            @NotBlank String password,
            String captchaId,
            Map<String, Object> captchaData
    ) {
    }

    public record LoginByCodeRequest(
            @NotBlank String phone,
            @NotBlank String code
    ) {
    }

    public record ResetPasswordRequest(
            @NotBlank String phone,
            @NotBlank String code,
            @NotBlank String newPassword
    ) {
    }

    public record ChangePasswordRequest(
            @NotBlank String oldPassword,
            @NotBlank String newPassword
    ) {
    }

    public record UpdateProfileRequest(String nickname, String avatar) {
    }

    public record CancelAccountRequest(
            @NotBlank String code,
            String reason
    ) {
    }
}
