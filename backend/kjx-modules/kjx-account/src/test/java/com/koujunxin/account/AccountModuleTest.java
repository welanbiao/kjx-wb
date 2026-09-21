package com.koujunxin.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class AccountModuleTest {

    @Test
    void moduleNameIsStable() {
        assertEquals("kjx-account", AccountModule.NAME);
        assertNotNull(AccountModule.class.getPackage());
    }
}
