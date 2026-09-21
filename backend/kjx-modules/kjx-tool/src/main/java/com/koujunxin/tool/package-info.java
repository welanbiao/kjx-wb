/**
 * 工具体系：builtin / compose / 前置校验。只依赖 runtime-api，禁止依赖 kjx-runtime。
 * <p>只允许依赖目标模块公开 api/port；禁止跨模块访问 Mapper、Entity、Repository 与内部 Service。</p>
 */
package com.koujunxin.tool;
