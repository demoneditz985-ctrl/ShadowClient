#!/usr/bin/env python3
"""
Regenerate every Vortex logo asset from one source image.

Usage:
    python3 tools/apply_icon.py path/to/icon.png [--inset 0.06] [--zoom 1.0] [--dry-run]

What it writes
--------------
    app/src/main/res/drawable/vortex_logo.png      512x512, transparent outside the circle
    app/src/main/res/drawable/vortex.png           144x144
    app/src/main/res/drawable/img.png              1280x1280 legacy launcher icon
    app/src/main/res/drawable/ic_launcher_foreground.png  adaptive-icon foreground (432)
    app/src/main/res/mipmap-*/ic_launcher.webp      rounded-square launcher icon
    app/src/main/res/mipmap-*/ic_launcher_round.webp circular launcher icon
    images/vortex.png, images/vortex2.jpg           README/profile artwork

The in-app splash + floating overlay use `vortex_logo.png`, the launcher uses the
mipmaps, and the notification icon keeps the simple white mark (a photo icon is
illegible in the status bar).

--inset trims the source before masking (0.06 = drop 6% of the edge, handy for
artwork that ships with a white margin around its circular ring).
"""

from __future__ import annotations

import argparse
import math
import os
import sys

from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "app/src/main/res")

DENSITIES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}

# Vortex dark-violet base used behind the artwork
BG_INNER = (34, 18, 56)
BG_OUTER = (10, 6, 17)


def radial_background(size: int, radius_scale: float = 1.2, ss: int = 1) -> Image.Image:
    """Deep violet radial backdrop used behind the mark."""
    S = size * ss
    bg = Image.new("RGBA", (S, S), BG_OUTER + (255,))
    draw = ImageDraw.Draw(bg)
    cx = cy = S / 2
    maxr = math.hypot(cx, cy) * radius_scale
    steps = 64
    for i in range(steps, 0, -1):
        t = i / steps
        r = maxr * t
        col = (
            int(BG_INNER[0] + (BG_OUTER[0] - BG_INNER[0]) * t),
            int(BG_INNER[1] + (BG_OUTER[1] - BG_INNER[1]) * t),
            int(BG_INNER[2] + (BG_OUTER[2] - BG_INNER[2]) * t),
            255,
        )
        draw.ellipse([cx - r, cy - r, cx + r, cy + r], fill=col)
    return bg.resize((size, size), Image.LANCZOS) if ss != 1 else bg


def circular_mark(source: Image.Image, size: int, inset: float, zoom: float) -> Image.Image:
    """Center-crop the source, zoom, then mask it into a circle with soft edges."""
    src = source.convert("RGBA")
    side = min(src.size)
    left = (src.width - side) // 2
    top = (src.height - side) // 2
    src = src.crop((left, top, left + side, top + side))

    if zoom != 1.0:
        keep = int(side / zoom)
        off = (side - keep) // 2
        src = src.crop((off, off, off + keep, off + keep))

    src = src.resize((size, size), Image.LANCZOS)

    mask = Image.new("L", (size * 4, size * 4), 0)
    pad = int(size * 4 * inset / 2)
    ImageDraw.Draw(mask).ellipse([pad, pad, size * 4 - pad, size * 4 - pad], fill=255)
    mask = mask.resize((size, size), Image.LANCZOS).filter(ImageFilter.GaussianBlur(size * 0.004))

    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    out.paste(src, (0, 0), mask)
    return out


def save(img: Image.Image, path: str, fmt: str | None = None, quality: int = 95) -> None:
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path, format=fmt, quality=quality)
    print(f"  wrote {os.path.relpath(path, ROOT):<58} {img.size[0]}x{img.size[1]}")


def build(source_path: str, inset: float, zoom: float, dry_run: bool) -> None:
    source = Image.open(source_path)
    print(f"source: {source_path} ({source.width}x{source.height} {source.mode})")

    def target(path: str) -> str:
        return os.path.join("/tmp/vortex-icon-dryrun", os.path.relpath(path, ROOT)) if dry_run else path

    # 1. transparent circular mark (splash + floating overlay)
    mark512 = circular_mark(source, 512, inset, zoom)
    save(mark512, target(os.path.join(RES, "drawable/vortex_logo.png")))
    save(circular_mark(source, 144, inset, zoom), target(os.path.join(RES, "drawable/vortex.png")))

    # 2. adaptive-icon foreground: mark inside the 66% safe zone, transparent
    fg = Image.new("RGBA", (432, 432), (0, 0, 0, 0))
    inner = circular_mark(source, 276, inset, zoom)
    fg.paste(inner, ((432 - 276) // 2, (432 - 276) // 2), inner)
    save(fg, target(os.path.join(RES, "drawable/ic_launcher_foreground.png")))

    # 3. legacy launcher icon (dark violet backdrop + mark)
    def launcher(size: int) -> Image.Image:
        icon = radial_background(size)
        art = circular_mark(source, int(size * 0.86), inset, zoom)
        alpha = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        off = (size - art.size[0]) // 2
        alpha.paste(art, (off, off), art)
        return Image.alpha_composite(icon, alpha)

    save(launcher(1280), target(os.path.join(RES, "drawable/img.png")))
    save(launcher(1280).convert("RGB"), target(os.path.join(ROOT, "images/vortex2.jpg")), quality=92)
    save(mark512, target(os.path.join(ROOT, "images/vortex.png")))

    # 4. mipmaps: rounded square + circle
    for name, size in DENSITIES.items():
        icon = launcher(size)
        rounded = icon.copy()
        mask = Image.new("L", (size, size), 0)
        ImageDraw.Draw(mask).rounded_rectangle([0, 0, size - 1, size - 1], radius=int(size * 0.22), fill=255)
        rounded.putalpha(mask)
        save(rounded, target(os.path.join(RES, f"mipmap-{name}/ic_launcher.webp")))

        circle = icon.copy()
        cmask = Image.new("L", (size, size), 0)
        ImageDraw.Draw(cmask).ellipse([0, 0, size - 1, size - 1], fill=255)
        circle.putalpha(cmask)
        save(circle, target(os.path.join(RES, f"mipmap-{name}/ic_launcher_round.webp")))

    print("\ndone - the notification icon (ic_notification.png) is intentionally left as the"
          "\nsimple white mark, which stays legible in the status bar.")


def main() -> int:
    parser = argparse.ArgumentParser(description="Apply a source image as the Vortex logo everywhere.")
    parser.add_argument("source", help="image file (png/jpg/webp)")
    parser.add_argument("--inset", type=float, default=0.06, help="trim edge before circular mask (0-0.4)")
    parser.add_argument("--zoom", type=float, default=1.0, help="1.0 = fit, 1.1 = 10%% closer crop")
    parser.add_argument("--dry-run", action="store_true", help="write to /tmp instead of the repo")
    args = parser.parse_args()

    if not os.path.isfile(args.source):
        print(f"error: {args.source} not found", file=sys.stderr)
        return 1
    if not (0.0 <= args.inset < 0.4):
        print("error: --inset must be between 0 and 0.4", file=sys.stderr)
        return 1
    if args.zoom < 1.0:
        print("error: --zoom must be >= 1.0", file=sys.stderr)
        return 1

    build(args.source, args.inset, args.zoom, args.dry_run)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
