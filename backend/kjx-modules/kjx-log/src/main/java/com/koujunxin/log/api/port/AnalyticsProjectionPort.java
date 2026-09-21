package com.koujunxin.log.api.port;

import java.util.Map;

/**
 * 分析投影入口：把 ACCESS / AI_CALL / TOOL_CALL 写入 Outbox，而不是直接打 ClickHouse。
 * ClickHouse 停机只造成 Outbox 积压，不影响本调用的成功返回。
 */
public interface AnalyticsProjectionPort {

    String projectAccess(Map<String, Object> payload);

    String projectAiCall(Map<String, Object> payload);

    String projectToolCall(Map<String, Object> payload);
}
