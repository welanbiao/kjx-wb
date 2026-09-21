package com.koujunxin.work.catalog;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class WorkStateMachineTest {

    @Test
    void draftMustBeReviewedBeforePublish() {
        assertTrue(WorkStateMachine.canTransit(WorkStatus.DRAFT, WorkStatus.PENDING_REVIEW));
        assertFalse(WorkStateMachine.canTransit(WorkStatus.DRAFT, WorkStatus.PUBLISHED));
        assertFalse(WorkStateMachine.canTransit(WorkStatus.DRAFT, WorkStatus.GRAY));
        assertFalse(WorkStateMachine.canTransit(WorkStatus.DRAFT, WorkStatus.UNPUBLISHED));
    }

    @Test
    void pendingReviewCanPublishGrayOrReject() {
        assertTrue(WorkStateMachine.canTransit(WorkStatus.PENDING_REVIEW, WorkStatus.PUBLISHED));
        assertTrue(WorkStateMachine.canTransit(WorkStatus.PENDING_REVIEW, WorkStatus.GRAY));
        assertTrue(WorkStateMachine.canTransit(WorkStatus.PENDING_REVIEW, WorkStatus.REJECTED));
        assertTrue(WorkStateMachine.canTransit(WorkStatus.PENDING_REVIEW, WorkStatus.DRAFT));
        assertFalse(WorkStateMachine.canTransit(WorkStatus.PENDING_REVIEW, WorkStatus.UNPUBLISHED));
    }

    @Test
    void publishedCanGrayOrUnpublish() {
        assertTrue(WorkStateMachine.canTransit(WorkStatus.PUBLISHED, WorkStatus.GRAY));
        assertTrue(WorkStateMachine.canTransit(WorkStatus.PUBLISHED, WorkStatus.UNPUBLISHED));
        assertFalse(WorkStateMachine.canTransit(WorkStatus.PUBLISHED, WorkStatus.DRAFT));
        assertFalse(WorkStateMachine.canTransit(WorkStatus.PUBLISHED, WorkStatus.REJECTED));
    }

    @Test
    void unpublishedCanRelist() {
        assertTrue(WorkStateMachine.canTransit(WorkStatus.UNPUBLISHED, WorkStatus.PUBLISHED));
        assertTrue(WorkStateMachine.canTransit(WorkStatus.UNPUBLISHED, WorkStatus.GRAY));
        assertFalse(WorkStateMachine.canTransit(WorkStatus.UNPUBLISHED, WorkStatus.DRAFT));
    }

    @Test
    void rejectedCanResubmit() {
        assertTrue(WorkStateMachine.canTransit(WorkStatus.REJECTED, WorkStatus.DRAFT));
        assertTrue(WorkStateMachine.canTransit(WorkStatus.REJECTED, WorkStatus.PENDING_REVIEW));
        assertFalse(WorkStateMachine.canTransit(WorkStatus.REJECTED, WorkStatus.PUBLISHED));
    }

    @Test
    void sameStatusIsNoop() {
        for (WorkStatus status : WorkStatus.values()) {
            assertTrue(WorkStateMachine.canTransit(status, status));
        }
    }

    @Test
    void illegalTransitionThrows() {
        assertThrows(InvalidWorkTransitionException.class,
                () -> WorkStateMachine.requireTransit(WorkStatus.DRAFT, WorkStatus.PUBLISHED));
    }

    @Test
    void publishTimeStampedOnlyWhenEnteringLive() {
        assertTrue(WorkStateMachine.shouldStampPublishTime(WorkStatus.PENDING_REVIEW, WorkStatus.PUBLISHED));
        assertTrue(WorkStateMachine.shouldStampPublishTime(WorkStatus.PENDING_REVIEW, WorkStatus.GRAY));
        assertFalse(WorkStateMachine.shouldStampPublishTime(WorkStatus.PUBLISHED, WorkStatus.GRAY));
        assertFalse(WorkStateMachine.shouldStampPublishTime(WorkStatus.GRAY, WorkStatus.PUBLISHED));
        assertFalse(WorkStateMachine.shouldStampPublishTime(WorkStatus.PUBLISHED, WorkStatus.UNPUBLISHED));
    }
}
