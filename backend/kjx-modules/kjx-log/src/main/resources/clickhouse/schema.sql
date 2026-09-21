-- ClickHouse 分析投影。仅 ACCESS / AI_CALL / TOOL_CALL。
-- 不得用于权限、权益、余额或状态判断。event_id 用于重放去重。
CREATE DATABASE IF NOT EXISTS kjx;

CREATE TABLE IF NOT EXISTS kjx.log_access
(
    event_id      String,
    ts            DateTime64(3),
    trace_id      String,
    request_id    String,
    method        LowCardinality(String),
    path          String,
    query_keys    Array(String),
    status        UInt16,
    resp_bytes    UInt64,
    latency_ms    UInt32,
    rate_limited  UInt8,
    upstream_ms   UInt32,
    login_type    LowCardinality(String),
    member_id     UInt64,
    work_code     LowCardinality(String)
)
ENGINE = ReplacingMergeTree
PARTITION BY toYYYYMMDD(ts)
ORDER BY (event_id)
TTL toDateTime(ts) + INTERVAL 180 DAY;

CREATE TABLE IF NOT EXISTS kjx.log_ai_call
(
    event_id           String,
    ts                 DateTime64(3),
    trace_id           String,
    request_id         String,
    member_id          UInt64,
    work_id            LowCardinality(String),
    session_id         String,
    turn_id            String,
    channel            LowCardinality(String),
    provider           LowCardinality(String),
    model              LowCardinality(String),
    prompt_version_id  String,
    prompt_mode        LowCardinality(String),
    messages_count     UInt16,
    tool_defs_count    UInt16,
    tools_enabled      Array(String),
    stream             UInt8,
    temperature        Float32,
    max_tokens         UInt32,
    status             LowCardinality(String),
    error_code         LowCardinality(String),
    degrade_level      LowCardinality(String),
    retry_count        UInt8,
    latency_ms         UInt32,
    first_token_ms     UInt32,
    input_tokens       UInt32,
    output_tokens      UInt32,
    cached_tokens      UInt32,
    reasoning_tokens   UInt32,
    cost_amount        Decimal(18, 6),
    charge_amount      Decimal(18, 6)
)
ENGINE = ReplacingMergeTree
PARTITION BY toYYYYMMDD(ts)
ORDER BY (event_id)
TTL toDateTime(ts) + INTERVAL 180 DAY;

CREATE TABLE IF NOT EXISTS kjx.log_tool_call
(
    event_id      String,
    ts            DateTime64(3),
    trace_id      String,
    request_id    String,
    session_id    String,
    tool_name     LowCardinality(String),
    tool_version  String,
    tool_type     LowCardinality(String),
    impl_kind     LowCardinality(String),
    duration_ms   UInt32,
    ui_ops_count  UInt16,
    outcome       LowCardinality(String),
    reject_reason String,
    member_id     UInt64
)
ENGINE = ReplacingMergeTree
PARTITION BY toYYYYMMDD(ts)
ORDER BY (event_id)
TTL toDateTime(ts) + INTERVAL 180 DAY;
