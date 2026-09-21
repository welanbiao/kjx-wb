package com.koujunxin.tool.execute;

import com.koujunxin.runtime.api.dto.ToolResult;

public record CallClaim(long id, boolean replay, ToolResult replayed) {

    public static CallClaim fresh(long id) {
        return new CallClaim(id, false, null);
    }

    public static CallClaim replay(long id, ToolResult result) {
        return new CallClaim(id, true, result);
    }
}
