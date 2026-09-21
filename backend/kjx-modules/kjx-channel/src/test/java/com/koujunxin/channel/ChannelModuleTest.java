package com.koujunxin.channel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class ChannelModuleTest {

    @Test
    void moduleNameIsStable() {
        assertEquals("kjx-channel", ChannelModule.NAME);
        assertNotNull(ChannelModule.class.getPackage());
    }
}
