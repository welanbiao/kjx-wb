package com.koujunxin.ai.route;

import com.koujunxin.ai.api.AiChannelMode;
import com.koujunxin.ai.api.AiException;
import com.koujunxin.ai.api.AiTaskKind;
import com.koujunxin.ai.api.ModelTier;
import com.koujunxin.ai.api.RoutedModel;
import com.koujunxin.ai.api.port.ModelRouter;

public final class DefaultModelRouter implements ModelRouter {

    private final String strongModel;
    private final String cheapModel;

    public DefaultModelRouter() {
        this("kjx-strong", "kjx-cheap");
    }

    public DefaultModelRouter(String strongModel, String cheapModel) {
        this.strongModel = strongModel;
        this.cheapModel = cheapModel;
    }

    @Override
    public RoutedModel route(RouteRequest request) {
        if (request == null || request.channel() == null) {
            throw new IllegalArgumentException("channel is required");
        }
        ModelTier tier = request.taskKind() == AiTaskKind.CHAT ? ModelTier.STRONG : ModelTier.CHEAP;
        if (request.channel() == AiChannelMode.BYOK) {
            if (request.byokSecret() == null || request.byokBaseUrl() == null || request.byokBaseUrl().isBlank()
                    || request.byokModel() == null || request.byokModel().isBlank()) {
                throw AiException.byokCredentialRequired();
            }
            return RoutedModel.byok(request.byokModel(), request.byokBaseUrl());
        }
        String modelId = tier == ModelTier.CHEAP ? cheapModel : strongModel;
        return RoutedModel.platform(tier, modelId);
    }
}
