package com.koujunxin.runtime.breakup;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.koujunxin.runtime.api.RuntimeFailure;
import com.koujunxin.runtime.ledger.CanonicalJson;

public final class BreakupCatalog {

    private final JsonNode asphyxia;
    private final JsonNode wall;
    private final JsonNode trialScript;
    private final JsonNode phoneLife;
    private final String appCss;
    private final String v4Css;
    private final String fontsCss;

    public BreakupCatalog() {
        this.asphyxia = readJson("works/breakup-editor/asphyxia.json");
        this.wall = readJson("works/breakup-editor/wall.json");
        this.trialScript = readJson("works/breakup-editor/server/trial-script.json");
        this.phoneLife = readJson("works/breakup-editor/phone-life.json");
        this.appCss = readText("works/breakup-editor/cdn/css/app.css");
        this.v4Css = readText("works/breakup-editor/cdn/css/v4.css");
        this.fontsCss = readText("works/breakup-editor/cdn/css/fonts.css");
    }

    public JsonNode asphyxia() {
        return asphyxia;
    }

    public JsonNode wall() {
        return wall;
    }

    public JsonNode trialScript() {
        return trialScript;
    }

    public JsonNode phoneLife(String coupleId, String side) {
        return phoneLife.path(coupleId).path(side);
    }

    public String appCss() {
        return appCss;
    }

    public String v4Css() {
        return v4Css;
    }

    public String fontsCss() {
        return fontsCss;
    }

    public ObjectNode coupleCopy() {
        return (ObjectNode) CanonicalJson.parse(CanonicalJson.write(asphyxia));
    }

    private static JsonNode readJson(String path) {
        return CanonicalJson.parse(readText(path));
    }

    private static String readText(String path) {
        try (InputStream in = BreakupCatalog.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                throw RuntimeFailure.notFound("CATALOG_MISSING", path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (RuntimeFailure ex) {
            throw ex;
        } catch (Exception ex) {
            throw RuntimeFailure.notFound("CATALOG_MISSING", path);
        }
    }
}
