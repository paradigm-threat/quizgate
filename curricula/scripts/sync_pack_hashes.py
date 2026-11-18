#!/usr/bin/env python3
"""Rewrite pack contentSha256/sizeBytes from curriculum.json and rebuild .qgpack.

Use after editing curriculum.json so CI validate_packs stays green:
  python3 curricula/scripts/sync_pack_hashes.py
  ./scripts/run_regression.sh
"""
from __future__ import annotations

import hashlib
import json
import shutil
import subprocess
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CURRICULA = ROOT / "curricula"
SITE = ROOT / "site" / "curricula"
ASSETS = ROOT / "UnlockQuiz" / "assets" / "curricula"


def sha_and_size(path: Path) -> tuple[str, int]:
    raw = path.read_bytes()
    return hashlib.sha256(raw).hexdigest(), len(raw)


def patch_index(path: Path, sha: str, size: int, pack_id: str) -> None:
    text = path.read_text()
    data = json.loads(text)
    updated = False

    def walk(obj):
        nonlocal updated
        if isinstance(obj, dict):
            oid = obj.get("id") or obj.get("packId") or ""
            if oid == pack_id or (isinstance(oid, str) and oid.startswith(pack_id)):
                if "contentSha256" in obj or "sizeBytes" in obj:
                    obj["contentSha256"] = sha
                    obj["sizeBytes"] = size
                    updated = True
            for v in obj.values():
                walk(v)
        elif isinstance(obj, list):
            for v in obj:
                walk(v)

    walk(data)
    if not updated:
        raise SystemExit(f"{path}: no entry for pack {pack_id}")
    path.write_text(json.dumps(data, indent=2) + "\n")
    print(f"updated {path.relative_to(ROOT)}")


def rebuild_qgpack(pack_dir: Path, version: int) -> None:
    name = f"{pack_dir.name}-v{version}.qgpack"
    out_dirs = [CURRICULA / "packs", SITE / "packs"]
    for d in out_dirs:
        d.mkdir(parents=True, exist_ok=True)
        out = d / name
        if out.exists():
            out.unlink()
        with zipfile.ZipFile(out, "w", compression=zipfile.ZIP_DEFLATED) as zf:
            for fn in ("SIGNATURE", "manifest.json", "curriculum.json"):
                zf.write(pack_dir / fn, arcname=fn)
        print(f"wrote {out.relative_to(ROOT)}")


def sync_pack(pack_dir: Path) -> None:
    curriculum = pack_dir / "curriculum.json"
    manifest = pack_dir / "manifest.json"
    if not curriculum.is_file() or not manifest.is_file():
        raise SystemExit(f"incomplete pack: {pack_dir}")
    sha, size = sha_and_size(curriculum)
    man = json.loads(manifest.read_text())
    pack_id = man.get("id") or pack_dir.name
    version = int(man.get("version") or 3)
    man["contentSha256"] = sha
    man["sizeBytes"] = size
    manifest.write_text(json.dumps(man, indent=2) + "\n")
    print(f"updated {manifest.relative_to(ROOT)} sha={sha[:12]} size={size}")

    asset_dir = ASSETS / pack_dir.name
    if asset_dir.is_dir():
        shutil.copy2(curriculum, asset_dir / "curriculum.json")
        shutil.copy2(manifest, asset_dir / "manifest.json")
        print(f"synced assets {asset_dir.relative_to(ROOT)}")

    for index in (CURRICULA / "index.json", SITE / "index.json"):
        if index.is_file():
            patch_index(index, sha, size, pack_id)

    rebuild_qgpack(pack_dir, version)


def main() -> None:
    packs = [p for p in CURRICULA.iterdir() if p.is_dir() and (p / "curriculum.json").is_file()]
    if not packs:
        raise SystemExit("no packs under curricula/")
    for p in sorted(packs):
        sync_pack(p)
    # Re-validate
    subprocess.check_call([sys.executable, str(CURRICULA / "scripts" / "validate_packs.py")])


if __name__ == "__main__":
    main()
