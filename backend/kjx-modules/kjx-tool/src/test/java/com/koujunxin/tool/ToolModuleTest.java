package com.koujunxin.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class ToolModuleTest {

    @Test
    void moduleNameIsStable() {
        assertEquals("kjx-tool", ToolModule.NAME);
        assertNotNull(ToolModule.class.getPackage());
    }
}
