package com.koujunxin.ai;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.koujunxin.ai.api.ByokSecret;
import com.koujunxin.ai.prompt.AssembledPrompt;

class SecretHygieneTest {

    @Test
    void secretsAndPromptBodiesAreRedacted() {
        ByokSecret secret = ByokSecret.of("sk-very-secret-key");
        assertFalse(secret.toString().contains("sk-very-secret-key"));
        secret.wipe();
        AssembledPrompt assembled = new AssembledPrompt("1", "机密母版正文", "可变状态", java.util.List.of());
        assertFalse(assembled.toString().contains("机密母版正文"));
        assertFalse(assembled.toString().contains("可变状态"));
    }

    @Test
    void productionLogsDoNotPrintPromptOrKeys() throws Exception {
        Path javaRoot = Path.of("src/main/java");
        try (Stream<Path> files = Files.walk(javaRoot)) {
            files.filter(path -> path.toString().endsWith(".java")).forEach(path -> {
                try {
                    String text = Files.readString(path);
                    for (String line : text.split("\n")) {
                        String trimmed = line.trim();
                        if (trimmed.contains("log.") && (trimmed.contains("info") || trimmed.contains("debug") || trimmed.contains("warn") || trimmed.contains("error"))) {
                            assertFalse(trimmed.contains("stablePrefix"), path.toString());
                            assertFalse(trimmed.contains("byokSecret"), path.toString());
                            assertFalse(trimmed.contains("reveal()"), path.toString());
                            assertFalse(trimmed.contains("METHOD"), path.toString());
                        }
                    }
                    assertFalse(text.contains("X-KJX-BYOK-Key"), path.toString());
                } catch (Error | RuntimeException ex) {
                    throw ex;
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            });
        }
        assertTrue(Files.exists(Path.of("src/main/java/com/koujunxin/ai/observe/SafeLangfuseReporter.java")));
    }
}
