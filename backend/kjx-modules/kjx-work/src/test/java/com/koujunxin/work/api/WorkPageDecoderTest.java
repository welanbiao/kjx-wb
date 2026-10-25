package com.koujunxin.work.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class WorkPageDecoderTest {

    @Test
    void liveQuestionMarksBecomeChineseTitle() {
        assertEquals("分手结局修改器", WorkPageDecoder.name("breakup-editor", "??????"));
        assertEquals("分手结局修改器", WorkPageDecoder.name("breakup-editor", "????????"));
        assertEquals("分手结局修改器", WorkPageDecoder.name("breakup-editor", "\uFFFD\uFFFD\uFFFD"));
        assertEquals(
                "如果那句话没说出口，他们还会分手吗？今天，帮 TA 改写曾经的选择。",
                WorkPageDecoder.summary("breakup-editor", "????????????????"));
    }

    @Test
    void latin1MojibakeRecoversUtf8() {
        String mojibake = new String("分手结局修改器".getBytes(StandardCharsets.UTF_8), StandardCharsets.ISO_8859_1);
        assertFalse(mojibake.contains("分手"));
        assertEquals("分手结局修改器", WorkPageDecoder.name("breakup-editor", mojibake));
    }

    @Test
    void intactChineseNameIsKept() {
        assertEquals("分手结局修改器", WorkPageDecoder.name("breakup-editor", "分手结局修改器"));
        assertTrue(WorkPageDecoder.name("other-work", "午夜剧本").contains("午夜"));
    }
}
