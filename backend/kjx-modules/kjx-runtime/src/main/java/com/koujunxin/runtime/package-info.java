/**
 * 运行时实现：会话 / 存档 / 分段披露 / Form A 校验 / 沙箱宿主代理。
 * <p>只允许依赖 runtime-api 公开端口；禁止依赖 kjx-tool 实现。作品包、素材签名走本模块窄接口，
 * 由装配方接到已有 work 能力，本模块不改 kjx-work。</p>
 */
package com.koujunxin.runtime;
