package com.koujunxin.channel.api;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Map;

import org.junit.jupiter.api.Test;

class SsrfGuardTest {

    @Test
    void allowsPublicAddress() {
        SsrfGuard guard = guard(Map.of("api.example.com", "203.0.113.10"));
        assertDoesNotThrow(() -> guard.check("https://api.example.com/v1"));
    }

    @Test
    void rejectsPrivateDnsAnswer() {
        SsrfGuard guard = guard(Map.of("api.example.com", "10.1.2.3"));
        assertThrows(IllegalArgumentException.class, () -> guard.check("https://api.example.com/v1"));
    }

    @Test
    void rejectsMetadataAndMixedAnswers() {
        SsrfGuard metadata = guard(Map.of("meta.example.com", "169.254.169.254"));
        assertThrows(IllegalArgumentException.class, () -> metadata.check("http://meta.example.com/"));
        SsrfGuard mixed = guard(Map.of("mixed.example.com", "203.0.113.10,10.0.0.8"));
        assertThrows(IllegalArgumentException.class, () -> mixed.check("https://mixed.example.com/v1"));
    }

    @Test
    void rechecksEveryRedirect() {
        SsrfGuard guard = guard(Map.of(
                "api.example.com", "203.0.113.10",
                "127.0.0.1", "127.0.0.1"));
        assertThrows(IllegalArgumentException.class,
                () -> guard.checkRedirect("https://api.example.com/v1", "http://127.0.0.1/latest/meta-data"));
    }

    private static SsrfGuard guard(Map<String, String> answers) {
        return new SsrfGuard(host -> {
            String[] parts = answers.getOrDefault(host, "").split(",");
            if (parts.length == 1 && parts[0].isBlank()) {
                throw new UnknownHostException(host);
            }
            return java.util.Arrays.stream(parts).map(SsrfGuardTest::address).toList();
        });
    }

    private static InetAddress address(String literal) {
        try {
            return InetAddress.getByName(literal.trim());
        } catch (UnknownHostException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
