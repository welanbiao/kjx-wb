/**
 * 日志：结构化日志 / 脱敏 / 分析投影。
 * <p>公开契约仅在 {@code api.port}。禁止跨模块访问 Mapper、Entity、Repository 与内部 Service。
 * ClickHouse 仅本模块使用；核心领域不得查询 ClickHouse。</p>
 */
package com.koujunxin.log;
