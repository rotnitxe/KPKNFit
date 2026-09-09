#!/usr/bin/env python3
"""Recortes de QA (zona baja / placa / torso) para inspectores visuales."""
from __future__ import annotations

import argparse
from pathlib import Path

from PIL import Image


def crops(src: Path, out_dir: Path) -> None:
    im = Image.open(src).convert("RGB")
    w, h = im.size
    out_dir.mkdir(parents=True, exist_ok=True)
    stem = src.stem
    im.crop((0, int(h * 0.42), w, h)).save(out_dir / f"{stem}_low.png")
    im.crop((int(w * 0.02), int(h * 0.48), int(w * 0.90), h)).save(out_dir / f"{stem}_plate.png")
    im.crop((int(w * 0.08), 0, int(w * 0.92), int(h * 0.52))).save(out_dir / f"{stem}_up.png")
    print(f"wrote crops in {out_dir}")


def main() -> None:
    p = argparse.ArgumentParser()
    p.add_argument("src")
    p.add_argument("--out-dir", required=True)
    args = p.parse_args()
    crops(Path(args.src), Path(args.out_dir))


if __name__ == "__main__":
    main()
