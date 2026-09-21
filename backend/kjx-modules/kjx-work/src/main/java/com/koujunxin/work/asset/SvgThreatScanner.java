package com.koujunxin.work.asset;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SVG 拒绝 script、on* 事件、外链。
 */
public final class SvgThreatScanner {

    private static final Pattern SCRIPT = Pattern.compile("(?is)<\\s*script\\b");
    private static final Pattern HANDLER = Pattern.compile("(?i)\\bon[a-z]+\\s*=");
    private static final Pattern HREF = Pattern.compile("(?is)(?:xlink:)?href\\s*=\\s*['\"]\\s*(https?:)?//");
    private static final Pattern CSS_URL = Pattern.compile("(?is)url\\(\\s*['\"]?(https?:)?//");
    private static final Pattern FOREIGN = Pattern.compile("(?is)<\\s*foreignObject\\b");
    private static final Pattern USE_EXTERNAL = Pattern.compile("(?is)<\\s*use\\b[^>]+(?:xlink:)?href\\s*=\\s*['\"]\\s*https?:");

    private SvgThreatScanner() {
    }

    public static List<String> threats(String svg) {
        List<String> hits = new ArrayList<>();
        if (svg == null || svg.isBlank()) {
            hits.add("empty svg");
            return hits;
        }
        if (SCRIPT.matcher(svg).find()) {
            hits.add("script");
        }
        Matcher handlers = HANDLER.matcher(svg);
        while (handlers.find()) {
            hits.add(handlers.group().trim());
        }
        if (HREF.matcher(svg).find() || CSS_URL.matcher(svg).find() || USE_EXTERNAL.matcher(svg).find()) {
            hits.add("external-link");
        }
        if (FOREIGN.matcher(svg).find()) {
            hits.add("foreignObject");
        }
        return hits;
    }

    public static List<String> threats(byte[] bytes) {
        return threats(new String(bytes, StandardCharsets.UTF_8));
    }

    public static boolean isSvg(String path) {
        return path != null && path.toLowerCase(Locale.ROOT).endsWith(".svg");
    }
}
