#!/usr/bin/env python3
"""Parse contracts YAML/JSON and check frozen M1 error codes from docs/13.

Missing contracts directory is handled by CI job skip, not by returning 0 here.
"""
from __future__ import annotations

import json
import sys
from pathlib import Path

try:
    import yaml
except ImportError:
    sys.stderr.write("PyYAML is required: pip install pyyaml\n")
    sys.exit(2)

ROOT = Path(__file__).resolve().parents[2]
CONTRACTS = ROOT / "contracts"

REQUIRED_ERRORS = (
    ("1001", "BINDING_MISMATCH"),
    ("1002", "BINDING_TAKEN"),
    ("1003", "ACCOUNT_ALREADY_BOUND"),
    ("1004", "BINDING_LOCKED"),
    ("1005", "MEMBER_NOT_STARTED"),
    ("1006", "MEMBER_EXPIRED"),
    ("1007", "UNLOCK_QUOTA_EXHAUSTED"),
    ("1008", "WORK_UNPUBLISHED"),
    ("1009", "BINDING_REQUIRED"),
    ("1010", "ALREADY_UNLOCKED"),
    ("1011", "VERIFY_CODE_INVALID"),
    ("1012", "RATE_LIMITED"),
    ("1013", "ACCOUNT_LOCKED"),
    ("1014", "ACCOUNT_CANCELLING"),
    ("1090", "IDEMPOTENCY_CONFLICT"),
)

TEXT_SUFFIXES = {".yaml", ".yml", ".json"}


def fail(message: str) -> None:
    sys.stderr.write(message + "\n")
    sys.exit(1)


def load_text(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def parse_file(path: Path) -> object:
    text = load_text(path)
    suffix = path.suffix.lower()
    try:
        if suffix == ".json":
            return json.loads(text)
        return yaml.safe_load(text)
    except Exception as exc:  # noqa: BLE001 — report parse path, then abort
        fail(f"failed to parse {path.relative_to(ROOT)}: {exc}")
        raise


def iter_contract_files() -> list[Path]:
    if not CONTRACTS.is_dir():
        fail("contracts/ directory is missing")
    files = sorted(
        p
        for p in CONTRACTS.rglob("*")
        if p.is_file() and p.suffix.lower() in TEXT_SUFFIXES
    )
    if not files:
        fail("contracts/ has no .yaml/.yml/.json files")
    return files


def walk_paths(node: object) -> list[str]:
    found: list[str] = []
    if isinstance(node, dict):
        paths = node.get("paths")
        if isinstance(paths, dict):
            found.extend(str(key) for key in paths)
        for value in node.values():
            found.extend(walk_paths(value))
    elif isinstance(node, list):
        for item in node:
            found.extend(walk_paths(item))
    return found


def is_legacy_api_path(path: str) -> bool:
    if path.startswith("/admin-api"):
        return False
    if path.startswith("/app"):
        return False
    if path.startswith("/v1"):
        return False
    return path.startswith("/api/") or path == "/api"


def check_errors_yaml(path: Path) -> None:
    text = load_text(path)
    missing: list[str] = []
    for code, name in REQUIRED_ERRORS:
        if code not in text or name not in text:
            missing.append(f"{code} {name}")
    if missing:
        fail(
            "contracts/errors.yaml missing frozen error codes from docs/13:\n  "
            + "\n  ".join(missing)
        )


def main() -> None:
    files = iter_contract_files()
    errors_yaml = CONTRACTS / "errors.yaml"
    if errors_yaml.is_file():
        check_errors_yaml(errors_yaml)

    legacy: list[str] = []
    for path in files:
        loaded = parse_file(path)
        rel = path.relative_to(ROOT).as_posix()
        if path.suffix.lower() in {".yaml", ".yml", ".json"}:
            for api_path in walk_paths(loaded):
                if is_legacy_api_path(api_path):
                    legacy.append(f"{rel}: {api_path}")

    if legacy:
        fail(
            "legacy /api/** paths are frozen-out by docs/13:\n  "
            + "\n  ".join(legacy)
        )

    print(f"contracts ok: parsed {len(files)} file(s)")


if __name__ == "__main__":
    main()
