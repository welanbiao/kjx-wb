# WP-31 聚合 / 依赖管理请求（集成 Agent）

本工作包**没有**修改 `backend/pom.xml`、`backend/kjx-modules/pom.xml`、`backend/kjx-server/**`、`contracts/**`、`docs/**`、`apps/**`、`deploy/**`，也**没有**改 WP-12 的目录 / 可见性 / 灰度实现（`WorkCatalog*` / `WorkVisibilityService` / `AppWorkController` / `AdminWorkController`）。下列事项请由集成 Agent 合入。

## 集成状态

主 Agent 已把 `WorkPackageAutoConfiguration` 登记进 AutoConfiguration.imports，上传限制写入 `application.yml`（50MB），Flyway 基线断言 V420–V424。本地对象键会拒绝绝对路径和 `..`。OpenAPI / 错误码仍未分配，保持本文件记录。

## 1. Flyway（必须）

WP-31 迁移在 `kjx-work` classpath，版本段 V420–V424（预留至 V449）：

| 版本 | 脚本 |
|---|---|
| V420 | `db/migration/V420__create_kjx_work_package.sql` |
| V421 | `db/migration/V421__create_kjx_asset.sql` |
| V422 | `db/migration/V422__create_kjx_music.sql` |
| V423 | `db/migration/V423__create_kjx_content_document.sql` |
| V424 | `db/migration/V424__create_kjx_oss_object.sql` |

保持：

```yaml
spring.flyway.locations: classpath:db/migration
```

不要使用 `classpath*:`。V400–V402 由 WP-12 所有；本包未改那些脚本。

## 2. `kjx-server` 装配

`scanBasePackages = com.koujunxin` 可扫到：

- `com.koujunxin.work.packagecontent.WorkPackageAutoConfiguration`
- `AdminWorkPackageController` `/admin-api/kjx/work/{workId}/versions/{version}/package`
- `AdminAssetController` `/admin-api/kjx/work/{workId}/versions/{version}/assets`
- `AdminMusicController` `/admin-api/kjx/music`

请确认：

1. 存在 `JdbcTemplate`、`ObjectMapper`、`WorkCatalogService`（WP-12）。
2. **不要**再注册第二套 `WorkVisibilityService` / 灰度实现。本包发布只写 `kjx_work_package.gray_ratio` 并调用已有 `activateWorkVersion`；作品主表 GRAY 状态仍走 WP-12 管理接口。
3. 把 `WorkPackageAutoConfiguration` 写入 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`（可选，组件扫描已能加载）。
4. 上传限制（建议在 `kjx-server` 配置，本包内仍有 50MB 流式二次校验）：

```yaml
spring.servlet.multipart.max-file-size: 50MB
spring.servlet.multipart.max-request-size: 50MB
```

5. 本地对象存储：默认内存实现 + HMAC 签名。可选环境变量 `KJX_WORK_OSS_HMAC_SECRET`（缺省 `local-dev-not-a-cloud-key`，**不是云密钥**）、`KJX_WORK_OSS_ROOT`（设置后改用本地目录）。禁止把密钥打进日志。
6. 真实阿里云 OSS / 腾讯云 COS 与 X-File-Storage 未接入，留给后续存储适配。

## 3. 契约缺口（未改 `contracts/`）

OpenAPI / `errors.yaml` 尚无作品包、素材、音乐路径。本包临时 HTTP 400 + `errorName`：

| errorName | HTTP | 含义 |
|---|---|---|
| `PACKAGE_TOO_LARGE` | 400 | zip 超过 50MB |
| `PACKAGE_TOO_MANY_FILES` | 400 | 文件数 > 500 |
| `PACKAGE_ZIP_SLIP` | 400 | 路径穿越 |
| `PACKAGE_ZIP_BOMB` | 400 | 压缩炸弹 |
| `PACKAGE_REJECTED` | 400 | 15 项机审失败（body.data 含报告） |
| `PACKAGE_NOT_FOUND` | 404 | 无此包 |
| `PACKAGE_NOT_VALIDATED` | 400 | 未通过机审不能预览/发布 |
| `PACKAGE_NO_PREVIOUS` | 400 | 无上一版本可回滚 |
| `SERVER_NOT_ON_CDN` | 400 | `server/` 禁止走 CDN 签名 |
| `MUSIC_LICENSE_REQUIRED` | 400 | 曲目缺版权 |

建议后续在 `contracts/openapi/paths/` 增加 admin 作品包/素材/音乐分片，并在 `errors.yaml` 分配业务码（当前未占用冻结码 1001–1090）。

未实现、需其他工作包：

- 定时发布、白名单账号灰度（可见性仍是 WP-12 `StableGrayBuckets`）。
- 沙箱宿主真正跑 Form A 渲染器（WP-42）。
- EXIF 剥离、隐形水印像素处理（缺 Thumbnailator / 图像库依赖，未改模块 POM）。
- 网络nt JSON Schema 校验器（父 POM 仅有版本号，本包用 Jackson 手写必填校验）。
- 文本合规的完整敏感词库（本包只拦 script / javascript: / eval）。

## 4. 给下游的端口

```text
com.koujunxin.work.packagecontent.WorkPackageService
com.koujunxin.work.asset.AssetService#sign
com.koujunxin.work.asset.ObjectStoragePort
com.koujunxin.work.music.MusicService#signTrack
com.koujunxin.work.content.ContentLibraryService
```

CDN 签名必须走 `StorageScope.CDN`；`server/` 对象键含 `/server/`，`CdnIsolation.rejectCdnKey` 会拒绝。

## 5. 独立验证

```text
mvn -f backend/pom.xml -pl kjx-modules/kjx-work -am test
```

Testcontainers MySQL 8.4；`disabledWithoutDocker = true`。
