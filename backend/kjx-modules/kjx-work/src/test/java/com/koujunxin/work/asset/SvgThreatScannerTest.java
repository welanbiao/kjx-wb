package com.koujunxin.work.asset;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SvgThreatScannerTest {

    @Test
    void rejectsScriptOnEventAndExternalLink() {
        assertTrue(SvgThreatScanner.threats("<svg><script>alert(1)</script></svg>").contains("script"));
        assertTrue(SvgThreatScanner.threats("<svg onclick=\"alert(1)\"></svg>").stream()
                .anyMatch(hit -> hit.toLowerCase().contains("onclick")));
        assertTrue(SvgThreatScanner.threats("<svg><image href=\"https://evil.example/x.png\"/></svg>")
                .contains("external-link"));
        assertTrue(SvgThreatScanner.threats("<svg><rect fill=\"url(https://evil.example/a)\"/></svg>")
                .contains("external-link"));
    }

    @Test
    void allowsPlainSvg() {
        assertTrue(SvgThreatScanner.threats(
                "<svg xmlns=\"http://www.w3.org/2000/svg\"><rect width=\"1\" height=\"1\"/></svg>").isEmpty());
        assertFalse(SvgThreatScanner.isSvg("icon.png"));
        assertTrue(SvgThreatScanner.isSvg("assets/images/icon.SVG"));
    }
}
