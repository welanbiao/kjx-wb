package com.koujunxin.work.content;

import java.util.List;
import java.util.Optional;

public interface ContentDocumentRepository {

    ContentDocumentRecord upsert(ContentDocumentRecord record);

    Optional<ContentDocumentRecord> find(long workId, String version, String docType);

    List<ContentDocumentRecord> list(long workId, String version);
}
