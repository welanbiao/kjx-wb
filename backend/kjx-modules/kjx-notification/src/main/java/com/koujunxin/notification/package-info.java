/**
 * 通知：Outbox 消费 / 站内信 / 短信投递
 * <p>只允许依赖目标模块公开 api/port；禁止跨模块访问 Mapper、Entity、Repository 与内部 Service。
 * 副作用必须走 Transactional Outbox，禁止在业务事务内直接调用 SMS4J。</p>
 */
package com.koujunxin.notification;
