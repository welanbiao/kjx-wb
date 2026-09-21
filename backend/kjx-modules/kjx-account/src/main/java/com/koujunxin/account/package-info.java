/**
 * 账号：注册 / 登录 / 验证码 / 会话 / 设备。
 * <p>只允许依赖目标模块公开 api/port；禁止跨模块访问 Mapper、Entity、Repository 与内部 Service。</p>
 * <p>注册仅手机号，不开放邮箱。安全通知只写 Outbox，不在本模块直接发短信。</p>
 */
package com.koujunxin.account;
