#!/usr/bin/env python3
"""
Generate Hytale-aesthetic 9-patch background textures for the AI Companion UI.

Outputs (in Common/UI/Custom/):
  bg@2x.png      — dark navy panel (64×64 @2x, use Border: 8 in .ui)
  overlay@2x.png — full-screen dim overlay (16×16 @2x, use Border: 2 in .ui)
"""

import os
from PIL import Image, ImageDraw

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(SCRIPT_DIR, "app", "src", "main", "resources", "Common", "UI", "Custom")
os.makedirs(OUT, exist_ok=True)


# ── Hytale dark UI palette ──────────────────────────────────────────────────
NAVY_FILL   = (11,  11,  28, 240)   # #0b0b1c, ~94% — panel body
BORDER_OUT  = (52,  76, 124, 255)   # #344c7c — outer 2px frame
BORDER_IN   = (32,  48,  82, 180)   # #20304e — inner soft frame
BORDER_TOP  = (72, 100, 160, 200)   # top-edge highlight (gives depth)
GOLD        = (232, 169,  59, 230)  # #E8A93B — Hytale gold accent
GOLD_DARK   = (140,  96,  28, 180)  # inner gold shadow
BLACK_DIM   = (0,    0,   8, 173)   # near-black ~68% — screen overlay


def save(img: Image.Image, name: str) -> None:
    path = os.path.join(OUT, name)
    img.save(path)
    w, h = img.size
    print(f"  ✓  {name:25s}  {w}×{h} RGBA  →  {path}")


# ── bg@2x.png — 64×64 dark navy 9-patch panel ──────────────────────────────
# 32×32 logical, Border: 8 logical (= 16px in this @2x file)
# Corner zone: outer 16×16 px of each corner — all decoration stays here.
SZ = 64

bg = Image.new("RGBA", (SZ, SZ), (0, 0, 0, 0))
d = ImageDraw.Draw(bg)

# Body fill
d.rectangle([0, 0, SZ - 1, SZ - 1], fill=NAVY_FILL)

# Outer border frame (2px)
d.rectangle([0, 0, SZ - 1, SZ - 1], outline=BORDER_OUT, width=2)

# Inner frame (1px, softer)
d.rectangle([2, 2, SZ - 3, SZ - 3], outline=BORDER_IN, width=1)

# Top highlight line — gives the panel a subtle lit-from-above look
d.line([(3, 3), (SZ - 4, 3)], fill=BORDER_TOP, width=1)

# Gold corner ornaments — L-shaped bracket in each corner
# Each ornament fits inside the 16px corner zone
BRACKET = 6  # arm length in px
GAP = 4      # offset from edge

for (x0, y0, dx, dy) in [
    (GAP,       GAP,       +1, +1),   # top-left
    (SZ-GAP-1,  GAP,       -1, +1),   # top-right
    (GAP,       SZ-GAP-1,  +1, -1),   # bottom-left
    (SZ-GAP-1,  SZ-GAP-1,  -1, -1),   # bottom-right
]:
    # Horizontal arm
    pts_h = [(x0, y0), (x0 + dx * BRACKET, y0)]
    # Vertical arm
    pts_v = [(x0, y0), (x0, y0 + dy * BRACKET)]
    d.line(pts_h, fill=GOLD, width=2)
    d.line(pts_v, fill=GOLD, width=2)
    # Inner shadow line (1px inward, darker gold)
    d.line([(x0 + dx, y0 + dy), (x0 + dx * BRACKET, y0 + dy)], fill=GOLD_DARK, width=1)
    d.line([(x0 + dx, y0 + dy), (x0 + dx, y0 + dy * BRACKET)], fill=GOLD_DARK, width=1)

save(bg, "bg@2x.png")


# ── overlay@2x.png — 16×16 full-screen dim ─────────────────────────────────
# 8×8 logical, Border: 2 logical — simple flat semi-transparent black
OV = 16
overlay = Image.new("RGBA", (OV, OV), BLACK_DIM)
save(overlay, "overlay@2x.png")


print()
print("All textures created. .ui files already reference these names.")
print("Border values used: bg → 8, overlay → 2")
