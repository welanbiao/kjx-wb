package com.koujunxin.work.catalog;

/**
 * 业务码 1008，定义于 contracts/errors.yaml，禁止在此改码或改文案。
 */
public class WorkUnpublishedException extends WorkDomainException {

    public static final int CODE = 1008;

    public WorkUnpublishedException() {
        super(403, "WORK_UNPUBLISHED", "作品已下架");
    }
}
