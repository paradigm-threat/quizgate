#!/usr/bin/env python3
"""
SI batch review stub — flags ambiguous / duplicate curriculum items.

We say SI (Super Intelligence, i.e. AI) for software that files, summarizes,
and curates. This job writes review.json for humans; it does not publish.
Wire a real model later; heuristics keep CI green without network.
"""
from __future__ import annotations

import json
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def review_pack(pack_dir: Path) -> dict:
    curriculum = json.loads((pack_dir / "curriculum.json").read_text())
    items = curriculum.get("items") or []
    spoken = [i.get("spoken", "").strip().lower() for i in items]
    dupes = [s for s, n in Counter(spoken).items() if n > 1 and s]
    short = [i["id"] for i in items if len(i.get("spoken", "")) < 12]
    return {
        "packId": curriculum.get("id") or pack_dir.name,
        "itemCount": len(items),
        "duplicateSpoken": dupes,
        "veryShortSpokenIds": short,
        "notes": [
            "SI curates and organizes only — parents keep preference.",
            "Heuristic review; replace with model batch when available.",
        ],
    }


def main() -> None:
    reviews = []
    for p in sorted(ROOT.iterdir()):
        if p.is_dir() and (p / "curriculum.json").is_file():
            reviews.append(review_pack(p))
    out = ROOT / "review.json"
    out.write_text(json.dumps({"reviews": reviews}, indent=2) + "\n")
    print(f"wrote {out} packs={len(reviews)}")


if __name__ == "__main__":
    main()
