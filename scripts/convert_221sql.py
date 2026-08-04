#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
SQL Server CREATE TABLE -> PostgreSQL converter (Python 3.12+)

Usage:
    py -3.12 scripts/convert_221sql.py
    py -3.12 scripts/convert_221sql.py --input D:\\221sql.txt --output D:\\221pgsql.txt
"""

from __future__ import annotations

import argparse
import re
from pathlib import Path


SCHEMA = "mes_ats_szb0"

# ---------------------------------------------------------------------------
# Split
# ---------------------------------------------------------------------------

def split_create_tables(sql_text: str) -> list[str]:
    """Split multi-table SQL Server DDL into individual CREATE TABLE statements."""
    text = sql_text.replace("\r\n", "\n").replace("\r", "\n")
    pattern = re.compile(r"\bCREATE\s+TABLE\b", re.IGNORECASE)
    matches = list(pattern.finditer(text))
    if not matches:
        return []

    statements: list[str] = []
    for i, m in enumerate(matches):
        start = m.start()
        end = matches[i + 1].start() if i + 1 < len(matches) else len(text)
        stmt = text[start:end].strip()
        stmt = re.sub(r"\n\s*GO\s*$", "", stmt, flags=re.IGNORECASE)
        stmt = stmt.strip().rstrip(";").strip()
        if stmt:
            statements.append(stmt)
    return statements


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def strip_brackets(name: str) -> str:
    return name.replace("[", "").replace("]", "").strip()


def unquote_ident(name: str) -> str:
    name = strip_brackets(name).strip()
    if (name.startswith('"') and name.endswith('"')) or (
        name.startswith("'") and name.endswith("'")
    ):
        name = name[1:-1]
    if "." in name:
        name = name.split(".")[-1]
    return name.strip()


def to_ident(name: str) -> str:
    """Lowercase PostgreSQL identifier."""
    name = unquote_ident(name)
    name = re.sub(r"[^\w]+", "_", name, flags=re.UNICODE)
    name = name.strip("_").lower()
    if not name:
        name = "col"
    if name[0].isdigit():
        name = "c_" + name
    return name


def map_data_type(sql_type: str) -> str:
    """Map a SQL Server type to PostgreSQL."""
    t = strip_brackets(sql_type)
    t = re.sub(r"\s+", " ", t.strip())

    m = re.match(
        r"^(?P<base>[A-Za-z#]+)"
        r"(?:\s*\((?P<args>[^)]*)\))?"
        r"(?:\s+(?P<extra>.*))?$",
        t,
        re.IGNORECASE,
    )
    if not m:
        return t.lower()

    base = m.group("base").lower()
    args = (m.group("args") or "").strip()
    extra = (m.group("extra") or "").strip().lower()

    mapping_simple = {
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
        "uniqueidentifier": "varchar(36)",
        "datetime": "timestamp",
        "datetime2": "timestamp",
        "smalldatetime": "timestamp",
        "date": "date",
        "time": "time",
        "datetimeoffset": "timestamptz",
        "text": "text",
        "ntext": "text",
        "image": "bytea",
        "xml": "xml",
        "sql_variant": "text",
        "hierarchyid": "text",
        "geography": "text",
        "geometry": "text",
        "timestamp": "bytea",
        "rowversion": "bytea",
    }

    if base in ("nvarchar", "nchar", "varchar", "char", "sysname"):
        if not args or args.upper() == "MAX":
            return "text"
        if base in ("nchar", "char"):
            return f"char({args})"
        return f"varchar({args})"

    if base in ("varbinary", "binary"):
        return "bytea"

    if base in ("decimal", "numeric", "dec"):
        return f"numeric({args})" if args else "numeric"

    if base == "double" and "precision" in extra:
        return "double precision"

    if base in mapping_simple:
        return mapping_simple[base]

    if args:
        return f"{base}({args})"
    return base


def extract_paren_body(stmt: str) -> tuple[str, str, str]:
    m = re.search(r"\bCREATE\s+TABLE\b", stmt, re.IGNORECASE)
    if not m:
        raise ValueError("Not a CREATE TABLE statement")

    i = m.end()
    while i < len(stmt) and stmt[i].isspace():
        i += 1
    while i < len(stmt) and stmt[i] != "(":
        i += 1
    if i >= len(stmt) or stmt[i] != "(":
        raise ValueError("Cannot find column list '('")

    start = i
    depth = 0
    for j in range(start, len(stmt)):
        ch = stmt[j]
        if ch == "(":
            depth += 1
        elif ch == ")":
            depth -= 1
            if depth == 0:
                return stmt[:start], stmt[start + 1 : j], stmt[j + 1 :]
    raise ValueError("Unbalanced parentheses in CREATE TABLE")


def parse_table_name(head: str) -> str:
    m = re.search(
        r"CREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?(.+)$",
        head.strip(),
        re.IGNORECASE,
    )
    if not m:
        raise ValueError(f"Cannot parse table name from: {head[:80]}")
    return to_ident(m.group(1).strip())


def split_sql_list(body: str) -> list[str]:
    parts: list[str] = []
    buf: list[str] = []
    depth = 0
    in_squote = False
    in_dquote = False
    i = 0
    while i < len(body):
        ch = body[i]
        if ch == "'" and not in_dquote:
            if in_squote and i + 1 < len(body) and body[i + 1] == "'":
                buf.append("''")
                i += 2
                continue
            in_squote = not in_squote
            buf.append(ch)
        elif ch == '"' and not in_squote:
            in_dquote = not in_dquote
            buf.append(ch)
        elif not in_squote and not in_dquote:
            if ch == "(":
                depth += 1
                buf.append(ch)
            elif ch == ")":
                depth -= 1
                buf.append(ch)
            elif ch == "," and depth == 0:
                part = "".join(buf).strip()
                if part:
                    parts.append(part)
                buf = []
            else:
                buf.append(ch)
        else:
            buf.append(ch)
        i += 1
    part = "".join(buf).strip()
    if part:
        parts.append(part)
    return parts


def is_constraint_line(line: str) -> bool:
    return bool(
        re.match(
            r"^(CONSTRAINT\b|PRIMARY\s+KEY\b|UNIQUE\b|FOREIGN\s+KEY\b|CHECK\b|INDEX\b)",
            line.strip(),
            re.IGNORECASE,
        )
    )


def parse_identity(col_def: str) -> tuple[str, bool, int, int]:
    m = re.search(
        r"\bIDENTITY\s*(?:\(\s*(\d+)\s*,\s*(\d+)\s*\))?",
        col_def,
        re.IGNORECASE,
    )
    if not m:
        return col_def, False, 1, 1
    seed = int(m.group(1) or 1)
    incr = int(m.group(2) or 1)
    cleaned = (col_def[: m.start()] + col_def[m.end() :]).strip()
    cleaned = re.sub(r"\s+", " ", cleaned)
    return cleaned, True, seed, incr


def sql_string_literal(s: str) -> str:
    return "'" + s.replace("'", "''") + "'"


def extract_default(col_def: str) -> tuple[str, str | None]:
    # SQL Server: [CONSTRAINT df_name] DEFAULT (expr)
    m = re.search(
        r"(?:CONSTRAINT\s+(\[[^\]]+\]|\S+)\s+)?DEFAULT\s*",
        col_def,
        re.IGNORECASE,
    )
    if not m:
        return col_def, None

    rest = col_def[m.end() :].lstrip()
    stop = re.search(
        r"\b(NOT\s+NULL|NULL|PRIMARY\s+KEY|UNIQUE|CHECK|CONSTRAINT|COLLATE|IDENTITY)\b",
        rest,
        re.IGNORECASE,
    )
    if stop:
        default_expr = rest[: stop.start()].strip()
        remaining = (col_def[: m.start()] + " " + rest[stop.start() :]).strip()
    else:
        default_expr = rest.strip()
        remaining = col_def[: m.start()].strip()

    return remaining, default_expr.rstrip(",").strip()


def convert_default(expr: str, pg_type: str) -> str | None:
    if not expr:
        return None
    e = expr.strip()
    while e.startswith("(") and e.endswith(")"):
        e = e[1:-1].strip()

    el = e.lower()
    if el in ("getdate()", "getdate", "sysdatetime()", "current_timestamp"):
        return "CURRENT_TIMESTAMP"
    if el in ("newid()", "newsequentialid()"):
        return "gen_random_uuid()::text"
    if el in ("(0)", "0") and pg_type == "boolean":
        return "false"
    if el in ("(1)", "1") and pg_type == "boolean":
        return "true"

    m = re.match(r"N?'(.*)'$", e, re.DOTALL)
    if m:
        return sql_string_literal(m.group(1))

    if re.match(r"^-?\d+(\.\d+)?$", e):
        if pg_type == "boolean":
            return "false" if e in ("0", "0.0") else "true"
        return e

    return e


def extract_nullability(col_def: str) -> tuple[str, bool | None]:
    d = col_def
    nullable: bool | None = None
    if re.search(r"\bNOT\s+NULL\b", d, re.IGNORECASE):
        nullable = False
        d = re.sub(r"\bNOT\s+NULL\b", "", d, flags=re.IGNORECASE)
    elif re.search(r"\bNULL\b", d, re.IGNORECASE):
        nullable = True
        d = re.sub(r"(?<!NOT\s)\bNULL\b", "", d, flags=re.IGNORECASE)
    d = re.sub(r"\s+", " ", d).strip().rstrip(",")
    return d, nullable


def parse_column(line: str) -> dict | None:
    if is_constraint_line(line):
        return None

    m = re.match(
        r"^(\[[^\]]+\]|\"[^\"]+\"|[A-Za-z_][\w$#@]*)\s+(.+)$",
        line.strip(),
        re.DOTALL,
    )
    if not m:
        return None

    col_name = to_ident(m.group(1))
    rest = m.group(2).strip()

    rest, is_ident, seed, incr = parse_identity(rest)
    rest, default_expr = extract_default(rest)
    rest, nullable = extract_nullability(rest)

    rest = re.sub(r"\bCOLLATE\s+\S+", "", rest, flags=re.IGNORECASE).strip()
    col_pk = bool(re.search(r"\bPRIMARY\s+KEY\b", rest, re.IGNORECASE))
    col_unique = bool(re.search(r"\bUNIQUE\b", rest, re.IGNORECASE))
    rest = re.sub(r"\bPRIMARY\s+KEY\b", "", rest, flags=re.IGNORECASE)
    rest = re.sub(r"\bUNIQUE\b", "", rest, flags=re.IGNORECASE)
    rest = re.sub(r"\s+", " ", rest).strip().rstrip(",")

    pg_type = map_data_type(rest)
    pg_default = convert_default(default_expr, pg_type) if default_expr else None

    return {
        "name": col_name,
        "type": pg_type,
        "nullable": nullable,
        "default": pg_default,
        "identity": is_ident,
        "identity_seed": seed,
        "identity_inc": incr,
        "pk": col_pk,
        "unique": col_unique,
    }


def parse_table_constraints(lines: list[str]) -> dict:
    pk_cols: list[str] = []
    uniques: list[list[str]] = []
    checks: list[str] = []
    fks: list[str] = []

    for line in lines:
        s = line.strip()
        if not s:
            continue

        m = re.match(
            r"(?:CONSTRAINT\s+(\[[^\]]+\]|\S+)\s+)?PRIMARY\s+KEY(?:\s+CLUSTERED|\s+NONCLUSTERED)?\s*\((.+)\)",
            s,
            re.IGNORECASE,
        )
        if m:
            pk_cols = []
            for c in m.group(2).split(","):
                c = re.sub(r"\b(ASC|DESC)\b", "", c, flags=re.IGNORECASE).strip()
                pk_cols.append(to_ident(c))
            continue

        m = re.match(
            r"(?:CONSTRAINT\s+(\[[^\]]+\]|\S+)\s+)?UNIQUE(?:\s+CLUSTERED|\s+NONCLUSTERED)?\s*\((.+)\)",
            s,
            re.IGNORECASE,
        )
        if m:
            ucols = []
            for c in m.group(2).split(","):
                c = re.sub(r"\b(ASC|DESC)\b", "", c, flags=re.IGNORECASE).strip()
                ucols.append(to_ident(c))
            uniques.append(ucols)
            continue

        m = re.match(
            r"(?:CONSTRAINT\s+(\[[^\]]+\]|\S+)\s+)?CHECK\s*\((.+)\)\s*$",
            s,
            re.IGNORECASE | re.DOTALL,
        )
        if m:
            checks.append(m.group(2).strip())
            continue

        m = re.match(
            r"(?:CONSTRAINT\s+(\[[^\]]+\]|\S+)\s+)?FOREIGN\s+KEY\s*\((.+)\)\s*REFERENCES\s+(\S+)\s*\((.+)\)",
            s,
            re.IGNORECASE,
        )
        if m:
            local_cols = [to_ident(c.strip()) for c in m.group(2).split(",")]
            ref_table = to_ident(m.group(3))
            ref_cols = [to_ident(c.strip()) for c in m.group(4).split(",")]
            fks.append(
                f"FOREIGN KEY ({', '.join(local_cols)}) "
                f"REFERENCES {SCHEMA}.{ref_table} ({', '.join(ref_cols)})"
            )
            continue

    return {"pk": pk_cols, "uniques": uniques, "checks": checks, "fks": fks}


def _nstring(pattern_name: str, text: str) -> str | None:
    """Extract N'string' / 'string' value for a named @param."""
    m = re.search(
        rf"@{pattern_name}\s*=\s*N?'((?:''|[^'])*)'",
        text,
        re.IGNORECASE | re.DOTALL,
    )
    if not m:
        return None
    return m.group(1).replace("''", "'")


def extract_ms_description_comments(stmt: str) -> list[tuple[str, str]]:
    """
    Parse sp_addextendedproperty MS_Description.
    Column name from @level2name=N'...'; table comment when @level2name absent.
    """
    comments: list[tuple[str, str]] = []
    # Match each EXEC ... sp_addextendedproperty ... ;  (qualified names OK)
    for m in re.finditer(
        r"(?:EXEC(?:UTE)?\s+)?(?:\[[^\]]+\]|\w+)(?:\.(?:\[[^\]]+\]|\w+))*\.?sp_addextendedproperty\s+(.*?)(?=;|\bGO\b|\bEXEC(?:UTE)?\b|\bCREATE\s+TABLE\b|$)",
        stmt,
        re.IGNORECASE | re.DOTALL,
    ):
        args = m.group(1)
        name = _nstring("name", args)
        if name and name.lower() != "ms_description":
            continue
        value = _nstring("value", args)
        if value is None:
            continue
        level2 = _nstring("level2name", args)
        if level2:
            comments.append((to_ident(level2), value))
        else:
            comments.append(("", value))
    return comments


def extract_create_indexes(stmt: str, table: str) -> list[str]:
    indexes: list[str] = []
    for m in re.finditer(
        r"CREATE\s+(UNIQUE\s+)?(?:NONCLUSTERED\s+|CLUSTERED\s+)?INDEX\s+(\S+)\s+ON\s+(\S+)\s*\((.+?)\)",
        stmt,
        re.IGNORECASE | re.DOTALL,
    ):
        unique = bool(m.group(1))
        idx_name = to_ident(m.group(2))
        on_table = to_ident(m.group(3))
        if on_table != table:
            continue
        cols = []
        for part in m.group(4).split(","):
            part = re.sub(r"\b(ASC|DESC)\b", "", part, flags=re.IGNORECASE).strip()
            cols.append(to_ident(part))
        u = "UNIQUE " if unique else ""
        indexes.append(
            f"CREATE {u}INDEX {idx_name} ON {SCHEMA}.{table} ({', '.join(cols)});"
        )
    return indexes


def convert_create_table(stmt: str) -> str:
    head, body, _tail = extract_paren_body(stmt)
    table = parse_table_name(head)

    items = split_sql_list(body)
    columns: list[dict] = []
    constraint_lines: list[str] = []

    for item in items:
        col = parse_column(item)
        if col is not None:
            columns.append(col)
        elif is_constraint_line(item):
            constraint_lines.append(item)

    cons = parse_table_constraints(constraint_lines)

    pk_cols = list(cons["pk"])
    for c in columns:
        if c["pk"] and c["name"] not in pk_cols:
            pk_cols.append(c["name"])

    if not pk_cols and columns:
        pk_cols = [columns[0]["name"]]
        columns[0]["pk"] = True
        if columns[0]["nullable"] is True:
            columns[0]["nullable"] = False

    identity_cols = [c for c in columns if c["identity"]]
    seq_sqls: list[str] = []
    for c in identity_cols:
        is_pk = c["name"] in pk_cols
        if is_pk or c["type"] in ("integer", "bigint", "smallint"):
            seq_name = f"{table}_seq" if len(identity_cols) == 1 else f"{table}_{c['name']}_seq"
            qualified_seq = f"{SCHEMA}.{seq_name}"
            seed = c["identity_seed"]
            incr = c["identity_inc"]
            seq_sqls.append(
                f"create sequence {qualified_seq} "
                f"INCREMENT BY {incr} MINVALUE 1 "
                f"MAXVALUE 999999999999999999 START {seed} CACHE 1 NO CYCLE;"
            )
            # User sample: varchar(36) + nextval for int identity PK
            c["type"] = "varchar(36)"
            c["default"] = f"nextval('{qualified_seq}')"
            c["nullable"] = False
            c["identity"] = False
            if is_pk or not pk_cols:
                if c["name"] not in pk_cols:
                    pk_cols = [c["name"]]
                c["pk"] = True

    col_lines: list[str] = []
    for c in columns:
        parts = [f"\t{c['name']} {c['type']}"]
        if c["nullable"] is False or c["name"] in pk_cols:
            parts.append("NOT NULL")
        if c["default"] is not None:
            parts.append(f"default {c['default']}")
        if len(pk_cols) == 1 and c["name"] == pk_cols[0]:
            parts.append("PRIMARY KEY")
        elif c["unique"] and c["name"] not in pk_cols:
            parts.append("UNIQUE")
        col_lines.append(" ".join(parts))

    table_constraints: list[str] = []
    if len(pk_cols) > 1:
        table_constraints.append(f"\tPRIMARY KEY ({', '.join(pk_cols)})")
    for ucols in cons["uniques"]:
        table_constraints.append(f"\tUNIQUE ({', '.join(ucols)})")
    for fk in cons["fks"]:
        table_constraints.append(f"\t{fk}")
    for chk in cons["checks"]:
        table_constraints.append(f"\tCHECK ({strip_brackets(chk)})")

    all_inner = col_lines + table_constraints
    create_sql = (
        f"CREATE TABLE {SCHEMA}.{table} (\n"
        + ",\n".join(all_inner)
        + "\n);"
    )

    out_parts: list[str] = []
    out_parts.extend(seq_sqls)
    out_parts.append(create_sql)

    for idx in extract_create_indexes(stmt, table):
        out_parts.append(idx)

    for col, comment in extract_ms_description_comments(stmt):
        if col:
            out_parts.append(
                f"COMMENT ON COLUMN {SCHEMA}.{table}.{col} IS {sql_string_literal(comment)};"
            )
        else:
            out_parts.append(
                f"COMMENT ON TABLE {SCHEMA}.{table} IS {sql_string_literal(comment)};"
            )

    return "\n".join(out_parts)


def main() -> None:
    parser = argparse.ArgumentParser(description="SQL Server -> PostgreSQL CREATE TABLE converter")
    parser.add_argument("--input", default=r"D:\221sql.txt", help="Input SQL Server DDL file")
    parser.add_argument("--output", default=r"D:\221pgsql.txt", help="Output PostgreSQL DDL (append)")
    parser.add_argument(
        "--print-array",
        action="store_true",
        help="Print split CREATE TABLE string-array summary to stderr",
    )
    args = parser.parse_args()

    in_path = Path(args.input)
    out_path = Path(args.output)

    if not in_path.is_file():
        raise SystemExit(f"Input file not found: {in_path}")

    raw_bytes = in_path.read_bytes()
    raw = None
    for enc in ("utf-8-sig", "utf-8", "gbk", "gb18030", "utf-16"):
        try:
            raw = raw_bytes.decode(enc)
            break
        except UnicodeDecodeError:
            continue
    if raw is None:
        raw = raw_bytes.decode("utf-8", errors="replace")

    statements: list[str] = split_create_tables(raw)

    if args.print_array:
        import sys

        sys.stderr.write(f"statements = [  # {len(statements)} items\n")
        for i, s in enumerate(statements):
            sys.stderr.write(f"  # [{i}] {len(s)} chars, starts: {s[:60]!r}\n")
        sys.stderr.write("]\n")

    converted_blocks: list[str] = []
    for stmt in statements:
        try:
            converted_blocks.append(convert_create_table(stmt))
        except Exception as ex:
            converted_blocks.append(f"-- CONVERT ERROR: {ex}")

    out_text = "\n\n".join(converted_blocks)
    if out_text:
        if not out_text.endswith("\n"):
            out_text += "\n"
        out_text += "\n"

    with out_path.open("a", encoding="utf-8", newline="\n") as f:
        f.write(out_text)

    # Only SQL to stdout (no explanations)
    print(out_text, end="")


if __name__ == "__main__":
    main()
