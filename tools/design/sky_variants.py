"""Dérive les ciels d'aube, de jour et de crépuscule du ciel de nuit en pixel art (72 × 108).

Le ciel de nuit vient des maquettes (design/axo-entre-deux.html). On garde la ville et
on change la palette : bandes du ciel, soleil à la place de la lune, étoiles effacées.
Usage : python3 tools/design/sky_variants.py  (depuis la racine du dépôt, Pillow requis)
"""
from collections import Counter

from PIL import Image

DIR = "composeApp/src/commonMain/composeResources/drawable/"
BANDS = [(18, 23, 61), (23, 30, 73), (27, 36, 80), (35, 43, 98), (44, 51, 116), (58, 63, 134), (75, 74, 152)]
STARS = {(255, 255, 255), (201, 206, 255), (255, 233, 168)}
MOON, BACK, FRONT = (251, 239, 194), (26, 31, 82), (12, 15, 46)
WINDOWS = {(255, 211, 107), (255, 180, 92), (159, 216, 255)}
PERIODS = {
    "dawn": dict(top=(232, 140, 120), bottom=(253, 216, 164), sun=(255, 244, 214), back=(176, 112, 128), front=(112, 66, 98), lights=True, r=5.5),
    "day": dict(top=(104, 186, 236), bottom=(196, 232, 250), sun=(255, 240, 170), back=(132, 166, 206), front=(84, 112, 160), lights=False, r=6.5),
    "dusk": dict(top=(150, 74, 120), bottom=(248, 166, 104), sun=(255, 214, 140), back=(134, 66, 96), front=(78, 36, 70), lights=True, r=7.5),
}


def lerp(a, b, t):
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3))


def main():
    src = Image.open(DIR + "bg_night.png").convert("RGB")
    w, h = src.size
    for name, p in PERIODS.items():
        im = src.copy()
        px = im.load()
        band = {b: lerp(p["top"], p["bottom"], i / (len(BANDS) - 1)) for i, b in enumerate(BANDS)}
        for y in range(h):
            row = Counter(src.getpixel((x, y)) for x in range(w) if src.getpixel((x, y)) in BANDS)
            row_band = row.most_common(1)[0][0] if row else BANDS[-1]
            for x in range(w):
                c = src.getpixel((x, y))
                near_moon = 46 <= x <= 65 and 7 <= y <= 24
                if c in BANDS:
                    px[x, y] = band[row_band] if near_moon else band[c]
                elif c in STARS or c == MOON:
                    px[x, y] = band[row_band]
                elif c == BACK:
                    px[x, y] = p["back"]
                elif c == FRONT:
                    px[x, y] = p["front"]
                elif c in WINDOWS:
                    px[x, y] = c if p["lights"] and (x + y) % 3 == 0 else p["front"]
        cx, cy, r = 54, 16, p["r"]
        for y in range(h):
            for x in range(w):
                if (x + 0.5 - cx) ** 2 + (y + 0.5 - cy) ** 2 <= r * r:
                    px[x, y] = p["sun"]
        im.save(DIR + f"bg_sky_{name}.png", optimize=True)


if __name__ == "__main__":
    main()
