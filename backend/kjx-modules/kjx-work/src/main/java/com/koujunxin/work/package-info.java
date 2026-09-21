/**
 * 作品：目录 / 版本 / 内容 / 素材 / 音乐 / 包
 * <p>只允许依赖目标模块公开 api/port；禁止跨模块访问 Mapper、Entity、Repository 与内部 Service。
 * WP-12 不实现解锁，不读取 member Mapper。可见性只走 {@code WorkVisibilityService}。
 * 权益查询通过 {@code EntitlementQueryPort} 窄端口，由 WP-13 实现。</p>
 */
package com.koujunxin.work;
