package com.koujunxin.work;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import com.koujunxin.work.port.EntitlementQueryPort;
import com.koujunxin.work.visibility.WorkVisibilityService;

class WorkModuleTest {

    @Test
    void moduleNameIsStable() {
        assertEquals("kjx-work", WorkModule.NAME);
        assertNotNull(WorkModule.class.getPackage());
    }

    @Test
    void visibilityServiceIsFinalAndUniquePublicType() {
        assertTrue(java.lang.reflect.Modifier.isFinal(WorkVisibilityService.class.getModifiers()));
        long impls = Arrays.stream(WorkVisibilityService.class.getDeclaredMethods())
                .filter(method -> method.getName().equals("visibleInPublicCatalog"))
                .count();
        assertTrue(impls >= 1);
        assertFalse(EntitlementQueryPort.class.getPackageName().contains("member"));
    }
}
