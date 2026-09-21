package com.koujunxin.channel.api;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 每次连接前重新解析并复验。任一地址落在私网、链路本地、环回或元数据网段即拒绝。
 */
public final class SsrfGuard {

    private static final Set<String> BLOCKED_HOSTS = Set.of(
            "localhost",
            "metadata.google.internal",
            "metadata.internal");

    private final DnsLookup lookup;

    public SsrfGuard(DnsLookup lookup) {
        if (lookup == null) {
            throw new IllegalArgumentException("lookup is required");
        }
        this.lookup = lookup;
    }

    public void check(String url) {
        URI uri = parse(url);
        String host = normalizeHost(uri);
        if (isBlockedHost(host) || host.endsWith(".internal") || host.endsWith(".local")) {
            throw rejected();
        }
        List<InetAddress> addresses;
        try {
            addresses = lookup.resolve(host);
        } catch (UnknownHostException ex) {
            throw rejected();
        }
        if (addresses == null || addresses.isEmpty()) {
            throw rejected();
        }
        for (InetAddress address : addresses) {
            if (address == null || isBlocked(address)) {
                throw rejected();
            }
        }
    }

    public void checkRedirect(String currentUrl, String location) {
        URI base = parse(currentUrl);
        URI next = base.resolve(location);
        check(next.toString());
    }

    private static URI parse(String url) {
        if (url == null || url.isBlank()) {
            throw rejected();
        }
        URI uri;
        try {
            uri = URI.create(url.trim());
        } catch (IllegalArgumentException ex) {
            throw rejected();
        }
        String scheme = uri.getScheme();
        if (scheme == null
                || (!scheme.equalsIgnoreCase("https") && !scheme.equalsIgnoreCase("http"))) {
            throw rejected();
        }
        if (uri.getUserInfo() != null) {
            throw rejected();
        }
        return uri;
    }

    private static String normalizeHost(URI uri) {
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw rejected();
        }
        return host.toLowerCase(Locale.ROOT);
    }

    private static boolean isBlockedHost(String host) {
        return BLOCKED_HOSTS.contains(host);
    }

    static boolean isBlocked(InetAddress address) {
        if (address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return true;
        }
        byte[] bytes = address.getAddress();
        if (bytes.length == 16) {
            int first = bytes[0] & 0xff;
            return (first & 0xfe) == 0xfc;
        }
        return false;
    }

    private static IllegalArgumentException rejected() {
        return new IllegalArgumentException("endpoint rejected");
    }
}
