package com.koujunxin.runtime.forma;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.koujunxin.runtime.api.RuntimeFailure;

class FormAComponentsTest {

    @Test
    void whitelistAcceptsKnownAndSlot() {
        assertTrue(FormAComponents.allowed("chat.bubble"));
        assertTrue(FormAComponents.allowed("custom.slot:mood"));
        assertFalse(FormAComponents.allowed("evil.widget"));
        assertFalse(FormAComponents.allowed("custom.slot:bad space"));
    }

    @Test
    void layoutRejectsUnknownAndHandlers() {
        assertThrows(RuntimeFailure.class, () -> FormAComponents.validateLayout(
                "{\"screens\":[{\"nodes\":[{\"type\":\"evil.widget\"}]}]}"));
        assertThrows(RuntimeFailure.class, () -> FormAComponents.validateLayout(
                "{\"screens\":[{\"nodes\":[{\"type\":\"chat.bubble\",\"onClick\":\"alert(1)\"}]}]}"));
    }
}
