"""
从 Key-Store 原图生成各尺寸应用/托盘图标（仅缩放，保留透明通道）。
运行: python scripts/process-logo.py [可选: 源文件路径]

优先使用:
  1. 命令行参数指定的 PNG
  2. assets/logo.png
  3. assets/logo-upload.png
  4. assets/logo-source.png
"""
from __future__ import annotations

import sys
from collections import deque
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "assets"
LOGO = ASSETS / "logo.png"
UPLOAD = ASSETS / "logo-upload.png"


def luminance(r: int, g: int, b: int) -> float:
    return 0.299 * r + 0.587 * g + 0.114 * b


def has_real_transparency(img: Image.Image) -> bool:
    """判断是否已有有效透明像素。"""
    if img.mode in ("RGBA", "LA") and "A" in img.getbands():
        alpha = img.getchannel("A")
        return any(a < 250 for a in alpha.getdata())
    return False


def restore_flattened_transparency(img: Image.Image, threshold: int = 40) -> Image.Image:
    """
    聊天上传的 PNG 常被压成 RGB 黑底；从四角泛洪将深色背景恢复为透明。
    若原图本身带 alpha 通道则原样保留。
    """
    rgba = img.convert("RGBA")
    if has_real_transparency(rgba):
        return rgba

    w, h = rgba.size
    px = rgba.load()
    visited = bytearray(w * h)
    q = deque()

    def idx(x: int, y: int) -> int:
        return y * w + x

    def is_bg(x: int, y: int) -> bool:
        r, g, b, _a = px[x, y]
        return max(r, g, b) <= threshold or luminance(r, g, b) <= threshold

    for x in range(w):
        q.append((x, 0))
        q.append((x, h - 1))
    for y in range(h):
        q.append((0, y))
        q.append((w - 1, y))

    while q:
        x, y = q.popleft()
        i = idx(x, y)
        if visited[i] or not is_bg(x, y):
            continue
        visited[i] = 1
        px[x, y] = (r := px[x, y][0], px[x, y][1], px[x, y][2], 0)
        for nx, ny in ((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)):
            if 0 <= nx < w and 0 <= ny < h:
                q.append((nx, ny))

    return rgba


def resize_square(img: Image.Image, size: int) -> Image.Image:
    """等比缩放为指定边长的正方形，保留透明通道。"""
    rgba = img.convert("RGBA")
    return rgba.resize((size, size), Image.Resampling.LANCZOS)


def save_png(img: Image.Image, path: Path) -> None:
    """保存带 alpha 的 PNG。"""
    img.save(path, format="PNG", optimize=True)


def save_ico(img: Image.Image, path: Path, sizes: list[int]) -> None:
    """保存带透明通道的多尺寸 ICO。"""
    img.convert("RGBA").save(
        path,
        format="ICO",
        sizes=[(s, s) for s in sizes],
    )


def export_icons(source: Path) -> None:
    ASSETS.mkdir(parents=True, exist_ok=True)

    raw = Image.open(source)
    img = restore_flattened_transparency(raw)

    save_png(img, ASSETS / "logo-source.png")

    app256 = resize_square(img, 256)
    tray32 = resize_square(img, 32)
    tray16 = resize_square(img, 16)

    save_png(app256, ASSETS / "app-icon.png")
    save_png(tray32, ASSETS / "tray-icon.png")
    save_png(tray16, ASSETS / "tray-icon-16.png")

    save_ico(app256, ASSETS / "app-icon.ico", [256, 48, 32, 16])
    save_ico(tray32, ASSETS / "tray-icon.ico", [32, 16])

    alpha_count = sum(1 for a in img.getchannel("A").getdata() if a < 10)
    print(f"Exported from: {source.name}")
    print(f"  transparent pixels: {alpha_count} / {img.width * img.height}")
    for name in (
        "logo-source.png",
        "app-icon.png",
        "tray-icon.png",
        "tray-icon-16.png",
        "app-icon.ico",
        "tray-icon.ico",
    ):
        p = ASSETS / name
        print(f"  {name}: {p.stat().st_size} bytes")


def resolve_source() -> Path:
    if len(sys.argv) > 1:
        p = Path(sys.argv[1])
        if p.exists():
            return p
        raise FileNotFoundError(p)
    for candidate in (LOGO, UPLOAD, ASSETS / "logo-source.png"):
        if candidate.exists():
            return candidate
    raise FileNotFoundError(
        "未找到图标源文件。请将透明 PNG 放到 assets/logo.png 后重试。"
    )


def main() -> None:
    export_icons(resolve_source())


if __name__ == "__main__":
    main()
