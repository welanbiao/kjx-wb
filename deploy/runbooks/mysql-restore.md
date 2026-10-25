# MySQL 全量备份与新实例恢复（WP-51）

**备份成功不等于恢复成功。** 必须在**新实例**上实际导入、拉起 `kjx-server`，并核对 `flyway_schema_history`，才算一次有效演练。

本手册使用占位符，不写真实主机、密码或密钥。口令只从环境变量或密钥管理读取，不要贴进工单正文或 git。

当前开发栈的 MySQL 来自 `deploy/compose/docker-compose.dev.yml`：服务名 `mysql`，容器名 `kjx-mysql`，镜像 `mysql:8.4`，数据卷 `kjx_mysql_data`，端口只绑 `127.0.0.1:3306`。恢复目标必须是**另一套数据目录 / 另一个容器 / 另一台主机**，禁止对正在服役的卷做覆盖导入。

本手册**未在 staging 做过恢复演练**。

## 0. 占位符

| 占位符 | 含义 |
|---|---|
| `${MYSQL_HOST}` | 源库主机（服役实例） |
| `${MYSQL_PORT}` | 源库端口，开发栈常见 `3306` |
| `${MYSQL_USER}` | 具有备份权限的账号 |
| `${MYSQL_PASSWORD}` | 从环境变量读取，例如 `$env:MYSQL_PASSWORD` |
| `${MYSQL_DATABASE}` | 业务库名，Compose 缺省 `koujunxin` |
| `${BACKUP_DIR}` | 备份落地目录（不在仓库内提交 dump） |
| `${DUMP_FILE}` | 全量 dump 文件名 |
| `${BINLOG_INDEX}` | 记录 binlog 位点的文本 |
| `${RESTORE_HOST}` | **新实例**主机 |
| `${RESTORE_PORT}` | 新实例端口 |
| `${RESTORE_CONTAINER}` | 可选：新容器名，例如 `kjx-mysql-restore`，**不要**复用 `kjx-mysql` |
| `${RESTORE_VOLUME}` | 新数据卷，**不要**使用 `kjx_mysql_data` |
| `${PITR_STOP}` | 点位恢复截止时间（`YYYY-MM-DD HH:MM:SS`，时区 `Asia/Shanghai`） |

客户端需与源库大版本一致（8.4）。Compose 已打开 `log-bin-trust-function-creators`（账本触发器需要）；新实例同样打开。

## 1. 备份前准备

1. 确认源库可连接，且你不会在本步骤对业务库执行 DDL。
2. 确认二进制日志已开启（点位恢复依赖 binlog）。未开 binlog 则只能恢复到 dump 当时，不能做到任意时间点。
3. 准备空的 `${BACKUP_DIR}`，权限仅限操作人。

只读探活：

```bash
mysql --host="${MYSQL_HOST}" --port="${MYSQL_PORT}" --user="${MYSQL_USER}" --password="${MYSQL_PASSWORD}" --execute="SELECT 1"
```

## 2. 记录 binlog 位点，再打全量 dump

顺序固定：**先记下位点（或让 dump 自己嵌入位点），再导出**。不要先 dump 再补记一个更晚的位点。

MySQL 8.4 优先：

```bash
mysql --host="${MYSQL_HOST}" --port="${MYSQL_PORT}" --user="${MYSQL_USER}" --password="${MYSQL_PASSWORD}" --execute="SHOW BINARY LOG STATUS\G" > "${BACKUP_DIR}/${BINLOG_INDEX}"
```

若客户端仍只认旧语句，可用已弃用的 `SHOW MASTER STATUS\G`，但文件里要同时留下 `File`、`Position`（或 GTID 集合）。

InnoDB 热备（业务库不分区，与 docs/13 一致）：

```bash
mysqldump \
  --host="${MYSQL_HOST}" \
  --port="${MYSQL_PORT}" \
  --user="${MYSQL_USER}" \
  --password="${MYSQL_PASSWORD}" \
  --single-transaction \
  --routines \
  --triggers \
  --events \
  --default-character-set=utf8mb4 \
  --set-gtid-purged=OFF \
  --source-data=2 \
  --databases "${MYSQL_DATABASE}" \
  > "${BACKUP_DIR}/${DUMP_FILE}"
```

说明：

- `--single-transaction`：InnoDB 一致性快照，避免 `FLUSH TABLES WITH READ LOCK` 长时间锁库。
- `--source-data=2`：把 `CHANGE REPLICATION SOURCE TO` 写进 dump **注释**，与第 2 步的位点文件交叉核对。旧客户端若只支持 `--master-data=2`，语义相同，选一个并在记录里写明。
- 若源库启用 GTID：同时保存 `@@GLOBAL.GTID_EXECUTED`，点位恢复改用 `mysqlbinlog --include-gtids` / `--exclude-gtids`。

校验 dump 不是空文件，并确认注释中的 binlog 文件名、Position 与 `${BINLOG_INDEX}` 一致。这一步只能证明**备份文件写出来了**，不能代替第 4～6 节。

持续把源库 binlog 复制到备份介质（文件名从位点中的 `File` 起，含之后滚动的文件），直到宣布冻结写入或到达 `${PITR_STOP}`。

## 3. 拉起新实例（禁止覆盖服役库）

**禁止：**

- 对正在服役的 `kjx-mysql` 执行 `mysql < dump`
- `docker compose ... down -v`
- 把 dump 导入卷 `kjx_mysql_data`
- 用同一 `container_name: kjx-mysql` 再起一个覆盖进程

**允许的做法示例**（新卷、新容器名；仍用 `mysql:8.4`）：

```bash
docker volume create "${RESTORE_VOLUME}"

docker run -d --name "${RESTORE_CONTAINER}" \
  -e TZ=Asia/Shanghai \
  -e MYSQL_DATABASE="${MYSQL_DATABASE}" \
  -e MYSQL_ROOT_PASSWORD="${MYSQL_PASSWORD}" \
  -e MYSQL_USER="${MYSQL_USER}" \
  -e MYSQL_PASSWORD="${MYSQL_PASSWORD}" \
  -p 127.0.0.1:${RESTORE_PORT}:3306 \
  -v "${RESTORE_VOLUME}:/var/lib/mysql" \
  mysql:8.4 \
  --explicit-defaults-for-timestamp=ON \
  --character-set-server=utf8mb4 \
  --collation-server=utf8mb4_0900_ai_ci \
  --default-time-zone=+08:00 \
  --log-bin-trust-function-creators=1 \
  --slow-query-log=1 \
  --long-query-time=1
```

等待新实例健康后再导入。宿主机已有空库同理：新数据目录、新端口、新实例，不要指向源库。

## 4. 导入全量 dump

```bash
mysql \
  --host="${RESTORE_HOST}" \
  --port="${RESTORE_PORT}" \
  --user="${MYSQL_USER}" \
  --password="${MYSQL_PASSWORD}" \
  < "${BACKUP_DIR}/${DUMP_FILE}"
```

导入报错即失败，不要继续套 binlog。导入成功后立刻只读抽查表是否存在（至少 `flyway_schema_history`、`kjx_outbox_event`）。

## 5. 按 binlog 追到目标点（可选 PITR）

仅当需要 dump 之后、故障之前的增量时执行。从 dump 注释 / `${BINLOG_INDEX}` 的 `File` + `Position` 开始，应用到 `${PITR_STOP}`：

```bash
mysqlbinlog \
  --start-position="${START_POSITION}" \
  --stop-datetime="${PITR_STOP}" \
  "${BACKUP_DIR}/binlog/${BINLOG_FILE}" \
  | mysql \
      --host="${RESTORE_HOST}" \
      --port="${RESTORE_PORT}" \
      --user="${MYSQL_USER}" \
      --password="${MYSQL_PASSWORD}"
```

多文件按序号拼接。`--start-position` 只对列表中**第一个**文件有效。  
不要在源库上 replay。不要跳过 `flyway_schema_history` 的 binlog 事件。

## 6. 必须做的恢复验收（否则不算成功）

在**新实例**上：

```sql
SELECT installed_rank, version, description, script, checksum, success, installed_on
  FROM flyway_schema_history
 ORDER BY installed_rank;
```

核对：

1. 每一行 `success = 1`。
2. 版本集合覆盖发布记录中的预期版本（可与 `tests/wp51/check-flyway-versions.ps1` 扫描到的 `V*.sql`、以及 `FlywayBaselineIT` 断言的模块版本对照）。
3. 没有手工插入、没有 `baseline-on-migrate` 制造的空洞。
4. 抽查关键表能查（账号/会员/解锁/Outbox 视备份范围而定），字符集为 `utf8mb4` / `utf8mb4_0900_ai_ci`。

然后把一台**非生产流量**的 `kjx-server` 指向新实例（改该进程的 `SPRING_DATASOURCE_*`，不要改正在服役的进程）：

```bash
# 仍启动 kjx-server 模块；数据源指向 RESTORE_* 而不是服役库
mvn -B -ntp -pl kjx-server -am spring-boot:run
```

`GET /actuator/health` 通过，且启动日志显示 Flyway **没有**把新实例当成空库再跑一遍 baseline（history 已在）。若 Flyway 试图重跑已存在版本，停止并把实例视为损坏，查 dump 是否漏了 `flyway_schema_history`。

以上全部完成，才能在恢复报告里写「恢复成功」。只看到 dump 文件很大、或源库 `mysqldump` 退出码为 0，只能写「备份文件已生成」。

## 7. 切流（仅演练通过后）

1. 源库停止写入（或保持只读）。
2. 再追一次 binlog 到冻结点，复检 `flyway_schema_history`。
3. 将 `kjx-server`、以及如需的 `web` / `admin` / `nginx` 切到新实例连接串。
4. 观察 Outbox 积压（`deploy/runbooks/outbox-replay.md`）。
5. 源库先保留，确认稳定后再下线。失败则把应用指回源库（源库未被覆盖时这才可能）。

## 8. 本手册未覆盖

- Redis AOF（开发栈卷名 `kjx_redis_data`）与对象存储版本回滚：WP-51 范围内另做，不在本文件用 MySQL dump 代替。
- ClickHouse：分析投影，不得当作权限或账本真相；见 `deploy/observability/README.md`。
- 在服役实例上“原地覆盖”任何步骤。
