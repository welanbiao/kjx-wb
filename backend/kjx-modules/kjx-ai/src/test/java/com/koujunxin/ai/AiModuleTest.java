package com.koujunxin.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class AiModuleTest {

    @Test
    void moduleNameIsStable() {
        assertEquals("kjx-ai", AiModule.NAME);
        assertNotNull(AiModule.class.getPackage());
    }
}
