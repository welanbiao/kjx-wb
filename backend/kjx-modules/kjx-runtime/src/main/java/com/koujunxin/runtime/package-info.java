/**
 * 运行时实现：会话 / 存档 / 分段披露。只依赖 runtime-api，禁止依赖 kjx-tool。
 * <p>只允许依赖目标模块公开 api/port；禁止跨模块访问 Mapper、Entity、Repository 与内部 Service。</p>
 */
package com.koujunxin.runtime;
