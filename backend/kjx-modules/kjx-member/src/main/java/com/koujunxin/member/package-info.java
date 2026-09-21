/**
 * 会员：主播扩展 / 三要素绑定 / 审核 / 申诉 / 额度重算。
 * <p>只允许依赖目标模块公开 api/port；禁止跨模块访问 Mapper、Entity、Repository 与内部 Service。
 * 禁止直连 account Mapper；账号 bind_status 只通过 {@code AccountBindingCommandPort}。
 * 短信不得在事务内直接发送，只写 Outbox。权益/解锁属 WP-13（V330+）。</p>
 */
package com.koujunxin.member;
