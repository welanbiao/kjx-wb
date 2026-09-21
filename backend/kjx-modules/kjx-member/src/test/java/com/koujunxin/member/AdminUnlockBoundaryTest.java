package com.koujunxin.member;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import com.koujunxin.member.api.MemberException;
import com.koujunxin.member.api.port.DeptAccessPort;
import com.koujunxin.member.unlock.UnlockApplicationService;
import com.koujunxin.member.web.AdminOpsUnlockController;
import com.koujunxin.work.api.WorkCatalogQueryPort;

class AdminUnlockBoundaryTest {

    @Test
    void hostCannotCallOpsEntitlementOrUnlockRecord() {
        UnlockApplicationService unlocks = mock(UnlockApplicationService.class);
        WorkCatalogQueryPort works = mock(WorkCatalogQueryPort.class);
        DeptAccessPort host = mock(DeptAccessPort.class);
        when(host.ops()).thenReturn(false);
        when(host.host()).thenReturn(true);
        AdminOpsUnlockController controller = new AdminOpsUnlockController(unlocks, host, works);

        MemberException adjust = assertThrows(MemberException.class,
                () -> controller.adjust(1L, new AdminOpsUnlockController.AdjustBody("ADD", "w", "x"), "k", null));
        MemberException page = assertThrows(MemberException.class,
                () -> controller.page(1L, 1L, null, null, null, 1, 20, null));
        assertEquals(403, adjust.bizCode());
        assertEquals(403, page.bizCode());
        verifyNoInteractions(unlocks);
    }
}
