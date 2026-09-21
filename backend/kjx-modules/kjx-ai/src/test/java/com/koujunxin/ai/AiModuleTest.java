package com.koujunxin.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

class AiModuleTest {

    @Test
    void moduleNameIsStable() {
        assertEquals("kjx-ai", AiModule.NAME);
        assertNotNullPackage();
    }

    @Test
    void flywayUsesStandardClasspathAndWp40Range() throws Exception {
        Path root = Path.of("src/main/resources/db/migration");
        assertTrue(Files.isDirectory(root));
        try (Stream<Path> files = Files.list(root)) {
            var names = files.map(path -> path.getFileName().toString()).sorted().toList();
            assertTrue(names.stream().anyMatch(name -> name.startsWith("V600")));
            assertTrue(names.stream().anyMatch(name -> name.startsWith("V601")));
            assertTrue(names.stream().anyMatch(name -> name.startsWith("V602")));
            assertTrue(names.stream().anyMatch(name -> name.startsWith("V603")));
            assertTrue(names.stream().noneMatch(name -> name.startsWith("V620")));
        }
        String support = Files.readString(Path.of("src/test/java/com/koujunxin/ai/jdbc/MysqlAiSupport.java"));
        assertTrue(support.contains("classpath:db/migration"));
        assertFalse(support.contains("classpath*:"));
        String pom = Files.readString(Path.of("pom.xml"));
        assertFalse(pom.contains("com.h2database"));
        assertFalse(pom.contains("kjx-channel"));
        assertFalse(pom.contains("kjx-billing"));
        assertFalse(pom.contains("kjx-tool"));
        assertFalse(pom.contains("kjx-runtime</"));
    }

    @Test
    void productionSourcesStayInsideModuleBoundary() throws Exception {
        Path javaRoot = Path.of("src/main/java");
        try (Stream<Path> files = Files.walk(javaRoot)) {
            files.filter(path -> path.toString().endsWith(".java")).forEach(path -> {
                try {
                    String text = Files.readString(path);
                    assertFalse(text.contains("import com.koujunxin.channel"), path.toString());
                    assertFalse(text.contains("import com.koujunxin.billing"), path.toString());
                    assertFalse(text.contains("import com.koujunxin.tool"), path.toString());
                    assertFalse(text.contains("import com.koujunxin.runtime.")
                            && !text.contains("import com.koujunxin.runtime.api"), path.toString());
                    if (text.contains("import com.koujunxin.runtime.") && !text.contains("runtime.api")) {
                        throw new AssertionError(path + " imports runtime implementation");
                    }
                    assertFalse(text.contains("SessionStateCommandPort"), path.toString());
                    assertFalse(text.contains("classpath*:"), path.toString());
                } catch (Error | RuntimeException ex) {
                    throw ex;
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            });
        }
    }

    private static void assertNotNullPackage() {
        org.junit.jupiter.api.Assertions.assertNotNull(AiModule.class.getPackage());
    }
}
