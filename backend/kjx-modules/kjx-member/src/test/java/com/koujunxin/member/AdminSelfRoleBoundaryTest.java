package com.koujunxin.member;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import com.koujunxin.member.api.MemberException;
import com.koujunxin.member.api.port.DeptAccessPort;
import com.koujunxin.member.application.MemberApplicationService;
import com.koujunxin.member.web.AdminSelfMemberController;

class AdminSelfRoleBoundaryTest {

    @Test
    void operatorCannotUseHostSelfEndpoints() {
        MemberApplicationService members = mock(MemberApplicationService.class);
        DeptAccessPort access = mock(DeptAccessPort.class);
        when(access.host()).thenReturn(false);
        AdminSelfMemberController controller = new AdminSelfMemberController(members, access);

        MemberException ex = assertThrows(MemberException.class, () -> controller.host(null));

        assertEquals(403, ex.bizCode());
        verifyNoInteractions(members);
    }
}
