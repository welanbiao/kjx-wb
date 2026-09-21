package com.koujunxin.member;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.koujunxin.member.recalc.MemberRecalcService;

class MemberModuleTest {

    @Test
    void moduleNameIsStable() {
        assertEquals("kjx-member", MemberModule.NAME);
    }

    @Test
    void recalcServiceIsFinalAndUnique() {
        assertTrue(Modifier.isFinal(MemberRecalcService.class.getModifiers()));
        assertEquals(1, Stream.of(MemberRecalcService.class.getDeclaredMethods())
                .filter(method -> method.getName().equals("recalc"))
                .count());
    }

    @Test
    void flywayUsesStandardClasspathLocation() throws Exception {
        Path root = Path.of("src/main/resources/db/migration");
        assertTrue(Files.isDirectory(root));
        try (Stream<Path> files = Files.list(root)) {
            assertTrue(files.anyMatch(path -> path.getFileName().toString().startsWith("V300")));
        }
        String src = Files.readString(Path.of("src/test/java/com/koujunxin/member/support/MysqlMemberSupport.java"));
        assertTrue(src.contains("classpath:db/migration"));
        assertFalse(src.contains("classpath*:"));
    }

    @Test
    void noMonthToDayConversionInSources() throws Exception {
        Path javaRoot = Path.of("src/main/java");
        try (Stream<Path> files = Files.walk(javaRoot)) {
            boolean coupled = files
                    .filter(path -> path.toString().endsWith(".java"))
                    .map(path -> {
                        try {
                            return Files.readString(path);
                        } catch (Exception ex) {
                            throw new RuntimeException(ex);
                        }
                    })
                    .anyMatch(text -> text.contains("subscribedMonths * 30")
                            || text.contains("subscribed_months * 30")
                            || text.contains("claimMonths * 30"));
            assertFalse(coupled);
        }
    }
}
