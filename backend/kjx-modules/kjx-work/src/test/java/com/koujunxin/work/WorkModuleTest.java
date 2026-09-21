package com.koujunxin.work;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class WorkModuleTest {

    @Test
    void moduleNameIsStable() {
        assertEquals("kjx-work", WorkModule.NAME);
        assertNotNull(WorkModule.class.getPackage());
    }
}
