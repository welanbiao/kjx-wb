package com.koujunxin.ai.openai;

import java.util.List;

public record OpenAiModelList(String object, List<OpenAiModel> data) {

    public OpenAiModelList {
        if (object == null || object.isBlank()) {
            object = "list";
        }
        data = data == null ? List.of() : List.copyOf(data);
    }

    public record OpenAiModel(String id, String object, long created, String ownedBy) {
        public OpenAiModel {
            if (object == null || object.isBlank()) {
                object = "model";
            }
            if (ownedBy == null || ownedBy.isBlank()) {
                ownedBy = "koujunxin";
            }
        }
    }
}
