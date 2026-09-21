package com.koujunxin.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class RuntimeModuleTest {

    @Test
    void moduleNameIsStable() {
        assertEquals("kjx-runtime", RuntimeModule.NAME);
        assertNotNull(RuntimeModule.class.getPackage());
    }
}
