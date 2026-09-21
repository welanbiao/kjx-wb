package com.koujunxin.work.catalog;

public class DuplicateVersionException extends WorkDomainException {

    public DuplicateVersionException(long workId, String version) {
        super(409, "DUPLICATE_VERSION", "version already exists: " + workId + "/" + version);
    }
}
