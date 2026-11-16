#!/usr/bin/env python3
"""Deterministic curriculum pack validator (CI). SI batch review is separate."""
from __future__ import annotations

import hashlib
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def validate_pack(pack_dir: Path) -> None:
    manifest = json.loads((pack_dir / "manifest.json").read_text())
    curriculum = json.loads((pack_dir / "curriculum.json").read_text())
    raw = (pack_dir / "curriculum.json").read_bytes()
    sha = hashlib.sha256(raw).hexdigest()
    expected = manifest.get("contentSha256", "")
    if expected and expected != sha:
        raise SystemExit(f"{pack_dir.name}: contentSha256 mismatch {expected} != {sha}")
    items = curriculum.get("items") or []
    if not items:
        raise SystemExit(f"{pack_dir.name}: empty items")
    ids = set()
    for item in items:
        iid = item.get("id")
        if not iid:
            raise SystemExit(f"{pack_dir.name}: item missing id")
        if iid in ids:
            raise SystemExit(f"{pack_dir.name}: duplicate id {iid}")
        ids.add(iid)
        choices = item.get("choices") or []
        if len(choices) != 3:
            raise SystemExit(f"{pack_dir.name}: {iid} need 3 choices")
        if item.get("correct") not in choices:
            raise SystemExit(f"{pack_dir.name}: {iid} correct not in choices")
        if "spoken" not in item or not item["spoken"]:
            raise SystemExit(f"{pack_dir.name}: {iid} empty spoken")
    print(f"OK {pack_dir.name} items={len(items)} sha={sha[:12]}")


def main() -> None:
    packs = [p for p in ROOT.iterdir() if p.is_dir() and (p / "curriculum.json").is_file()]
    if not packs:
        raise SystemExit("no packs under curricula/")
    for p in sorted(packs):
        validate_pack(p)
    print("ALL PACKS VALID")


if __name__ == "__main__":
    main()
