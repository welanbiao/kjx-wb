/**
 * 会员：主播扩展 / 三要素绑定 / 审核 / 申诉 / 额度重算。
 * <p>只允许依赖目标模块公开 api/port；禁止跨模块访问 Mapper、Entity、Repository 与内部 Service。
 * 禁止直连 account Mapper；账号 bind_status 只通过 {@code AccountBindingCommandPort}。
 * 短信不得在事务内直接发送，只写 Outbox。
 * 权益/解锁属 WP-13（V330+）：实现 EntitlementQueryPort / EntitlementAccessPort 与原子解锁。
 * 可见性只调用 kjx-work 的 WorkVisibilityService，禁止复制灰度算法或访问 work Mapper。
 */
package com.koujunxin.member;
