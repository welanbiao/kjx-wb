package com.koujunxin.tool.registry;

import java.util.Optional;

import com.koujunxin.tool.api.ToolSpec;

public interface ToolCatalog {

    Optional<ToolSpec> find(String workCode, String toolCode, String version);

    void register(ToolRegistration registration);

    void bindWork(String workCode, String toolCode, String version, boolean enabled);
}
