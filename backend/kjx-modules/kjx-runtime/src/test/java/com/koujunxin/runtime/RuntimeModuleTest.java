package com.koujunxin.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

class RuntimeModuleTest {

    @Test
    void moduleNameIsStable() {
        assertEquals("kjx-runtime", RuntimeModule.NAME);
        assertNotNull(RuntimeModule.class.getPackage());
        assertTrue(Modifier.isFinal(RuntimeModule.class.getModifiers()));
    }

    @Test
    void flywayUsesStandardClasspathLocation() throws Exception {
        Path root = Path.of("src/main/resources/db/migration");
        assertTrue(Files.isDirectory(root));
        try (Stream<Path> files = Files.list(root)) {
            var names = files.map(path -> path.getFileName().toString()).toList();
            assertTrue(names.stream().anyMatch(name -> name.startsWith("V630")));
            assertTrue(names.stream().anyMatch(name -> name.startsWith("V631")));
            assertTrue(names.stream().anyMatch(name -> name.startsWith("V632")));
            assertTrue(names.stream().noneMatch(name -> name.startsWith("V650")));
        }
        String support = Files.readString(Path.of("src/test/java/com/koujunxin/runtime/jdbc/MysqlRuntimeSupport.java"));
        assertTrue(support.contains("classpath:db/migration"));
        assertFalse(support.contains("classpath*:"));
        String pom = Files.readString(Path.of("pom.xml"));
        assertFalse(pom.contains("<artifactId>kjx-tool</artifactId>"));
        String src = Files.readString(Path.of("src/main/java/com/koujunxin/runtime/session/RuntimeApplicationService.java"));
        assertTrue(src.contains("ToolExecutionPort"));
        assertFalse(src.contains("com.koujunxin.tool"));
    }
}
