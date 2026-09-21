package com.koujunxin.work.packagecontent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.koujunxin.work.asset.SvgThreatScanner;
import com.koujunxin.work.content.ContentSchemaValidator;

public final class WorkPackageInspector {

    private static final Pattern SEMVER = Pattern.compile("(\\d+)\\.(\\d+)\\.(\\d+)");
    private static final Set<String> BUILTIN_TOOLS = Set.of(
            "moments.post", "contact.add", "profile.update",
            "chat.send", "chat.recall", "chat.read_receipt", "chat.voice", "chat.call",
            "redpacket.send", "transfer.send", "wallet.adjust",
            "order.food", "order.gift", "order.status",
            "music.play", "music.select", "music.pause", "music.resume", "music.stop",
            "music.volume", "music.playlist.set", "music.share", "music.listen_together",
            "photo.add", "memo.add", "game.start", "weather.get", "time.get",
            "session.end", "save.checkpoint", "get_ending", "get_secret", "check_condition",
            "get_asset", "schedule_event");

    private static final Set<String> BITMAP = Set.of(".png", ".jpg", ".jpeg", ".webp");
    private static final Set<String> SFX = Set.of(".mp3", ".ogg");
    private static final Set<String> FONT = Set.of(".woff2");
    private static final Set<String> VIDEO = Set.of(".mp4", ".webm");

    private final ObjectMapper mapper;
    private final WorkPackageLimits limits;

    public WorkPackageInspector(ObjectMapper mapper, WorkPackageLimits limits) {
        this.mapper = mapper;
        this.limits = limits;
    }

    public Inspection inspect(Path root, List<String> files, String expectedWorkCode, String expectedVersion)
            throws IOException {
        List<ReviewFinding> findings = new ArrayList<>();
        findings.add(ReviewFinding.pass(ReviewCheck.ZIP_INTEGRITY, "zip extracted to isolated directory"));

        ManifestDocument manifest = null;
        JsonNode manifestNode = null;
        Path manifestPath = IsolatedZipExtractor.safeResolve(root, "manifest.json");
        if (!Files.isRegularFile(manifestPath)) {
            findings.add(ReviewFinding.fail(ReviewCheck.MANIFEST_SCHEMA, "manifest.json missing", "manifest.json"));
        } else {
            try {
                manifestNode = mapper.readTree(Files.readString(manifestPath, StandardCharsets.UTF_8));
                List<String> errors = validateManifest(manifestNode, expectedWorkCode, expectedVersion);
                if (errors.isEmpty()) {
                    manifest = toManifest(manifestNode);
                    findings.add(ReviewFinding.pass(ReviewCheck.MANIFEST_SCHEMA, "manifest schema ok"));
                } else {
                    findings.add(ReviewFinding.fail(ReviewCheck.MANIFEST_SCHEMA, String.join("; ", errors),
                            "manifest.json"));
                }
            } catch (Exception ex) {
                findings.add(ReviewFinding.fail(ReviewCheck.MANIFEST_SCHEMA, "manifest.json is not valid JSON",
                        "manifest.json"));
            }
        }

        findings.add(runtimeCompat(manifest));
        findings.add(directories(root, manifest));
        findings.add(checksums(root, files));
        findings.add(assetLimits(root, files));
        findings.add(svgSafe(root, files));
        findings.add(externalLinks(root, files));
        findings.add(cdnIsolation(files));
        findings.add(protocolSchema(root));
        findings.add(toolNames(root, manifest));
        findings.add(contentSchema(root, manifest));
        findings.add(permissions(manifest));
        findings.add(textCompliance(root, files));
        findings.add(advisory(root, manifest));

        return new Inspection(ValidationReport.of(findings), manifest, files);
    }

    private ReviewFinding runtimeCompat(ManifestDocument manifest) {
        if (manifest == null || manifest.runtimeCompat() == null) {
            return ReviewFinding.fail(ReviewCheck.RUNTIME_COMPAT, "runtimeCompat missing", "manifest.json");
        }
        if (!compatible(manifest.runtimeCompat(), limits.platformRuntimeVersion())) {
            return ReviewFinding.fail(ReviewCheck.RUNTIME_COMPAT,
                    "runtime " + limits.platformRuntimeVersion() + " not in " + manifest.runtimeCompat(),
                    "manifest.runtimeCompat");
        }
        return ReviewFinding.pass(ReviewCheck.RUNTIME_COMPAT, "runtime compatible");
    }

    private ReviewFinding directories(Path root, ManifestDocument manifest) {
        if (manifest == null) {
            return ReviewFinding.fail(ReviewCheck.DIRECTORY_COMPLETE, "cannot check directories without manifest",
                    "manifest.json");
        }
        List<String> missing = new ArrayList<>();
        requireFile(root, "checksums.json", missing);
        requireFile(root, manifest.layout() == null ? "theme/layout.json" : manifest.layout(), missing);
        requireFile(root, manifest.tokens() == null ? "theme/tokens.json" : manifest.tokens(), missing);
        requireFile(root, "content/nodes.json", missing);
        requireFile(root, "assets/assets.manifest.json", missing);
        if ("B".equals(manifest.form()) || "C".equals(manifest.form())) {
            if (manifest.entry() == null || manifest.entry().isBlank()) {
                missing.add("entry");
            } else {
                requireFile(root, manifest.entry(), missing);
            }
        }
        if (!missing.isEmpty()) {
            return ReviewFinding.fail(ReviewCheck.DIRECTORY_COMPLETE, "missing " + missing, missing.get(0));
        }
        return ReviewFinding.pass(ReviewCheck.DIRECTORY_COMPLETE, "required directories present");
    }

    private ReviewFinding checksums(Path root, List<String> files) throws IOException {
        Path checksums = IsolatedZipExtractor.safeResolve(root, "checksums.json");
        if (!Files.isRegularFile(checksums)) {
            return ReviewFinding.fail(ReviewCheck.CHECKSUMS, "checksums.json missing", "checksums.json");
        }
        JsonNode node = mapper.readTree(Files.readString(checksums, StandardCharsets.UTF_8));
        JsonNode mapNode = node.has("files") ? node.get("files") : node;
        if (mapNode == null || !mapNode.isObject()) {
            return ReviewFinding.fail(ReviewCheck.CHECKSUMS, "checksums.json must be a path-to-sha256 map",
                    "checksums.json");
        }
        Set<String> listed = new HashSet<>();
        List<String> mismatches = new ArrayList<>();
        Iterator<Map.Entry<String, JsonNode>> fields = mapNode.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            String path = field.getKey().replace('\\', '/');
            listed.add(path);
            Path file = IsolatedZipExtractor.safeResolve(root, path);
            if (!Files.isRegularFile(file)) {
                mismatches.add(path + " missing");
                continue;
            }
            String actual = StreamingUploads.hex(file);
            String expected = field.getValue().asText("").toLowerCase(Locale.ROOT);
            if (!actual.equalsIgnoreCase(expected)) {
                mismatches.add(path);
            }
        }
        for (String file : files) {
            if ("checksums.json".equals(file)) {
                continue;
            }
            if (!listed.contains(file)) {
                mismatches.add("unlisted:" + file);
            }
        }
        if (!mismatches.isEmpty()) {
            return ReviewFinding.fail(ReviewCheck.CHECKSUMS, "checksum mismatch " + mismatches, mismatches.get(0));
        }
        return ReviewFinding.pass(ReviewCheck.CHECKSUMS, "checksums match");
    }

    private ReviewFinding assetLimits(Path root, List<String> files) throws IOException {
        List<String> bad = new ArrayList<>();
        for (String relative : files) {
            if (!relative.startsWith("assets/") || relative.equals("assets/assets.manifest.json")) {
                continue;
            }
            Path file = IsolatedZipExtractor.safeResolve(root, relative);
            long size = Files.size(file);
            String lower = relative.toLowerCase(Locale.ROOT);
            String ext = ext(lower);
            if (BITMAP.contains(ext) && size > limits.maxBitmapBytes()) {
                bad.add(relative + " bitmap>" + limits.maxBitmapBytes());
            } else if (".svg".equals(ext) && size > limits.maxSvgBytes()) {
                bad.add(relative + " svg>" + limits.maxSvgBytes());
            } else if (SFX.contains(ext) && size > limits.maxSfxBytes()) {
                bad.add(relative + " sfx>" + limits.maxSfxBytes());
            } else if (FONT.contains(ext) && size > limits.maxFontBytes()) {
                bad.add(relative + " font>" + limits.maxFontBytes());
            } else if (VIDEO.contains(ext) && size > limits.maxVideoBytes()) {
                bad.add(relative + " video>" + limits.maxVideoBytes());
            } else if (!BITMAP.contains(ext) && !".svg".equals(ext) && !SFX.contains(ext) && !FONT.contains(ext)
                    && !VIDEO.contains(ext) && !lower.endsWith(".json")) {
                bad.add(relative + " unsupported type");
            }
        }
        if (!bad.isEmpty()) {
            return ReviewFinding.fail(ReviewCheck.ASSET_LIMITS, String.join("; ", bad), bad.get(0));
        }
        return ReviewFinding.pass(ReviewCheck.ASSET_LIMITS, "asset limits ok");
    }

    private ReviewFinding svgSafe(Path root, List<String> files) throws IOException {
        List<String> bad = new ArrayList<>();
        for (String relative : files) {
            if (!SvgThreatScanner.isSvg(relative)) {
                continue;
            }
            String svg = Files.readString(IsolatedZipExtractor.safeResolve(root, relative), StandardCharsets.UTF_8);
            List<String> threats = SvgThreatScanner.threats(svg);
            if (!threats.isEmpty()) {
                bad.add(relative + " " + threats);
            }
        }
        if (!bad.isEmpty()) {
            return ReviewFinding.fail(ReviewCheck.SVG_SAFE, "unsafe svg " + bad, bad.get(0));
        }
        return ReviewFinding.pass(ReviewCheck.SVG_SAFE, "svg safe");
    }

    private ReviewFinding externalLinks(Path root, List<String> files) throws IOException {
        List<String> hits = ExternalLinkScanner.scan(root, files);
        if (!hits.isEmpty()) {
            return ReviewFinding.fail(ReviewCheck.NO_EXTERNAL_URL, "external url " + hits, hits.get(0));
        }
        return ReviewFinding.pass(ReviewCheck.NO_EXTERNAL_URL, "no external urls");
    }

    private ReviewFinding cdnIsolation(List<String> files) {
        for (String file : CdnIsolation.cdnFiles(files)) {
            if (CdnIsolation.isServerPath(file) || file.toLowerCase(Locale.ROOT).contains("/server/")) {
                return ReviewFinding.fail(ReviewCheck.SERVER_CDN_ISOLATION,
                        "server path leaked into CDN set: " + file, file);
            }
        }
        return ReviewFinding.pass(ReviewCheck.SERVER_CDN_ISOLATION, "server/ isolated from CDN");
    }

    private ReviewFinding protocolSchema(Path root) throws IOException {
        Path schema = IsolatedZipExtractor.safeResolve(root, "server/protocol.schema.json");
        if (!Files.isRegularFile(schema)) {
            return ReviewFinding.pass(ReviewCheck.PROTOCOL_SCHEMA, "no protocol schema (optional for Form A)");
        }
        JsonNode node = mapper.readTree(Files.readString(schema, StandardCharsets.UTF_8));
        if (!node.isObject() || !node.has("type")) {
            return ReviewFinding.fail(ReviewCheck.PROTOCOL_SCHEMA, "protocol.schema.json must be an object schema",
                    "server/protocol.schema.json");
        }
        return ReviewFinding.pass(ReviewCheck.PROTOCOL_SCHEMA, "protocol schema present");
    }

    private ReviewFinding toolNames(Path root, ManifestDocument manifest) throws IOException {
        List<String> names = new ArrayList<>();
        if (manifest != null && manifest.tools() != null) {
            names.addAll(manifest.tools());
        }
        Path tools = IsolatedZipExtractor.safeResolve(root, "server/tools.json");
        if (Files.isRegularFile(tools)) {
            JsonNode node = mapper.readTree(Files.readString(tools, StandardCharsets.UTF_8));
            if (node.isArray()) {
                for (JsonNode item : node) {
                    if (item.hasNonNull("name")) {
                        names.add(item.get("name").asText());
                    }
                }
            } else if (node.has("tools") && node.get("tools").isArray()) {
                for (JsonNode item : node.get("tools")) {
                    if (item.hasNonNull("name")) {
                        names.add(item.get("name").asText());
                    }
                }
            }
        }
        Set<String> seen = new HashSet<>();
        List<String> dup = new ArrayList<>();
        for (String name : names) {
            if (!seen.add(name)) {
                dup.add(name);
            }
        }
        List<String> collide = names.stream()
                .filter(name -> !BUILTIN_TOOLS.contains(name) && BUILTIN_TOOLS.contains(name))
                .toList();
        // custom tools that reuse builtin identifiers while also appearing twice already caught;
        // additionally reject custom objects that duplicate builtin AND appear in tools.json with type=custom
        if (Files.isRegularFile(tools)) {
            JsonNode node = mapper.readTree(Files.readString(tools, StandardCharsets.UTF_8));
            JsonNode arr = node.isArray() ? node : node.get("tools");
            if (arr != null && arr.isArray()) {
                for (JsonNode item : arr) {
                    String name = item.path("name").asText("");
                    String type = item.path("type").asText("");
                    if ("custom".equals(type) && BUILTIN_TOOLS.contains(name)) {
                        collide = List.of(name);
                    }
                }
            }
        }
        if (!dup.isEmpty()) {
            return ReviewFinding.fail(ReviewCheck.TOOL_NAMES, "duplicate tool names " + dup, dup.get(0));
        }
        if (!collide.isEmpty()) {
            return ReviewFinding.fail(ReviewCheck.TOOL_NAMES, "custom tool collides with builtin " + collide,
                    collide.get(0));
        }
        return ReviewFinding.pass(ReviewCheck.TOOL_NAMES, "tool names unique");
    }

    private ReviewFinding contentSchema(Path root, ManifestDocument manifest) throws IOException {
        List<String> errors = ContentSchemaValidator.validate(root, mapper);
        if (!errors.isEmpty()) {
            return ReviewFinding.fail(ReviewCheck.CONTENT_SCHEMA, String.join("; ", errors), errors.get(0));
        }
        return ReviewFinding.pass(ReviewCheck.CONTENT_SCHEMA, "content schema ok");
    }

    private ReviewFinding permissions(ManifestDocument manifest) {
        if (manifest == null) {
            return ReviewFinding.fail(ReviewCheck.PERMISSIONS, "permissions missing", "manifest.json");
        }
        List<String> bad = manifest.permissions().stream()
                .filter(item -> !ManifestDocument.PERMISSIONS.contains(item))
                .toList();
        if (!bad.isEmpty()) {
            return ReviewFinding.fail(ReviewCheck.PERMISSIONS, "unknown permissions " + bad, "manifest.permissions");
        }
        if (manifest.permissions().isEmpty()) {
            return ReviewFinding.fail(ReviewCheck.PERMISSIONS, "permissions must not be empty", "manifest.permissions");
        }
        return ReviewFinding.pass(ReviewCheck.PERMISSIONS, "permissions ok");
    }

    private ReviewFinding textCompliance(Path root, List<String> files) throws IOException {
        Pattern banned = Pattern.compile("(?is)<\\s*script\\b|javascript:|eval\\s*\\(");
        List<String> hits = new ArrayList<>();
        for (String relative : files) {
            if (!ExternalLinkScanner.isTextLike(relative) || SvgThreatScanner.isSvg(relative)) {
                continue;
            }
            String text = Files.readString(IsolatedZipExtractor.safeResolve(root, relative), StandardCharsets.UTF_8);
            if (banned.matcher(text).find()) {
                hits.add(relative);
            }
        }
        if (!hits.isEmpty()) {
            return ReviewFinding.fail(ReviewCheck.TEXT_COMPLIANCE, "unsafe text in " + hits, hits.get(0));
        }
        return ReviewFinding.pass(ReviewCheck.TEXT_COMPLIANCE, "text compliance ok");
    }

    private ReviewFinding advisory(Path root, ManifestDocument manifest) {
        List<String> warns = new ArrayList<>();
        if (manifest != null && (manifest.music() == null || manifest.music().isBlank())) {
            warns.add("music.json not declared");
        }
        if (!Files.isRegularFile(IsolatedZipExtractor.safeResolve(root, "server/prompts/system.md"))) {
            warns.add("server/prompts/system.md missing");
        }
        if (warns.isEmpty()) {
            return ReviewFinding.pass(ReviewCheck.ADVISORY, "no advisories");
        }
        return ReviewFinding.warn(ReviewCheck.ADVISORY, String.join("; ", warns), "advisory");
    }

    private List<String> validateManifest(JsonNode node, String expectedWorkCode, String expectedVersion) {
        List<String> errors = new ArrayList<>();
        if (!node.isObject()) {
            errors.add("manifest must be object");
            return errors;
        }
        requireText(node, "id", errors);
        requireText(node, "name", errors);
        requireText(node, "type", errors);
        requireText(node, "version", errors);
        requireText(node, "form", errors);
        requireText(node, "runtimeCompat", errors);
        requireText(node, "contentVersion", errors);
        requireText(node, "layout", errors);
        requireText(node, "tokens", errors);
        requireText(node, "promptMode", errors);
        if (!node.has("permissions") || !node.get("permissions").isArray()) {
            errors.add("permissions must be array");
        }
        if (!node.has("allowedChannels") || !node.get("allowedChannels").isArray()) {
            errors.add("allowedChannels must be array");
        }
        if (!node.has("tools") || !node.get("tools").isArray()) {
            errors.add("tools must be array");
        }
        if (!node.has("audit") || !node.get("audit").isObject()) {
            errors.add("audit must be object");
        } else {
            JsonNode audit = node.get("audit");
            if (!audit.hasNonNull("copyright") || !audit.hasNonNull("contentRating") || !audit.hasNonNull("source")) {
                errors.add("audit requires copyright/contentRating/source");
            }
        }
        String type = node.path("type").asText("");
        if (!type.isEmpty() && !ManifestDocument.TYPES.contains(type)) {
            errors.add("type must be layout|game");
        }
        if ("game".equals(type) && (!node.has("apps") || !node.get("apps").isArray() || node.get("apps").isEmpty())) {
            errors.add("apps required for game");
        }
        String form = node.path("form").asText("");
        if (!form.isEmpty() && !ManifestDocument.FORMS.contains(form)) {
            errors.add("form must be A|B|C");
        }
        String prompt = node.path("promptMode").asText("");
        if (!prompt.isEmpty() && !ManifestDocument.PROMPT_MODES.contains(prompt)) {
            errors.add("promptMode invalid");
        }
        if (expectedWorkCode != null && node.hasNonNull("id") && !expectedWorkCode.equals(node.get("id").asText())) {
            errors.add("manifest.id must match workCode");
        }
        if (expectedVersion != null && node.hasNonNull("version")
                && !expectedVersion.equals(node.get("version").asText())) {
            errors.add("manifest.version must match work version");
        }
        if (node.has("allowedChannels") && node.get("allowedChannels").isArray()) {
            for (JsonNode ch : node.get("allowedChannels")) {
                if (!ManifestDocument.CHANNELS.contains(ch.asText())) {
                    errors.add("allowedChannels invalid");
                    break;
                }
            }
        }
        return errors;
    }

    private ManifestDocument toManifest(JsonNode node) {
        return new ManifestDocument(
                text(node, "id"),
                text(node, "name"),
                text(node, "type"),
                text(node, "version"),
                text(node, "form"),
                text(node, "runtimeCompat"),
                text(node, "entry"),
                text(node, "contentVersion"),
                text(node, "layout"),
                text(node, "tokens"),
                text(node, "music"),
                strings(node.get("permissions")),
                strings(node.get("allowedChannels")),
                text(node, "promptMode"),
                strings(node.get("apps")),
                strings(node.get("tools")),
                node.get("audit"),
                node.get("budget"));
    }

    static boolean compatible(String range, String runtime) {
        int[] current = parseSemver(runtime);
        if (current == null) {
            return false;
        }
        Matcher matcher = Pattern.compile("(>=|>|<=|<|=)?\\s*(\\d+\\.\\d+\\.\\d+)").matcher(range);
        boolean any = false;
        while (matcher.find()) {
            any = true;
            String op = matcher.group(1) == null ? ">=" : matcher.group(1);
            int[] bound = parseSemver(matcher.group(2));
            int cmp = compare(current, bound);
            boolean ok = switch (op) {
                case ">" -> cmp > 0;
                case ">=" -> cmp >= 0;
                case "<" -> cmp < 0;
                case "<=" -> cmp <= 0;
                default -> cmp == 0;
            };
            if (!ok) {
                return false;
            }
        }
        return any;
    }

    private static int[] parseSemver(String value) {
        Matcher matcher = SEMVER.matcher(value);
        if (!matcher.find()) {
            return null;
        }
        return new int[] {
                Integer.parseInt(matcher.group(1)),
                Integer.parseInt(matcher.group(2)),
                Integer.parseInt(matcher.group(3))
        };
    }

    private static int compare(int[] a, int[] b) {
        for (int i = 0; i < 3; i++) {
            int c = Integer.compare(a[i], b[i]);
            if (c != 0) {
                return c;
            }
        }
        return 0;
    }

    private static void requireFile(Path root, String relative, List<String> missing) {
        Path file = IsolatedZipExtractor.safeResolve(root, relative);
        if (!Files.isRegularFile(file)) {
            missing.add(relative);
        }
    }

    private static void requireText(JsonNode node, String field, List<String> errors) {
        if (!node.hasNonNull(field) || !node.get(field).isTextual() || node.get(field).asText().isBlank()) {
            errors.add(field + " required");
        }
    }

    private static String text(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.get(field).asText() : null;
    }

    private static List<String> strings(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (JsonNode item : node) {
            values.add(item.asText());
        }
        return List.copyOf(values);
    }

    private static String ext(String path) {
        int dot = path.lastIndexOf('.');
        return dot < 0 ? "" : path.substring(dot);
    }

    public record Inspection(ValidationReport report, ManifestDocument manifest, List<String> files) {
        public Map<String, Object> asMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("passed", report.passed());
            map.put("findings", report.findings().stream().map(finding -> Map.of(
                    "check", finding.check().name(),
                    "status", finding.status().name(),
                    "message", finding.message(),
                    "locator", finding.locator() == null ? "" : finding.locator()
            )).toList());
            return map;
        }
    }
}
