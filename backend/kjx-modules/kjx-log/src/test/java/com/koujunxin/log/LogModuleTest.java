package com.koujunxin.log;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class LogModuleTest {

    @Test
    void moduleNameIsStable() {
        assertEquals("kjx-log", LogModule.NAME);
        assertEquals("1.0", LogModule.SCHEMA_VERSION);
        assertNotNull(LogModule.class.getPackage());
    }
}
