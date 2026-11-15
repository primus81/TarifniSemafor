"""Renders the Tarifni semafor logo (ring of the five official block colours + bolt) for Google Play.

Usage: python3 store_assets/render_store_graphics.py store_assets
Output: google_play_512.png (app icon, 512 x 512), feature_graphic_1024x500.png (Slovenian) and
feature_graphic_1024x500_en.png (English). Needs Pillow and the Lato font.
"""
import math, sys
from PIL import Image, ImageDraw, ImageFont, ImageFilter

OUT = sys.argv[1]
SS = 4  # supersampling
COLORS = [(0xF1,0x69,0x0F),(0xF9,0xC2,0x12),(0x8F,0x8F,0x8F),(0xA1,0xD0,0x9E),(0x54,0xC9,0xE3)]
BG = (0x12,0x17,0x1F)
BOLT = [(58,8),(28,55),(47,55),(35,92),(75,43),(54,43),(70,8)]
FONT_B = "/usr/share/fonts/truetype/lato/Lato-Bold.ttf"
FONT_R = "/usr/share/fonts/truetype/lato/Lato-Regular.ttf"

def logo(img, cx, cy, d, bolt=(255,255,255), glow=True):
    base = d*0.085; r = d/2 - base*1.4
    if glow:
        g = Image.new("RGBA", img.size, (0,0,0,0)); gd = ImageDraw.Draw(g)
        for i,c in enumerate(COLORS):
            draw_arc(gd, cx, cy, r, base*2.2, i, c+(110,))
        g = g.filter(ImageFilter.GaussianBlur(base*1.6))
        img.alpha_composite(g)
    dr = ImageDraw.Draw(img)
    for i,c in enumerate(COLORS):
        draw_arc(dr, cx, cy, r, base, i, c+(255,))
    size = r*2*0.62; s = size/100; ox = cx-size/2; oy = cy-size/2
    dr.polygon([(ox+x*s, oy+y*s) for x,y in BOLT], fill=bolt+(255,))

def draw_arc(dr, cx, cy, r, w, i, col):
    a0 = -90+i*72+6; a1 = a0+60
    box = [cx-r, cy-r, cx+r, cy+r]
    dr.arc(box, a0, a1, fill=col, width=int(round(w)))
    # Round caps.
    for a in (a0, a1):
        x = cx + r*math.cos(math.radians(a)) - 0.0; y = cy + r*math.sin(math.radians(a))
        # PIL draws the arc stroke inward from the box; centre of the stroke is at r - w/2.
        rr = r - w/2
        x = cx + rr*math.cos(math.radians(a)); y = cy + rr*math.sin(math.radians(a))
        dr.ellipse([x-w/2, y-w/2, x+w/2, y+w/2], fill=col)

def icon512():
    S = 512*SS
    img = Image.new("RGBA", (S,S), BG+(255,))
    logo(img, S/2, S/2, S*0.80)
    img.resize((512,512), Image.LANCZOS).save(f"{OUT}/google_play_512.png", optimize=True)  # 32-bit PNG, as Google Play asks

def feature(lang):
    W,H = 1024*SS, 500*SS
    img = Image.new("RGBA", (W,H), BG+(255,))
    # Soft background dots in the five block colours.
    bg = Image.new("RGBA", (W,H), (0,0,0,0)); bd = ImageDraw.Draw(bg)
    for (x,y,rad),c in zip([(880,80,170),(960,330,150),(110,430,120),(720,470,90),(560,40,70)], COLORS):
        bd.ellipse([(x-rad)*SS,(y-rad)*SS,(x+rad)*SS,(y+rad)*SS], fill=c+(38,))
    img.alpha_composite(bg.filter(ImageFilter.GaussianBlur(40*SS)))
    # Icon tile.
    tile = Image.new("RGBA", (W,H), (0,0,0,0)); td = ImageDraw.Draw(tile)
    td.rounded_rectangle([52*SS,140*SS,272*SS,360*SS], radius=46*SS, fill=(0x0B,0x0F,0x15,255), outline=(0x2F,0x39,0x46,255), width=2*SS)
    img.alpha_composite(tile)
    logo(img, 162*SS, 250*SS, 190*SS)
    d = ImageDraw.Draw(img)
    title = "Tarifni semafor"
    sub = ["Sledi časovnim blokom", "in svoji porabi"] if lang=="sl" else ["Follow the time blocks", "and your own consumption"]
    chip = "Slovenija • Omrežnina" if lang=="sl" else "Slovenia • Network charge"
    d.text((320*SS,150*SS), title, font=ImageFont.truetype(FONT_B, 76*SS), fill=(255,255,255,255))
    f2 = ImageFont.truetype(FONT_R, 30*SS)
    d.text((322*SS,252*SS), sub[0], font=f2, fill=(0xC8,0xD2,0xDC,255))
    d.text((322*SS,292*SS), sub[1], font=f2, fill=(0xC8,0xD2,0xDC,255))
    f3 = ImageFont.truetype(FONT_B, 22*SS)
    tw = d.textlength(chip, font=f3)
    d.rounded_rectangle([320*SS,356*SS,320*SS+tw+44*SS,402*SS], radius=23*SS, fill=(0x00,0x65,0x8F,255))
    d.text((342*SS,366*SS), chip, font=f3, fill=(255,255,255,255))
    # Five small block dots under the chip.
    for i,c in enumerate(COLORS):
        x = 322*SS + i*26*SS
        d.ellipse([x, 424*SS, x+14*SS, 438*SS], fill=c+(255,))
    name = "feature_graphic_1024x500.png" if lang=="sl" else "feature_graphic_1024x500_en.png"
    img.resize((1024,500), Image.LANCZOS).convert("RGB").save(f"{OUT}/{name}", optimize=True)

def launcher_preview():
    """How the adaptive icon looks under a circular mask (for checking only)."""
    S = 432*SS
    img = Image.new("RGBA", (S,S), BG+(255,))
    logo(img, S/2, S/2, S*66/108, glow=False)
    mask = Image.new("L", (S,S), 0); ImageDraw.Draw(mask).ellipse([S*0.09,S*0.09,S*0.91,S*0.91], fill=255)
    out = Image.new("RGBA", (S,S), (255,255,255,255)); out.paste(img, (0,0), mask)
    out.resize((216,216), Image.LANCZOS).save(f"{OUT}/launcher_preview.png")

icon512(); feature("sl"); feature("en")
