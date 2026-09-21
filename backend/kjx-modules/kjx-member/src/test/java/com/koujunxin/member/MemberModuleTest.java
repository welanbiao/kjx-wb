package com.koujunxin.member;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class MemberModuleTest {

    @Test
    void moduleNameIsStable() {
        assertEquals("kjx-member", MemberModule.NAME);
        assertNotNull(MemberModule.class.getPackage());
    }
}
