package com.koujunxin.work.api;

import java.util.List;

public record WorkPageView(List<WorkSummaryView> list, long total) {
}
