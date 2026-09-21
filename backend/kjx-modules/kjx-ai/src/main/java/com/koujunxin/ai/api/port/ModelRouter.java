package com.koujunxin.ai.api.port;

import com.koujunxin.ai.api.AiTaskKind;
import com.koujunxin.ai.api.ByokSecret;
import com.koujunxin.ai.api.RoutedModel;

public interface ModelRouter {

    RoutedModel route(RouteRequest request);

    record RouteRequest(
            com.koujunxin.ai.api.AiChannelMode channel,
            AiTaskKind taskKind,
            String byokBaseUrl,
            String byokModel,
            ByokSecret byokSecret
    ) {
        @Override
        public String toString() {
            return "RouteRequest[channel=" + channel + ", taskKind=" + taskKind + "]";
        }
    }
}
