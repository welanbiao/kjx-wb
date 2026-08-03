#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
SQL Server CREATE TABLE → PostgreSQL 转换脚本 (Python 3.12.1)

读取 D:\\221sql.txt（可含多条建表语句），按 CREATE TABLE 拆分，
转换为 PostgreSQL 可执行 SQL，追加写入 D:\\221pgsql.txt。

输出文件仅包含 SQL；表之间空行分隔。
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

INPUT_PATH = Path(r"D:\221sql.txt")
OUTPUT_PATH = Path(r"D:\221pgsql.txt")
SCHEMA = "mes_ats_szb0"


def split_create_tables(sql_text: str) -> list[str]:
    text = sql_text.replace("\r\n", "\n").replace("\r", "\n")
    text = re.sub(r"(?im)^\s*GO\s*;?\s*$", "\n", text)
    matches = list(re.finditer(r"(?i)\bCREATE\s+TABLE\b", text))
    if not matches:
        return []
    out: list[str] = []
    for i, m in enumerate(matches):
        start = m.start()
        end = matches[i + 1].start() if i + 1 < len(matches) else len(text)
        stmt = text[start:end].strip().rstrip(";").strip()
        if stmt:
            out.append(stmt)
    return out


def strip_brackets(name: str) -> str:
    return re.sub(r"[\[\]]", "", name).strip()


def normalize_ident(name: str) -> str:
    name = strip_brackets(name)
    if "." in name:
        name = name.split(".")[-1]
    return name.lower()


def find_matching_paren(s: str, open_idx: int) -> int:
    depth = 0
    in_str = False
    quote = ""
    i = open_idx
    while i < len(s):
        c = s[i]
        if in_str:
            if c == quote:
                if i + 1 < len(s) and s[i + 1] == quote:
                    i += 2
                    continue
                in_str = False
            i += 1
            continue
        if c in ("'", '"'):
            in_str = True
            quote = c
            i += 1
            continue
        if c == "(":
            depth += 1
        elif c == ")":
            depth -= 1
            if depth == 0:
                return i
        i += 1
    return -1


def split_top_level(body: str, sep: str = ",") -> list[str]:
    parts: list[str] = []
    buf: list[str] = []
    depth = 0
    in_str = False
    quote = ""
    i = 0
    while i < len(body):
        c = body[i]
        if in_str:
            buf.append(c)
            if c == quote:
                if i + 1 < len(body) and body[i + 1] == quote:
                    buf.append(body[i + 1])
                    i += 2
                    continue
                in_str = False
            i += 1
            continue
        if c in ("'", '"'):
            in_str = True
            quote = c
            buf.append(c)
            i += 1
            continue
        if c == "(":
            depth += 1
            buf.append(c)
        elif c == ")":
            depth -= 1
            buf.append(c)
        elif c == sep and depth == 0:
            part = "".join(buf).strip()
            if part:
                parts.append(part)
            buf = []
        else:
            buf.append(c)
        i += 1
    part = "".join(buf).strip()
    if part:
        parts.append(part)
    return parts


def map_data_type(type_sql: str) -> str:
    t = strip_brackets(type_sql.strip())
    t = re.sub(r"\s+", " ", t)
    t = re.sub(r"(?i)\bIDENTITY\s*(?:\([^)]*\))?", "", t).strip()
    t = re.sub(r"\s+", " ", t)

    m = re.match(r"(?i)^([a-z0-9_]+)(?:\s*\(([^)]*)\))?", t)
    if not m:
        return t.lower()

    base = m.group(1).lower()
    args = (m.group(2) or "").strip()

    if base in ("nvarchar", "varchar", "nchar", "char"):
        out_base = "varchar" if base in ("nvarchar", "varchar") else "char"
        if not args or args.upper() == "MAX":
            return "text"
        return f"{out_base}({args})"

    if base in ("decimal", "numeric"):
        return f"numeric({args})" if args else "numeric"

    mapping = {
        "int": "integer",
        "integer": "integer",
        "bigint": "bigint",
        "smallint": "smallint",
        "tinyint": "smallint",
        "bit": "boolean",
        "float": "double precision",
        "real": "real",
        "money": "numeric(19,4)",
        "smallmoney": "numeric(10,4)",
        "datetime": "timestamp",
        "datetime2": "timestamp",
        "smalldatetime": "timestamp",
        "date": "date",
        "time": "time",
        "datetimeoffset": "timestamptz",
        "uniqueidentifier": "varchar(36)",
        "text": "text",
        "ntext": "text",
        "xml": "xml",
        "image": "bytea",
        "varbinary": "bytea",
        "binary": "bytea",
        "sysname": "varchar(128)",
    }
    return mapping.get(base, base)


def convert_default(expr: str) -> str:
    e = expr.strip()
    # 反复去掉成对括号
    while True:
        e2 = e.strip()
        if len(e2) >= 2 and e2[0] == "(" and find_matching_paren(e2, 0) == len(e2) - 1:
            e = e2[1:-1].strip()
            continue
        e = e2
        break

    e = strip_brackets(e)
    low = e.lower()
    if low in ("getdate()", "sysdatetime()", "current_timestamp", "getutcdate()"):
        return "CURRENT_TIMESTAMP"
    if low in ("newid()", "newsequentialid()"):
        return "gen_random_uuid()::text"
    if (e.startswith("N'") or e.startswith("n'")) and e.endswith("'"):
        return "'" + e[2:-1] + "'"
    if e.startswith("'") and e.endswith("'"):
        return e
    if re.fullmatch(r"-?\d+(\.\d+)?", e):
        return e
    return e


def extract_table_name(stmt: str) -> str:
    m = re.search(
        r"(?is)CREATE\s+TABLE\s+((?:\[[^\]]+\]|[A-Za-z0-9_]+)(?:\.(?:\[[^\]]+\]|[A-Za-z0-9_]+))?)",
        stmt,
    )
    if not m:
        raise ValueError("无法解析表名")
    return normalize_ident(m.group(1))


def is_constraint_def(part: str) -> bool:
    return bool(
        re.match(
            r"(?is)^\s*(CONSTRAINT\b|PRIMARY\s+KEY\b|UNIQUE\b|FOREIGN\s+KEY\b|CHECK\b|INDEX\b)",
            part,
        )
    )


def parse_table_pk(parts: list[str]) -> list[str]:
    for part in parts:
        m = re.search(
            r"(?is)PRIMARY\s+KEY(?:\s+(?:CLUSTERED|NONCLUSTERED))?\s*\(([^)]+)\)",
            part,
        )
        if m:
            cols = []
            for raw in split_top_level(m.group(1)):
                col = re.sub(r"(?i)\s+(ASC|DESC)\b", "", raw).strip()
                cols.append(normalize_ident(col))
            return cols
    return []


def take_default(rest: str) -> tuple[str | None, str]:
    """从列定义剩余部分提取 DEFAULT，返回 (default_sql, rest_without_default)。"""
    m = re.search(r"(?is)\bDEFAULT\b", rest)
    if not m:
        return None, rest
    i = m.end()
    while i < len(rest) and rest[i].isspace():
        i += 1
    if i >= len(rest):
        return None, rest[: m.start()].strip()

    if rest[i] == "(":
        j = find_matching_paren(rest, i)
        if j < 0:
            raise ValueError(f"DEFAULT 括号不匹配: {rest}")
        expr = rest[i : j + 1]
        new_rest = (rest[: m.start()] + " " + rest[j + 1 :]).strip()
        return convert_default(expr), new_rest

    if rest.startswith("N'", i) or rest.startswith("n'", i):
        j = i + 2
        while j < len(rest):
            if rest[j] == "'":
                if j + 1 < len(rest) and rest[j + 1] == "'":
                    j += 2
                    continue
                j += 1
                break
            j += 1
        expr = rest[i:j]
        new_rest = (rest[: m.start()] + " " + rest[j:]).strip()
        return convert_default(expr), new_rest

    if rest[i] == "'":
        j = i + 1
        while j < len(rest):
            if rest[j] == "'":
                if j + 1 < len(rest) and rest[j + 1] == "'":
                    j += 2
                    continue
                j += 1
                break
            j += 1
        expr = rest[i:j]
        new_rest = (rest[: m.start()] + " " + rest[j:]).strip()
        return convert_default(expr), new_rest

    mm = re.match(
        r"(?is)(.+?)(?=\s+(?:NOT\s+NULL|NULL|PRIMARY\s+KEY|UNIQUE|CHECK|CONSTRAINT|ROWGUIDCOL)\b|$)",
        rest[i:],
    )
    expr = mm.group(1).strip() if mm else rest[i:].strip()
    new_rest = (rest[: m.start()] + " " + rest[i + len(expr) :]).strip()
    return convert_default(expr), new_rest


def convert_column(
    part: str,
    table_name: str,
    pk_cols: set[str],
) -> tuple[str | None, str | None, str | None]:
    """
    Returns (column_sql, sequence_sql, col_name)
    """
    if is_constraint_def(part):
        return None, None, None

    m = re.match(r"(?is)^\s*((?:\[[^\]]+\]|[A-Za-z0-9_#]+))\s+(.+)$", part)
    if not m:
        return None, None, None

    col_name = normalize_ident(m.group(1))
    rest = m.group(2).strip()

    is_identity = bool(re.search(r"(?i)\bIDENTITY\b", rest))
    col_pk = bool(re.search(r"(?i)\bPRIMARY\s+KEY\b", rest))

    default_sql, rest = take_default(rest)

    # flags
    not_null = bool(re.search(r"(?i)\bNOT\s+NULL\b", rest))
    rest = re.sub(r"(?i)\bNOT\s+NULL\b", " ", rest)
    rest = re.sub(r"(?i)\bNULL\b", " ", rest)
    rest = re.sub(r"(?i)\bIDENTITY\s*(?:\([^)]*\))?", " ", rest)
    rest = re.sub(r"(?i)\bPRIMARY\s+KEY(?:\s+(?:CLUSTERED|NONCLUSTERED))?", " ", rest)
    rest = re.sub(r"(?i)\bUNIQUE(?:\s+(?:CLUSTERED|NONCLUSTERED))?", " ", rest)
    rest = re.sub(r"(?i)\bROWGUIDCOL\b", " ", rest)
    rest = re.sub(r"(?i)\bSPARSE\b", " ", rest)
    rest = re.sub(r"\s+", " ", rest).strip()

    pg_type = map_data_type(rest)
    seq_sql = None

    if is_identity:
        if pg_type not in ("integer", "bigint", "smallint"):
            pg_type = "integer"
        seq_name = f"{SCHEMA}.{table_name}_seq"
        seq_sql = (
            f"create sequence {seq_name} "
            f"INCREMENT BY 1 MINVALUE 1 MAXVALUE 999999999999999999 "
            f"START 1 CACHE 1 NO CYCLE;"
        )
        pieces = [
            f"\t{col_name} {pg_type}",
            "NOT NULL",
            f"default nextval('{seq_name}')",
        ]
        if col_name in pk_cols or col_pk:
            if len(pk_cols) <= 1:
                pieces.append("PRIMARY KEY")
        return " ".join(pieces), seq_sql, col_name

    pieces = [f"\t{col_name} {pg_type}"]
    if not_null or col_name in pk_cols or col_pk:
        pieces.append("NOT NULL")
    if default_sql is not None:
        pieces.append(f"default {default_sql}")
    if (col_name in pk_cols or col_pk) and len(pk_cols) <= 1:
        pieces.append("PRIMARY KEY")

    return " ".join(pieces), None, col_name


def convert_unique_constraint(part: str) -> str | None:
    um = re.search(
        r"(?is)(?:CONSTRAINT\s+(?:\[[^\]]+\]|\w+)\s+)?UNIQUE(?:\s+(?:CLUSTERED|NONCLUSTERED))?\s*\(([^)]+)\)",
        part,
    )
    if not um:
        return None
    cols = []
    for raw in split_top_level(um.group(1)):
        col = re.sub(r"(?i)\s+(ASC|DESC)\b", "", raw).strip()
        cols.append(normalize_ident(col))
    return f"\tUNIQUE ({', '.join(cols)})"


def convert_create_table(stmt: str) -> str:
    table = extract_table_name(stmt)
    m = re.search(r"(?is)CREATE\s+TABLE\s+[^\(]+\(", stmt)
    if not m:
        raise ValueError(f"表 {table} 缺少列定义括号")
    open_idx = m.end() - 1
    close_idx = find_matching_paren(stmt, open_idx)
    if close_idx < 0:
        raise ValueError(f"表 {table} 括号不匹配")

    body = stmt[open_idx + 1 : close_idx]
    parts = split_top_level(body)

    pk_cols = parse_table_pk(parts)
    # 列级 PK
    for part in parts:
        if is_constraint_def(part):
            continue
        cm = re.match(r"(?is)^\s*((?:\[[^\]]+\]|[A-Za-z0-9_#]+))\s+(.+)$", part)
        if cm and re.search(r"(?i)\bPRIMARY\s+KEY\b", cm.group(2)):
            pk_cols = [normalize_ident(cm.group(1))]
            break

    first_col = None
    for part in parts:
        if is_constraint_def(part):
            continue
        cm = re.match(r"(?is)^\s*((?:\[[^\]]+\]|[A-Za-z0-9_#]+))\s+", part)
        if cm:
            first_col = normalize_ident(cm.group(1))
            break
    if not pk_cols and first_col:
        pk_cols = [first_col]

    pk_set = set(pk_cols)
    col_lines: list[str] = []
    seq_lines: list[str] = []
    extras: list[str] = []

    for part in parts:
        if is_constraint_def(part):
            if re.search(r"(?is)PRIMARY\s+KEY", part):
                continue
            u = convert_unique_constraint(part)
            if u:
                extras.append(u)
            continue

        col_sql, seq_sql, _ = convert_column(part, table, pk_set)
        if col_sql:
            # 多列主键时去掉列尾 PRIMARY KEY
            if len(pk_cols) > 1:
                col_sql = re.sub(r"(?i)\s+PRIMARY\s+KEY\b", "", col_sql)
            col_lines.append(col_sql)
        if seq_sql:
            seq_lines.append(seq_sql)

    if len(pk_cols) > 1:
        extras.insert(0, f"\tPRIMARY KEY ({', '.join(pk_cols)})")

    table_sql = (
        f"CREATE TABLE {SCHEMA}.{table} (\n"
        + ",\n".join(col_lines + extras)
        + "\n);"
    )
    return "\n".join([*seq_lines, table_sql])


def main() -> None:
    if not INPUT_PATH.exists():
        raise SystemExit(f"输入文件不存在: {INPUT_PATH}")

    raw = INPUT_PATH.read_text(encoding="utf-8", errors="replace")
    statements = split_create_tables(raw)
    if not statements:
        raise SystemExit("未找到 CREATE TABLE 语句")

    outputs: list[str] = []
    err_count = 0
    for stmt in statements:
        try:
            outputs.append(convert_create_table(stmt))
        except Exception as exc:  # noqa: BLE001
            err_count += 1
            try:
                tname = extract_table_name(stmt)
            except Exception:
                tname = "unknown"
            print(f"ERROR {tname}: {exc}", file=sys.stderr)

    if not outputs:
        raise SystemExit("没有成功转换的表")

    result = "\n\n".join(outputs) + "\n"
    # 追加写入；若原文件非空则先空行分隔
    prepend_nl = OUTPUT_PATH.exists() and OUTPUT_PATH.stat().st_size > 0
    with OUTPUT_PATH.open("a", encoding="utf-8", newline="\n") as f:
        if prepend_nl:
            f.write("\n")
        f.write(result)

    # 进度信息只打 stderr，不污染 SQL 文件
    print(f"converted={len(outputs)} errors={err_count} -> {OUTPUT_PATH}", file=sys.stderr)


if __name__ == "__main__":
    main()
