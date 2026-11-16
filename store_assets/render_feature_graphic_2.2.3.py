"""Feature graphic for Tarifni semafor 2.2.3, built on the 2.2.2 artwork.

Keeps the 2.2.2 background (waves, power lines), title, subtitle and chip; replaces the old green bolt tile with
the 2.2.3 logo (ring of the five official uro.si block colours + bolt) and adds the five block colours under the
chip. The English version also gets an English subtitle and chip (the Slovenian ones are filled in from the
surrounding background first).

Usage: python3 store_assets/render_feature_graphic_2.2.3.py
Input:  store_assets/play_2.2.2/feature_graphic_2.2.2_source.jpg (2000 x 961)
Output: store_assets/feature_graphic_2.2.3_1024x500.png (sl), store_assets/feature_graphic_2.2.3_1024x500_en.png (en),
        plus 2000 px masters in store_assets/play_2.2.3_master/
Needs Pillow, numpy is not required.
"""
import math, os
from PIL import Image, ImageDraw, ImageFilter, ImageFont, ImageChops

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(HERE, "play_2.2.2", "feature_graphic_2.2.2_source.jpg")
OUT = HERE
MASTER = os.path.join(HERE, "play_2.2.3_master")

COLORS = [(0xF1, 0x69, 0x0F), (0xF9, 0xC2, 0x12), (0x8F, 0x8F, 0x8F), (0xA1, 0xD0, 0x9E), (0x54, 0xC9, 0xE3)]
BOLT = [(58, 8), (28, 55), (47, 55), (35, 92), (75, 43), (54, 43), (70, 8)]
F_REG = "/usr/share/fonts/truetype/noto/NotoSansDisplay-Regular.ttf"
F_BOLD = "/usr/share/fonts/truetype/noto/NotoSansDisplay-Bold.ttf"
SS = 3  # supersampling of everything drawn here

# Areas of the 2.2.2 artwork (2000 x 961 pixels).
TILE = (104, 132, 566, 600)       # old icon tile incl. its border and glow
SUBTITLE = (630, 282, 1262, 458)  # "Spremljaj omrežnino / in porabo"
CHIP = (670, 612, 1310, 735)      # "Slovenija • Omrežnina"


def fill_from_surroundings(img, box, rounds=4):
    """Fills [box] with a smooth continuation of the pixels around it (normalized convolution, coarse to fine)."""
    w, h = img.size
    # Known pixels: everything outside [box] except bright text (title, chip), which must not bleed into the fill.
    gray = img.convert("L")
    mask = gray.point(lambda v: 0 if v > 110 else 255).filter(ImageFilter.MinFilter(9))
    ImageDraw.Draw(mask).rectangle(box, fill=0)
    out = img.copy()
    for radius in [90, 45, 22, 11][:rounds]:
        known = Image.composite(out, Image.new("RGB", (w, h)), mask)
        num = known.filter(ImageFilter.GaussianBlur(radius))
        den = mask.filter(ImageFilter.GaussianBlur(radius))
        # Start from the previous round: a finer round only refines pixels it can actually estimate.
        filled = out.copy()
        np_ = num.load(); dp = den.load(); fp = filled.load()
        x0, y0, x1, y1 = box
        for y in range(max(0, y0 - 2), min(h, y1 + 3)):
            for x in range(max(0, x0 - 2), min(w, x1 + 3)):
                d = dp[x, y] / 255.0
                if d > 0.08:
                    r, g, b = np_[x, y]
                    fp[x, y] = (min(255, int(r / d)), min(255, int(g / d)), min(255, int(b / d)))
        inner = Image.new("L", (w, h), 0)
        ImageDraw.Draw(inner).rectangle(box, fill=255)
        out = Image.composite(filled, out, inner)
    # Soft edge so the patch blends in.
    edge = Image.new("L", (w, h), 0)
    ImageDraw.Draw(edge).rectangle(box, fill=255)
    edge = edge.filter(ImageFilter.GaussianBlur(6))
    return Image.composite(out, img, edge)


def draw_arc(dr, cx, cy, r, w, i, col):
    a0 = -90 + i * 72 + 6
    a1 = a0 + 60
    dr.arc([cx - r, cy - r, cx + r, cy + r], a0, a1, fill=col, width=int(round(w)))
    rr = r - w / 2
    for a in (a0, a1):
        x = cx + rr * math.cos(math.radians(a))
        y = cy + rr * math.sin(math.radians(a))
        dr.ellipse([x - w / 2, y - w / 2, x + w / 2, y + w / 2], fill=col)


def logo_layer(size, d):
    """The 2.2.3 logo on a transparent square layer of [size] px, ring diameter [d]."""
    S = size * SS
    D = d * SS
    layer = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    base = D * 0.085
    r = D / 2 - base * 1.4
    cx = cy = S / 2
    glow = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    gd = ImageDraw.Draw(glow)
    for i, c in enumerate(COLORS):
        draw_arc(gd, cx, cy, r, base * 2.4, i, c + (120,))
    layer.alpha_composite(glow.filter(ImageFilter.GaussianBlur(base * 1.8)))
    dr = ImageDraw.Draw(layer)
    for i, c in enumerate(COLORS):
        draw_arc(dr, cx, cy, r, base, i, c + (255,))
    bs = r * 2 * 0.62
    s = bs / 100
    ox = cx - bs / 2
    oy = cy - bs / 2
    bolt = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    ImageDraw.Draw(bolt).polygon([(ox + x * s, oy + y * s) for x, y in BOLT], fill=(255, 255, 255, 255))
    layer.alpha_composite(Image.new("RGBA", (S, S), (255, 255, 255, 0)))
    glow_b = bolt.filter(ImageFilter.GaussianBlur(base * 0.9))
    glow_b.putalpha(glow_b.getchannel("A").point(lambda v: int(v * 0.45)))
    layer.alpha_composite(glow_b)
    layer.alpha_composite(bolt)
    return layer.resize((size, size), Image.LANCZOS)


def new_tile(img):
    x0, y0, x1, y1 = TILE
    w, h = x1 - x0, y1 - y0
    S = SS
    tile = Image.new("RGBA", (w * S, h * S), (0, 0, 0, 0))
    td = ImageDraw.Draw(tile)
    td.rounded_rectangle([0, 0, w * S - 1, h * S - 1], radius=86 * S, fill=(8, 11, 16, 255),
                         outline=(54, 70, 86, 255), width=3 * S)
    tile = tile.resize((w, h), Image.LANCZOS)
    # Soft shadow under the tile.
    shadow = Image.new("RGBA", img.size, (0, 0, 0, 0))
    ImageDraw.Draw(shadow).rounded_rectangle([x0 + 6, y0 + 14, x1 + 6, y1 + 14], radius=86, fill=(0, 0, 0, 150))
    img.alpha_composite(shadow.filter(ImageFilter.GaussianBlur(18)))
    img.alpha_composite(tile, (x0, y0))
    logo = logo_layer(min(w, h) - 40, (min(w, h) - 40) * 0.92)
    img.alpha_composite(logo, (x0 + (w - logo.width) // 2, y0 + (h - logo.height) // 2))


def block_dots(img, x, y):
    dr = ImageDraw.Draw(img)
    for i, c in enumerate(COLORS):
        cx = x + i * 46
        dr.ellipse([cx, y, cx + 26, y + 26], fill=c + (255,))


def chip(img, text):
    font = ImageFont.truetype(F_BOLD, 50)
    dr = ImageDraw.Draw(img)
    tw = dr.textlength(text, font=font)
    x0, y0 = 678, 622
    x1, y1 = int(x0 + tw + 96), 726
    layer = Image.new("RGBA", ((x1 - x0) * SS, (y1 - y0) * SS), (0, 0, 0, 0))
    ImageDraw.Draw(layer).rounded_rectangle([0, 0, (x1 - x0) * SS - 1, (y1 - y0) * SS - 1],
                                            radius=52 * SS, fill=(19, 124, 236, 255))
    img.alpha_composite(layer.resize((x1 - x0, y1 - y0), Image.LANCZOS), (x0, y0))
    dr.text((x0 + 48, y0 + 14), text, font=font, fill=(255, 255, 255, 255))


def subtitle(img, lines):
    font = ImageFont.truetype(F_REG, 66)
    dr = ImageDraw.Draw(img)
    for i, line in enumerate(lines):
        dr.text((654, 296 + i * 78), line, font=font, fill=(226, 232, 240, 255))


def to_play(img, path):
    """Play needs exactly 1024 x 500: crop the sides to that ratio, then scale."""
    w, h = img.size
    target_w = round(h * 1024 / 500)
    off = (w - target_w) // 2
    img.crop((off, 0, off + target_w, h)).convert("RGB").resize((1024, 500), Image.LANCZOS).save(path, optimize=True)


def build(lang):
    img = Image.open(SRC).convert("RGB")
    img = fill_from_surroundings(img, TILE)
    if lang == "en":
        img = fill_from_surroundings(img, SUBTITLE, rounds=2)
        img = fill_from_surroundings(img, CHIP)
    img = img.convert("RGBA")
    new_tile(img)
    if lang == "en":
        subtitle(img, ["Track network charges", "and your consumption"])
        chip(img, "Slovenia • Network charge")
    block_dots(img, 684, 772)
    os.makedirs(MASTER, exist_ok=True)
    suffix = "" if lang == "sl" else "_en"
    img.convert("RGB").save(os.path.join(MASTER, f"feature_graphic_2.2.3_2000{suffix}.png"), optimize=True)
    to_play(img, os.path.join(OUT, f"feature_graphic_2.2.3_1024x500{suffix}.png"))


if __name__ == "__main__":
    build("sl")
    build("en")
