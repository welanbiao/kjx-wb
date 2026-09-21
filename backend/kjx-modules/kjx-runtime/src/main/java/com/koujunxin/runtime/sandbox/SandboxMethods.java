package com.koujunxin.runtime.sandbox;

import java.util.Set;

/**
 * Form B/C 宿主方法白名单。iframe 不得直连模型或读取宿主凭据。
 */
public final class SandboxMethods {

    public static final Set<String> HOST_METHODS = Set.of(
            "ai.complete",
            "state.save",
            "state.load",
            "assets.sign",
            "events.emit",
            "events.schedule",
            "metrics.report",
            "user.profile",
            "tools.invoke");

    public static final String IFRAME_SANDBOX = "allow-scripts allow-forms";

    private SandboxMethods() {
    }

    public static boolean allowed(String method) {
        return method != null && HOST_METHODS.contains(method);
    }
}
