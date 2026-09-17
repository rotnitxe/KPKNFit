"""Persona via rembg (dilatado) UNION pixeles no-negros del original (torres/metal gris)."""
from __future__ import annotations

from pathlib import Path

import numpy as np
from PIL import Image, ImageFilter
from rembg import remove


def hybrid_cutout(src: Path, out: Path, size: int = 1024, luma_keep: int = 18, dilate: int = 11) -> None:
    im = Image.open(src).convert("RGB")
    if im.size != (size, size):
        im = im.resize((size, size), Image.Resampling.LANCZOS)
    cut = remove(im).convert("RGBA")
    if cut.size != (size, size):
        cut = cut.resize((size, size), Image.Resampling.LANCZOS)

    rgb = np.array(im)
    rgba = np.array(cut)
    luma = (0.2126 * rgb[:, :, 0] + 0.7152 * rgb[:, :, 1] + 0.0722 * rgb[:, :, 2])
    rem_a = rgba[:, :, 3]
    if dilate >= 3 and dilate % 2 == 1:
        rem_a = np.array(Image.fromarray(rem_a).filter(ImageFilter.MaxFilter(dilate)))
    keep = (rem_a > 40) | (luma > luma_keep)
    # Catalog PNGs require transparent corners even if a tower hits the frame edge.
    keep[:2, :] = False
    keep[-2:, :] = False
    keep[:, :2] = False
    keep[:, -2:] = False
    out_a = np.zeros((size, size, 4), dtype=np.uint8)
    out_a[:, :, :3] = rgb
    out_a[:, :, 3] = np.where(keep, 255, 0).astype(np.uint8)

    result = Image.fromarray(out_a, "RGBA")
    out.parent.mkdir(parents=True, exist_ok=True)
    result.save(out)
    px = result.load()
    w, h = result.size
    corners = [px[0, 0][3], px[w - 1, 0][3], px[0, h - 1][3], px[w - 1, h - 1][3]]
    if any(a != 0 for a in corners):
        raise SystemExit(f"esquinas no transparentes: {corners}")
    print(f"wrote {out} corners_a={corners}", flush=True)


if __name__ == "__main__":
    assets = Path(r"C:\Users\valen\.cursor\projects\c-Users-valen-Documents-KPKNFit\assets")
    tmp = Path(r"C:\Users\valen\Documents\KPKNFit\.codex_tmp")
    hybrid_cutout(assets / "exercise_curl_superman_r.png", tmp / "hybrid_superman.png")
    hybrid_cutout(assets / "exercise_triceps_patada_polea_u.png", tmp / "hybrid_patada.png")
