"""Instantiate static weight TTFs from variable Google Fonts for bundling in res/font."""

from __future__ import annotations

import os
from pathlib import Path

from fontTools.ttLib import TTFont
from fontTools.varLib.instancer import instantiateVariableFont

TMP = Path(os.environ.get("TEMP", "/tmp")) / "mplay_fonts"
OUT = Path("app/src/main/res/font")


def instantiate(src_name: str, dest_prefix: str, weights: list[tuple[int, str]], axes_extra: dict | None = None) -> None:
    src = TMP / src_name
    for weight, label in weights:
        font = TTFont(src)
        partial = {"wght": float(weight)}
        if axes_extra:
            partial.update(axes_extra)
        instantiateVariableFont(font, partial, inplace=True, overlap=True)
        dest = OUT / f"{dest_prefix}_{label}.ttf"
        font.save(dest)
        print(f"wrote {dest} ({dest.stat().st_size} bytes)")


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    instantiate(
        "dmsans_var.ttf",
        "dm_sans",
        [(400, "regular"), (500, "medium"), (600, "semibold"), (700, "bold")],
        axes_extra={"opsz": 14.0},
    )
    instantiate(
        "outfit_var.ttf",
        "outfit",
        [
            (400, "regular"),
            (500, "medium"),
            (600, "semibold"),
            (700, "bold"),
            (800, "extrabold"),
        ],
    )
    print("done")


if __name__ == "__main__":
    main()
