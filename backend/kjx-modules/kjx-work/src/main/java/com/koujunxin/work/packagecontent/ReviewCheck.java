package com.koujunxin.work.packagecontent;

/**
 * 规格 §9.4 十五项机审。
 */
public enum ReviewCheck {
    ZIP_INTEGRITY,
    MANIFEST_SCHEMA,
    RUNTIME_COMPAT,
    DIRECTORY_COMPLETE,
    CHECKSUMS,
    ASSET_LIMITS,
    SVG_SAFE,
    NO_EXTERNAL_URL,
    SERVER_CDN_ISOLATION,
    PROTOCOL_SCHEMA,
    TOOL_NAMES,
    CONTENT_SCHEMA,
    PERMISSIONS,
    TEXT_COMPLIANCE,
    ADVISORY
}
