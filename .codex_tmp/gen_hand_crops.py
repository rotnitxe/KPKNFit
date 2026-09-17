from PIL import Image
from pathlib import Path

assets = Path(r"C:\Users\valen\.cursor\projects\c-Users-valen-Documents-KPKNFit\assets")
out = Path(r"C:\Users\valen\Documents\KPKNFit\.codex_tmp\qa_preview")
files = {
    "martillo_x": "exercise_curl_martillo_kettlebell_x.png",
    "superman_r": "exercise_curl_superman_r.png",
    "patada_t": "exercise_triceps_patada_polea_t.png",
    "patada_u": "exercise_triceps_patada_polea_u.png",
}
crops = {
    "martillo_x": (0.28, 0.30, 0.72, 0.48),
    "superman_r": (0.05, 0.38, 0.95, 0.72),
    "patada_t": (0.02, 0.50, 0.55, 0.82),
    "patada_u": (0.02, 0.50, 0.55, 0.82),
}
for key, fname in files.items():
    src = assets / fname
    im = Image.open(src).convert("RGB")
    w, h = im.size
    x0, y0, x1, y1 = crops[key]
    box = (int(w * x0), int(h * y0), int(w * x1), int(h * y1))
    dst = out / f"crop_exercise_{fname.replace('.png', '')}.png"
    im.crop(box).save(dst)
    print(dst, box, im.size)
