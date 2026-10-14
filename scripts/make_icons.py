"""FIRSAT360 uygulama simgesi ve açılış logosu üretir (özgün tasarım). Kullanım: python scripts/make_icons.py"""
import sys
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont, ImageFilter

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "android-app/app/src/main/res"
FONT = "/usr/share/fonts/opentype/inter/Inter-Bold.otf"
S = 1024

def lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))

def base(round_shape: bool):
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    grad = Image.new("RGBA", (S, S))
    c1, c2 = (67, 56, 202), (139, 92, 246)
    px = grad.load()
    for y in range(S):
        for x in range(S):
            t = min(1.0, (x * 0.45 + y * 0.55) / S)
            px[x, y] = lerp(c1, c2, t) + (255,)
    mask = Image.new("L", (S, S), 0)
    md = ImageDraw.Draw(mask)
    if round_shape:
        md.ellipse((0, 0, S - 1, S - 1), fill=255)
    else:
        md.rounded_rectangle((0, 0, S - 1, S - 1), radius=int(S * 0.23), fill=255)
    img.paste(grad, (0, 0), mask)
    return img

def mark(img, scale=1.0, cx=None, cy=None):
    d = ImageDraw.Draw(img)
    cx = cx or S // 2; cy = cy or S // 2
    r = int(S * 0.30 * scale); w = int(S * 0.055 * scale)
    # 360 halkası: 300 derecelik yay + uçta amber "fırsat" noktası
    d.arc((cx - r, cy - r, cx + r, cy + r), start=-60, end=240, fill=(255, 255, 255, 255), width=w)
    import math
    ang = math.radians(-60)
    dx, dy = cx + r * math.cos(ang), cy + r * math.sin(ang)
    dr = int(w * 0.95)
    d.ellipse((dx - dr, dy - dr, dx + dr, dy + dr), fill=(255, 196, 61, 255))
    font = ImageFont.truetype(FONT, int(S * 0.36 * scale))
    d.text((cx, cy + int(S * 0.01 * scale)), "F", font=font, fill=(255, 255, 255, 255), anchor="mm")
    return img

def save_sizes(img, name):
    for folder, px in {"mipmap-mdpi": 48, "mipmap-hdpi": 72, "mipmap-xhdpi": 96, "mipmap-xxhdpi": 144, "mipmap-xxxhdpi": 192}.items():
        out = RES / folder
        out.mkdir(parents=True, exist_ok=True)
        img.resize((px, px), Image.LANCZOS).save(out / f"{name}.png", optimize=True)

def main():
    icon = mark(base(False))
    save_sizes(icon, "ic_launcher")
    save_sizes(mark(base(True)), "ic_launcher_round")
    # Açılış logosu (şeffaf zemin üzerine simge)
    for folder, px in {"drawable-xhdpi": 240, "drawable-xxhdpi": 360, "drawable-xxxhdpi": 480}.items():
        out = RES / folder
        out.mkdir(parents=True, exist_ok=True)
        icon.resize((px, px), Image.LANCZOS).save(out / "splash_logo.png", optimize=True)
    icon.resize((512, 512), Image.LANCZOS).save(ROOT / "docs" / "icon-512.png")
    print("ok")

if __name__ == "__main__":
    main()
