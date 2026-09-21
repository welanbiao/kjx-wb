package com.koujunxin.work.catalog;

public class InvalidWorkTransitionException extends WorkDomainException {

    public InvalidWorkTransitionException(WorkStatus from, WorkStatus to) {
        super(400, "INVALID_WORK_TRANSITION",
                "illegal work status transition: " + from + " -> " + to);
        this.from = from;
        this.to = to;
    }

    private final WorkStatus from;
    private final WorkStatus to;

    public WorkStatus from() {
        return from;
    }

    public WorkStatus to() {
        return to;
    }
}
