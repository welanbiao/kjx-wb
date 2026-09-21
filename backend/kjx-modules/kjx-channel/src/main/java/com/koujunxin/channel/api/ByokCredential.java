package com.koujunxin.channel.api;

public record ByokCredential(SecretRef secretRef, String endpoint) {

    public ByokCredential {
        if (secretRef == null) {
            throw new IllegalArgumentException("secretRef rejected");
        }
        if (endpoint == null || endpoint.isBlank()) {
            throw new IllegalArgumentException("endpoint is required");
        }
        endpoint = endpoint.trim();
    }

    @Override
    public String toString() {
        return "ByokCredential[secretRef=redacted, endpoint=" + endpoint + "]";
    }
}
