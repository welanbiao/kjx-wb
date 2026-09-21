package com.koujunxin.work.catalog;

public record AdminWorkQuery(
        WorkType workType,
        WorkStatus status,
        String keyword,
        int pageNo,
        int pageSize
) {

    public AdminWorkQuery {
        pageNo = pageNo < 1 ? 1 : pageNo;
        pageSize = pageSize < 1 ? 20 : Math.min(pageSize, 100);
        keyword = keyword == null || keyword.isBlank() ? null : keyword.trim();
    }

    public int offset() {
        return (pageNo - 1) * pageSize;
    }
}
