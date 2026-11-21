#!/usr/bin/env python3
"""Rewrite pack contentSha256/sizeBytes from curriculum.json and rebuild .qgpack.

Also refreshes curricula/index.json + site/curricula/index.json with version,
packUrl, description, and updatedAt so devices can sync the shared SoT pack.

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
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CURRICULA = ROOT / "curricula"
SITE = ROOT / "site" / "curricula"
ASSETS = ROOT / "UnlockQuiz" / "assets" / "curricula"
PACK_BASE_URL = "https://quizgate.app/curricula/packs"


def sha_and_size(path: Path) -> tuple[str, int]:
    raw = path.read_bytes()
    return hashlib.sha256(raw).hexdigest(), len(raw)


def patch_index(
    path: Path,
    *,
    pack_id: str,
    sha: str,
    size: int,
    version: int,
    title: str,
    description: str,
    age_min: int,
    age_max: int,
    difficulty_rank: int,
    locale: str,
    min_app_version: int,
    bundled: bool,
) -> None:
    text = path.read_text()
    data = json.loads(text)
    updated = False
    now = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    pack_url = f"{PACK_BASE_URL}/{pack_id}-v{version}.qgpack"

    def apply_fields(obj: dict) -> None:
        nonlocal updated
        obj["id"] = pack_id
        obj["title"] = title or pack_id
        obj["ageMin"] = age_min
        obj["ageMax"] = age_max
        obj["difficultyRank"] = difficulty_rank
        obj["locale"] = locale or "en"
        obj["version"] = version
        obj["minAppVersion"] = min_app_version
        obj["description"] = description or ""
        obj["contentSha256"] = sha
        obj["sizeBytes"] = size
        obj["packUrl"] = pack_url
        obj["bundled"] = bundled
        updated = True

    def walk(obj):
        if isinstance(obj, dict):
            oid = obj.get("id") or obj.get("packId") or ""
            if oid == pack_id or (isinstance(oid, str) and oid.startswith(pack_id)):
                apply_fields(obj)
            for v in obj.values():
                walk(v)
        elif isinstance(obj, list):
            for v in obj:
                walk(v)

    walk(data)
    if not updated:
        # Create a curricula[] entry if missing.
        curricula = data.setdefault("curricula", [])
        if not isinstance(curricula, list):
            raise SystemExit(f"{path}: curricula is not a list")
        entry: dict = {}
        apply_fields(entry)
        curricula.append(entry)
    data["updatedAt"] = now
    path.write_text(json.dumps(data, indent=2) + "\n")
    print(f"updated {path.relative_to(ROOT)} → v{version} {pack_url}")


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
            patch_index(
                index,
                pack_id=pack_id,
                sha=sha,
                size=size,
                version=version,
                title=str(man.get("title") or pack_id),
                description=str(man.get("description") or ""),
                age_min=int(man.get("ageMin") or 4),
                age_max=int(man.get("ageMax") or 7),
                difficulty_rank=int(man.get("difficultyRank") or 10),
                locale=str(man.get("locale") or "en"),
                min_app_version=int(man.get("minAppVersion") or 1),
                bundled=True,
            )

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
