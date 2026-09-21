package com.koujunxin.log.envelope;

/**
 * docs/06 十二类日志。MDC 与结构化字段名全平台统一使用下划线。
 */
public enum LogCategory {
    BIZ,
    BILLING,
    AUDIT,
    SEC,
    MOD,
    ACCESS,
    AI_CALL,
    TOOL_CALL,
    AI_CONTENT,
    APP,
    TRACE,
    ERROR
}
