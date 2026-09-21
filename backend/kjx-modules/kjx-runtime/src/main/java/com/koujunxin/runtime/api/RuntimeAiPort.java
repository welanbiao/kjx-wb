package com.koujunxin.runtime.api;

/**
 * 可选 AI 完成。WP-40 未接线时宿主返回 AI_UNAVAILABLE，不得下发 Prompt。
 */
public interface RuntimeAiPort {

    String complete(long accountId, String sessionId, String workCode, String payloadJson);
}
