package com.koujunxin.member;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.koujunxin.member.code.MemberCodeGenerator;
import com.koujunxin.member.normalize.DouyinIds;
import com.koujunxin.member.normalize.ExpireTimes;
import com.koujunxin.member.normalize.MemberCodes;
import com.koujunxin.member.recalc.MemberRecalcService;

class MemberRulesTest {

    @Test
    void fifteenMonthsAnd435DaysYieldsFloorDaysQuota() {
        var result = new MemberRecalcService().recalc(
                435, 0,
                LocalDateTime.of(2026, 5, 31, 23, 59, 59, 999_000_000),
                LocalDateTime.of(2027, 8, 9, 23, 59, 59, 999_000_000),
                LocalDateTime.of(2026, 9, 21, 12, 0));
        assertEquals(14, result.availableCount());
        assertEquals(14, result.remainCount());
        assertEquals("VALID", result.status());
    }

    @Test
    void claimMonthsNeverEntersRecalcSignature() throws Exception {
        var method = MemberRecalcService.class.getMethod(
                "recalc", int.class, int.class, LocalDateTime.class, LocalDateTime.class, LocalDateTime.class);
        assertEquals(5, method.getParameterCount());
        assertFalse(method.toGenericString().toLowerCase().contains("month"));
    }

    @Test
    void generatedCodesExcludeAmbiguousChars() {
        MemberCodeGenerator generator = new MemberCodeGenerator();
        Set<String> codes = new HashSet<>();
        for (int i = 0; i < 400; i++) {
            String code = generator.next("KJX");
            assertTrue(MemberCodes.STORED.matcher(code).matches(), code);
            assertEquals(12, code.length());
            String random = code.substring(4);
            for (int n = 0; n < random.length(); n++) {
                assertFalse(MemberCodes.forbiddenChar(random.charAt(n)), code);
            }
            codes.add(code);
        }
        assertTrue(codes.size() > 300);
    }

    @Test
    void douyinAndExpireNormalization() {
        assertEquals("zhangsan", DouyinIds.normalize(" @ZhangSan "));
        assertEquals(
                LocalDateTime.of(2027, 8, 9, 23, 59, 59, 999_000_000),
                ExpireTimes.parseRequired("2027-08-09"));
        assertEquals(
                LocalDateTime.of(2027, 8, 9, 23, 59, 59, 999_000_000),
                ExpireTimes.parseRequired("2027-08-09T00:00:00+08:00"));
    }
}
