package com.koujunxin.member.api.port;

/**
 * 后台数据权限。HOST 自助路径只允许本部门；运营走全部/自定义范围。
 * 生产应由 kjx-server 装配 {@code DeptDataScopeGuard} 适配器。
 */
public interface DeptAccessPort {

    long requireActorId();

    long requireDeptId();

    void requireAccess(long resourceDeptId);

    boolean ops();

    boolean host();
}
