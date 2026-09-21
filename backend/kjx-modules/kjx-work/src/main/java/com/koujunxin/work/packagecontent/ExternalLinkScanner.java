package com.koujunxin.work.packagecontent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 默认拒绝一切外链。扫描文本类文件中的 http(s):// 与协议相对 URL。
 */
public final class ExternalLinkScanner {

    private static final Pattern URL = Pattern.compile("(?i)(?:https?:)?//[a-z0-9][^\\s\"'<>]*");
    private static final Pattern DATA_HTML = Pattern.compile("(?i)javascript:");

    private ExternalLinkScanner() {
    }

    public static List<String> scan(Path root, List<String> files) throws IOException {
        List<String> hits = new ArrayList<>();
        for (String relative : files) {
            if (!isTextLike(relative)) {
                continue;
            }
            Path file = IsolatedZipExtractor.safeResolve(root, relative);
            if (!Files.isRegularFile(file)) {
                continue;
            }
            String text = Files.readString(file, StandardCharsets.UTF_8);
            Matcher matcher = URL.matcher(text);
            while (matcher.find()) {
                if (isXmlns(text, matcher.start())) {
                    continue;
                }
                hits.add(relative + ":" + matcher.group());
            }
            if (DATA_HTML.matcher(text).find()) {
                hits.add(relative + ":javascript:");
            }
        }
        return hits;
    }

    static boolean isXmlns(String text, int urlStart) {
        int from = Math.max(0, urlStart - 40);
        String prefix = text.substring(from, urlStart);
        return prefix.matches("(?s).*xmlns(?::[A-Za-z0-9_-]+)?\\s*=\\s*[\"']?$");
    }

    static boolean isTextLike(String relative) {
        String lower = relative.toLowerCase(Locale.ROOT);
        return lower.endsWith(".json")
                || lower.endsWith(".svg")
                || lower.endsWith(".html")
                || lower.endsWith(".htm")
                || lower.endsWith(".css")
                || lower.endsWith(".js")
                || lower.endsWith(".md")
                || lower.endsWith(".txt")
                || lower.endsWith(".xml");
    }
}
