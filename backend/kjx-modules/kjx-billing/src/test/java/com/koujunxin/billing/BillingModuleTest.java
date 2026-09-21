package com.koujunxin.billing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class BillingModuleTest {

    @Test
    void moduleNameIsStable() {
        assertEquals("kjx-billing", BillingModule.NAME);
        assertNotNull(BillingModule.class.getPackage());
    }
}
