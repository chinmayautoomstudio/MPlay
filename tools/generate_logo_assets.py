"""Builds the in-app logo assets from the master logo:
     python tools/generate_logo_assets.py [path/to/logo.png]
   Writes app/src/main/res/drawable-nodpi/{splash_logo.webp, ic_mp3studio_logo.webp}.
   The launcher icons come from Android Studio's Image Asset wizard and are not touched here.

   The master is a 1000x1000 glowing rounded tile on a dark backdrop; TILE and TILE_RADIUS
   describe where the tile sits in it. Update them if the master's framing changes.
"""

import os
import sys

import numpy as np
from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), "..")
DEFAULT_LOGO = os.path.join(ROOT, "logos", "mp3-studio-logo-v1.png")
OUT_DIR = os.path.join(ROOT, "app", "src", "main", "res", "drawable-nodpi")

MASTER_SIZE = 1000
TILE = (268, 275, 727, 720)  # left, top, right, bottom, neon border included
TILE_RADIUS = 140
# Must match splash_screen_background in colors.xml and ic_launcher_background.xml.
BACKDROP = np.array([3, 0, 19], dtype=np.float64)

SPLASH_SIZE = 1152  # 288dp at 4x; Android shows the inner circle of 2/3 the width
SPLASH_FADE = (290.0, 372.0)  # radius where the glow starts and finishes fading into BACKDROP

MARK_CROP = (180, 180, 820, 820)
MARK_SIZE = 512
GLOW_REACH = 85.0  # master pixels beyond the tile edge where the glow reaches zero


def smoothstep(edge0, edge1, x):
    t = np.clip((x - edge0) / (edge1 - edge0), 0.0, 1.0)
    return t * t * (3 - 2 * t)


def tile_distance(xs, ys):
    """Signed distance from each pixel to the rounded tile edge (negative inside)."""
    left, top, right, bottom = TILE
    cx, cy = (left + right) / 2, (top + bottom) / 2
    hx, hy = (right - left) / 2 - TILE_RADIUS, (bottom - top) / 2 - TILE_RADIUS
    qx = np.abs(xs - cx) - hx
    qy = np.abs(ys - cy) - hy
    outside = np.hypot(np.maximum(qx, 0), np.maximum(qy, 0))
    inside = np.minimum(np.maximum(qx, qy), 0)
    return outside + inside - TILE_RADIUS


def splash(master):
    canvas = np.empty((SPLASH_SIZE, SPLASH_SIZE, 3))
    canvas[:] = BACKDROP
    offset = (SPLASH_SIZE - MASTER_SIZE) // 2
    canvas[offset:offset + MASTER_SIZE, offset:offset + MASTER_SIZE] = master
    ys, xs = np.mgrid[0:SPLASH_SIZE, 0:SPLASH_SIZE]
    centre = SPLASH_SIZE / 2
    keep = 1 - smoothstep(*SPLASH_FADE, np.hypot(xs - centre, ys - centre))
    out = canvas * keep[..., None] + BACKDROP * (1 - keep[..., None])
    return Image.fromarray(np.uint8(np.clip(out, 0, 255).round()), "RGB")


def mark(master):
    left, top, right, bottom = MARK_CROP
    ys, xs = np.mgrid[top:bottom, left:right].astype(np.float64)
    rgb = master[top:bottom, left:right]
    distance = tile_distance(xs, ys)
    tile = 1 - smoothstep(-1.5, 1.5, distance)
    # Outside the tile the backdrop is dark, so brightness is glow: lift it into alpha.
    peak = rgb.max(axis=2)
    glow_alpha = (peak / 255) * (1 - smoothstep(0, GLOW_REACH, distance))
    glow_rgb = np.where(peak[..., None] > 0, rgb * (255 / np.maximum(peak, 1))[..., None], 0)
    alpha = np.maximum(tile, glow_alpha)
    colour = rgb * tile[..., None] + glow_rgb * (1 - tile[..., None])
    rgba = np.dstack([colour, alpha * 255])
    image = Image.fromarray(np.uint8(np.clip(rgba, 0, 255).round()), "RGBA")
    return image.resize((MARK_SIZE, MARK_SIZE), Image.LANCZOS)


def main():
    path = sys.argv[1] if len(sys.argv) > 1 else DEFAULT_LOGO
    image = Image.open(path).convert("RGB")
    if image.size != (MASTER_SIZE, MASTER_SIZE):
        image = image.resize((MASTER_SIZE, MASTER_SIZE), Image.LANCZOS)
    master = np.asarray(image, dtype=np.float64)
    os.makedirs(OUT_DIR, exist_ok=True)
    splash(master).save(os.path.join(OUT_DIR, "splash_logo.webp"), quality=92, method=6)
    mark(master).save(os.path.join(OUT_DIR, "ic_mp3studio_logo.webp"), quality=92, method=6)
    print("Wrote splash_logo.webp and ic_mp3studio_logo.webp to", os.path.normpath(OUT_DIR))


if __name__ == "__main__":
    main()
