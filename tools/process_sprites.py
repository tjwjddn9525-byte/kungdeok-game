#!/usr/bin/env python3
"""raw/ 의 원본 그림을 앱에서 쓸 스프라이트로 정리한다 (SPEC 3-3).

1. 바깥 테두리에서부터 흰색을 채워 투명으로 만든다 (캐릭터 안쪽 흰 털은 유지).
2. 여백을 잘라낸다.
3. sprites/large/ : 긴 변 512px (도감·진화 연출용)
   sprites/small/ : 단계별 오버레이 크기(dp) x SMALL_SCALE px, nearest-neighbor 축소

사용법: python3 tools/process_sprites.py   (저장소 루트에서 실행, Pillow 필요)
"""
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
RAW = ROOT / "raw"
OUT_LARGE = ROOT / "sprites" / "large"
OUT_SMALL = ROOT / "sprites" / "small"

LARGE_SIZE = 512
# 오버레이 그림은 dp 크기의 3배(xxhdpi 기준) 픽셀로 저장하고, 앱에서 dp 크기로 nearest 스케일한다.
SMALL_SCALE = 3
# 흰 배경으로 볼 색 차이 허용치 (RGB 채널 차이의 합)
WHITE_THRESH = 60

STAGE_DP = {1: 40, 2: 52, 3: 64, 4: 76, 5: 88, "fusion": 100}


def stage_of(name: str):
    if name.startswith("fusion"):
        return "fusion"
    return int(name.split("_")[1])


def remove_background(img: Image.Image) -> Image.Image:
    rgba = img.convert("RGBA")
    w, h = rgba.size
    # 1px 흰 테두리를 덧대서 바깥 전체가 하나로 이어지게 한 뒤 (0,0)에서 채운다.
    padded = Image.new("RGBA", (w + 2, h + 2), (255, 255, 255, 255))
    padded.paste(rgba, (1, 1))
    ImageDraw.floodfill(padded, (0, 0), (255, 255, 255, 0), thresh=WHITE_THRESH)
    out = padded.crop((1, 1, w + 1, h + 1))
    # 투명 영역의 색은 0으로 정리 (축소 시 흰 테두리 번짐 방지)
    px = out.load()
    for y in range(h):
        for x in range(w):
            if px[x, y][3] == 0:
                px[x, y] = (0, 0, 0, 0)
    return out


def trim(img: Image.Image) -> Image.Image:
    bbox = img.getchannel("A").getbbox()
    return img.crop(bbox) if bbox else img


def fit(img: Image.Image, long_side: int, resample) -> Image.Image:
    w, h = img.size
    s = long_side / max(w, h)
    return img.resize((max(1, round(w * s)), max(1, round(h * s))), resample)


def main():
    OUT_LARGE.mkdir(parents=True, exist_ok=True)
    OUT_SMALL.mkdir(parents=True, exist_ok=True)
    for src in sorted(RAW.glob("*.png")):
        name = src.stem
        clean = trim(remove_background(Image.open(src)))
        fit(clean, LARGE_SIZE, Image.LANCZOS).save(OUT_LARGE / src.name, optimize=True)
        small_px = STAGE_DP[stage_of(name)] * SMALL_SCALE
        fit(clean, small_px, Image.NEAREST).save(OUT_SMALL / src.name, optimize=True)
        print(f"{name}: trimmed {clean.size} -> small {small_px}px")


if __name__ == "__main__":
    main()
