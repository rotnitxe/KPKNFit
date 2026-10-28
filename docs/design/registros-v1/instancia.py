# Instancia «Nuevo programa» sobre capturas REALES del asistente: repinta la etiqueta de la cabecera, la fila plegada de datos
# conocidos y compone el cierre «Listo para empezar» con piezas reales (cabecera, filas de opción, botón).
import os, sys
import numpy as np
from PIL import Image, ImageDraw, ImageFont

BASE = sys.argv[1]  # docs/design/registros-v1
FONTS = sys.argv[2]  # android-native/app/src/main/res/font
REAL = os.path.join(BASE, 'real')
OUT = os.path.join(BASE, 'real-web')
INK = (242, 238, 230)
PILL = (18, 18, 18)
GREY = (150, 146, 140)

def f(name, size):
    return ImageFont.truetype(os.path.join(FONTS, name), size)

def spaced(d, xy, text, font, fill, track=0):
    x, y = xy
    for ch in text:
        d.text((x, y), ch, font=font, fill=fill)
        x += d.textlength(ch, font=font) + track
    return x

def spaced_len(d, text, font, track=0):
    return sum(d.textlength(ch, font=font) + track for ch in text) - track

def cols(a, y0, y1, x0, x1, th=300):
    br = a[y0:y1, x0:x1].sum(2)
    return [x0 + x for x in range(x1 - x0) if (br[:, x] > th).any()]

def relabel(im, word='NUEVO PROGRAMA'):
    """Sustituye «ENTRENO» por `word` conservando el icono y el contador «· n/m»."""
    a = np.asarray(im).astype(int)
    c = cols(a, 60, 104, 330, 900)
    groups, start, prev = [], c[0], c[0]
    for x in c[1:]:
        if x - prev > 14:
            groups.append((start, prev)); start = x
        prev = x
    groups.append((start, prev))
    icon, dot_start, end = groups[0], None, groups[-1][1]
    # el punto medio es el grupo estrecho tras la palabra
    for g in groups[2:]:
        if g[1] - g[0] < 14:
            dot_start = g[0]; break
    icon_img = im.crop((icon[0] - 2, 56, icon[1] + 3, 106))
    tail = im.crop((dot_start - 2, 56, end + 3, 106))
    d = ImageDraw.Draw(im)
    d.rectangle((icon[0] - 6, 56, end + 8, 106), fill=PILL)
    font = f('wizard_body_semibold.ttf', 36)
    gap1, gap2 = 26, 20
    wlen = spaced_len(d, word, font, 4)
    total = icon_img.width + gap1 + wlen + gap2 + tail.width
    x = round(589 - total / 2)
    im.paste(icon_img, (x, 56)); x += icon_img.width + gap1
    spaced(d, (x, 63), word, font, INK, 4)
    x += wlen + gap2
    im.paste(tail, (round(x), 56))
    return im

def fold_row(im, y0, y1, label, value):
    """Repinta el texto de una fila plegada (check y lápiz reales se quedan)."""
    d = ImageDraw.Draw(im)
    d.rectangle((170, y0 - 6, 1040, y1 + 8), fill=(0, 0, 0))
    d.text((180, y0 - 4), label, font=f('wizard_body_regular.ttf', 34), fill=GREY)
    d.text((180, y0 + 36), value, font=f('wizard_body_semibold.ttf', 42), fill=INK)
    return im

def save(im, name):
    im = im.convert('RGB')
    w = 393
    im.resize((w, round(im.height * w / im.width)), Image.LANCZOS).save(os.path.join(OUT, name + '.jpg'), quality=84, optimize=True)

def load(n):
    return Image.open(os.path.join(REAL, n + '.png')).convert('RGB')

# 1) Pasos del asistente en la instancia «Nuevo programa».
steps = ['wz_01_experiencia', 'wz_02_lugar', 'wz_03_material', 'wz_04_objetivo', 'wz_05_compites', 'wz_06_energia', 'wz_07_dias',
         'wz_08_tiempo', 'wz_08b_tecnica', 'wz_08c_constancia', 'wz_08d_fuerza', 'wz_08e_movilidad', 'wz_09_musculos', 'wz_10_marcas',
         'wz_11_plan_propuestas', 'wz_12_semana']
for s in steps:
    im = load(s)
    a = np.asarray(im).astype(int)
    if (a[60:104, 330:900].sum(2) > 500).any() and (abs(a[70, 360] - PILL).sum() < 10):
        im = relabel(im)
    if s == 'wz_01_experiencia':
        im = fold_row(im, 251, 336, 'Lo que ya sabemos', 'Matías · 34 años · 1,78 m · 80,4 kg')
    save(im, 'ni_' + s[3:])

# 2) Cierre «Listo para empezar», compuesto con piezas reales del paso de experiencia.
src = load('wz_01_experiencia')
a = np.asarray(src).astype(int)
canvas = Image.new('RGB', src.size, (0, 0, 0))
head = relabel(load('wz_12_semana')).crop((0, 0, 1179, 190))      # cabecera real con el progreso lleno (15/15)
canvas.paste(head, (0, 0))
fold_src = src.crop((0, 230, 1179, 360))                             # fila plegada real (check + lápiz)
for i, (lab, val) in enumerate([('Programa', 'Fuerza y masa en bloques · 6 sem.'), ('Tu semana', '5 días · ~60 min · Mié a Mar')]):
    y = 205 + i * 132
    canvas.paste(fold_src, (0, y))
    fold_row(canvas, y + 21, y + 106, lab, val)
d = ImageDraw.Draw(canvas)
d.line((72, 488, 1106, 488), fill=(26, 26, 26), width=3)
eb = f('wizard_body_semibold.ttf', 30)
spaced(d, (76, 540), 'ÚLTIMO PASO · ENTRENO', eb, GREY, 3)
syne = f('syne_extrabold.ttf', 84)
for i, line in enumerate(['Listo para', 'empezar']):
    d.text((72, 600 + i * 104), line, font=syne, fill=INK)
body = f('wizard_body_regular.ttf', 40)
for i, line in enumerate(['Ya tienes «Motor y fuerza» en curso', '(semana 4 de 6). ¿Qué hacemos con el nuevo?']):
    d.text((76, 832 + i * 54), line, font=body, fill=(200, 196, 190))
row = src.crop((40, 905, 1140, 1125))                                # fila de opción real (borde, radio)
ra = np.asarray(row).astype(int)
inner = tuple(int(v) for v in ra[100, 760])
opts = [('Empezar ahora', 'Pausa «Motor y fuerza»; podrás reanudarlo', False),
        ('Ponerlo en cola', 'Empieza cuando termine el actual', True),
        ('Guardarlo sin activar', 'Queda en «Tus programas»', False)]
for i, (t, s, on) in enumerate(opts):
    y = 1010 + i * 228
    canvas.paste(row, (40, y))
    d.rectangle((40 + 70, y + 30, 40 + 930, y + 190), fill=inner)
    d.text((40 + 74, y + 48), t, font=f('wizard_body_semibold.ttf', 44), fill=INK)
    d.text((40 + 74, y + 110), s, font=f('wizard_body_regular.ttf', 34), fill=GREY)
    if on:
        # radio marcado: aro claro y punto
        cy = y + 110
        rcols = [x for x in range(800, 1080) if (ra[100, x - 40] > 150).all()] if False else None
        d.ellipse((1022 - 31, cy - 31, 1022 + 31, cy + 31), fill=inner, outline=INK, width=5)
        d.ellipse((1022 - 14, cy - 14, 1022 + 14, cy + 14), fill=INK)
btn = load('wz_12_semana').crop((480, 2302, 700, 2522))              # botón real de confirmar, recortado en círculo
mask = Image.new('L', btn.size, 0)
ImageDraw.Draw(mask).ellipse((10, 10, 210, 210), fill=255)
canvas.paste(btn, (480, 2302), mask)
note = f('wizard_body_regular.ttf', 32)
txt = 'Al confirmar: guardado en cola, sin tocar el actual.'
d.text((589 - d.textlength(txt, font=note) / 2, 2210), txt, font=note, fill=(120, 116, 110))
canvas.save(os.path.join(REAL, 'ni_13_listo.png'))
save(canvas, 'ni_13_listo')
print('ok')
