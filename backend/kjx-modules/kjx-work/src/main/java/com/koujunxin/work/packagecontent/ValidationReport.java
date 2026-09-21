package com.koujunxin.work.packagecontent;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public record ValidationReport(boolean passed, List<ReviewFinding> findings) {

    public ValidationReport {
        findings = findings == null ? List.of() : List.copyOf(findings);
    }

    public static ValidationReport of(List<ReviewFinding> findings) {
        boolean passed = findings.stream().noneMatch(ReviewFinding::failed);
        return new ValidationReport(passed, findings);
    }

    public Map<ReviewCheck, ReviewFinding> byCheck() {
        Map<ReviewCheck, ReviewFinding> map = new EnumMap<>(ReviewCheck.class);
        for (ReviewFinding finding : findings) {
            map.put(finding.check(), finding);
        }
        return map;
    }

    public ReviewFinding finding(ReviewCheck check) {
        return byCheck().get(check);
    }
}
