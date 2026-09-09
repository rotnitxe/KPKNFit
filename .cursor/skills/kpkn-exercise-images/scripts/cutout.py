#!/usr/bin/env python3
"""Recorta un PNG de técnica a RGBA 1024x1024 (esquinas alfa 0)."""
from __future__ import annotations

import argparse
from pathlib import Path

from PIL import Image
from rembg import remove


def cutout(src: Path, out: Path, size: int = 1024) -> None:
    im = Image.open(src).convert("RGB")
    cut = remove(im)
    if cut.size != (size, size):
        cut = cut.resize((size, size), Image.Resampling.LANCZOS)
    out.parent.mkdir(parents=True, exist_ok=True)
    cut.save(out)
    px = cut.load()
    w, h = cut.size
    corners = [px[0, 0][3], px[w - 1, 0][3], px[0, h - 1][3], px[w - 1, h - 1][3]]
    if any(a != 0 for a in corners):
        raise SystemExit(f"esquinas no transparentes: {corners}")
    print(f"wrote {out} corners_a={corners}")


def main() -> None:
    p = argparse.ArgumentParser()
    p.add_argument("src")
    p.add_argument("--out", required=True)
    p.add_argument("--size", type=int, default=1024)
    args = p.parse_args()
    cutout(Path(args.src), Path(args.out), args.size)


if __name__ == "__main__":
    main()
