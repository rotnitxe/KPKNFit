from PIL import Image, ImageDraw
import numpy as np
from pathlib import Path

base = Path(__file__).parent


def analyze_hole(name):
    im = Image.open(base / f"{name}_kb.png").convert("RGBA")
    arr = np.array(im)
    h, w = arr.shape[:2]
    rgb = arr[:, :, :3].astype(float)
    lum = 0.299 * rgb[:, :, 0] + 0.587 * rgb[:, :, 1] + 0.114 * rgb[:, :, 2]
    alpha = arr[:, :, 3] > 128

    x0, x1, y0, y1 = 0, int(w * 0.65), 0, int(h * 0.55)
    sl = np.s_[y0:y1, x0:x1]
    roi_lum = lum[sl]
    roi_rgb = rgb[sl]
    roi_a = alpha[sl]

    r, g, b = roi_rgb[:, :, 0], roi_rgb[:, :, 1], roi_rgb[:, :, 2]
    mx = np.maximum(np.maximum(r, g), b)
    mn = np.minimum(np.minimum(r, g), b)
    sat = np.divide(mx - mn, mx, out=np.zeros_like(mx), where=mx > 0)
    chrome = roi_a & (roi_lum > 95) & (sat < 0.25) & (roi_lum < 245)
    void = roi_a & (roi_lum < 8)

    chrome_ys, chrome_xs = np.where(chrome)
    if len(chrome_xs) == 0:
        return None

    cx_min, cx_max = chrome_xs.min(), chrome_xs.max()
    cy_min = chrome_ys.min()
    cy_mid = int((chrome_ys.min() + chrome_ys.max()) * 0.45)
    ix0 = cx_min + int((cx_max - cx_min) * 0.25)
    ix1 = cx_min + int((cx_max - cx_min) * 0.75)

    interior_black = 0
    interior_chrome = 0
    interior_other = 0
    for x in range(ix0, ix1):
        col_chrome = np.where(chrome[:, x])[0]
        if len(col_chrome) < 4:
            continue
        top = col_chrome.min()
        bot = col_chrome.max()
        y_start = top + 2
        y_end = min(bot - 2, top + max(8, (bot - top) // 3))
        if y_end <= y_start:
            continue
        seg = np.s_[y_start:y_end, x]
        interior_black += void[seg].sum()
        interior_chrome += chrome[seg].sum()
        interior_other += roi_a[seg].sum() - void[seg].sum() - chrome[seg].sum()

    total = interior_black + interior_chrome + interior_other
    return {
        "interior_black": int(interior_black),
        "interior_chrome": int(interior_chrome),
        "interior_other": int(interior_other),
        "chrome_ratio": interior_chrome / max(1, total),
        "ix": (ix0 + x0, ix1 + x0),
        "y": (y0 + cy_min, y0 + cy_mid),
    }


for name in ["co", "db", "dc"]:
    print(name, analyze_hole(name))

imgs = []
for name in ["co", "db", "dc"]:
    im = Image.open(base / f"{name}_kb.png").convert("RGB")
    r = analyze_hole(name)
    d = ImageDraw.Draw(im)
    if r:
        x0, x1 = r["ix"]
        y0, y1 = r["y"]
        d.rectangle([x0, y0, x1, y1], outline=(255, 0, 255), width=2)
        d.text((5, 5), f"{name}: chrome={r['interior_chrome']}", fill=(255, 0, 255))
    imgs.append(im.crop((0, 0, int(im.width * 0.7), int(im.height * 0.6))))

w = max(i.width for i in imgs)
h = max(i.height for i in imgs)
montage = Image.new("RGB", (w * 3, h), (0, 0, 0))
for i, im in enumerate(imgs):
    montage.paste(im, (i * w, 0))
montage.save(base / "compare_montage.png")
print("saved compare_montage.png")
