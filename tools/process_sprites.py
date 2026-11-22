#!/usr/bin/env python3
"""raw/ 의 원본 그림을 앱에서 쓸 스프라이트로 정리한다 (SPEC 3-3).

1. 바깥 테두리에서부터 흰색을 채워 투명으로 만든다 (캐릭터 안쪽 흰 털은 유지).
2. 여백을 잘라낸다.
3. sprites/large/ : 긴 변 512px (도감·진화 연출용)
   sprites/small/ : 단계별 오버레이 크기(dp) x SMALL_SCALE px, nearest-neighbor 축소

4. 오버레이 애니메이션 프레임 (SPEC 8, 2단계): sprites/small/<이름>_walk1.png, _walk2.png, _sleep.png
   - raw/<이름>_walk1.png 처럼 직접 그린 프레임이 있으면 그것을 쓴다.
   - 없으면 원본 그림에서 만든다: 걷기 = 다리 쪽(아래 30%)을 좌우로 번갈아 기울임,
     잠자기 = 납작하게 웅크린 자세(세로 82%, 가로 106%)에 살짝 어둡게.

사용법: python3 tools/process_sprites.py   (저장소 루트에서 실행, Pillow 필요)
"""
from pathlib import Path

from PIL import Image, ImageDraw, ImageEnhance

FRAMES = ("walk1", "walk2", "sleep")
LEG_SPLIT = 0.70   # 이 높이 아래를 다리로 본다
LEG_SHEAR = 0.16   # 다리 기울기

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


def walk_frame(img: Image.Image, direction: int) -> Image.Image:
    """다리 쪽을 [direction] 방향으로 기울인다. (통통 튀는 움직임은 앱에서 따로 준다)"""
    w, h = img.size
    split = int(h * LEG_SPLIT)
    out = Image.new("RGBA", img.size, (0, 0, 0, 0))
    out.alpha_composite(img.crop((0, 0, w, split)), (0, 0))
    legs = img.crop((0, split, w, h))
    k = LEG_SHEAR * direction
    # 출력 (x, y) 는 입력 (x - k*y, y) 를 본다: 윗줄은 그대로, 발끝으로 갈수록 밀림
    sheared = legs.transform(legs.size, Image.AFFINE, (1, -k, 0, 0, 1, 0), resample=Image.NEAREST)
    out.alpha_composite(sheared, (0, split))
    return out


def sleep_frame(img: Image.Image) -> Image.Image:
    w, h = img.size
    nw, nh = int(w * 1.06), int(h * 0.82)
    squashed = img.resize((nw, nh), Image.NEAREST)
    rgb = ImageEnhance.Brightness(squashed.convert("RGB")).enhance(0.88).convert("RGBA")
    rgb.putalpha(squashed.getchannel("A"))
    out = Image.new("RGBA", (nw, h), (0, 0, 0, 0))
    out.alpha_composite(rgb, (0, h - nh))
    return out


def mask_iou(a: Image.Image, b: Image.Image) -> float:
    """두 그림의 실루엣(불투명한 곳)이 얼마나 겹치는지"""
    import numpy as np
    size = (64, 64)
    ma = np.array(a.resize(size, Image.NEAREST).getchannel("A")) > 0
    mb = np.array(b.resize(size, Image.NEAREST).getchannel("A")) > 0
    return (ma & mb).sum() / max(1, (ma | mb).sum())


def make_frames(name: str, clean: Image.Image, small_px: int):
    base_scale = small_px / max(clean.size)
    base_small = fit(clean, small_px, Image.NEAREST)
    drawn = {f: RAW / f"{name}_{f}.png" for f in FRAMES if (RAW / f"{name}_{f}.png").exists()}

    # 직접 그린 프레임: 서 있는 키(걷기1)가 원래 그림과 같아지도록 한 시트 전체를 같은 비율로 줄인다
    drawn_scale = None
    if "walk1" in drawn:
        w1 = trim(remove_background(Image.open(drawn["walk1"])))
        drawn_scale = base_small.height / w1.height

    for frame in FRAMES:
        if frame in drawn and drawn_scale is not None:
            img = trim(remove_background(Image.open(drawn[frame])))
            size = (max(1, round(img.width * drawn_scale)), max(1, round(img.height * drawn_scale)))
            img = img.resize(size, Image.LANCZOS)
            # 걷기 프레임이 원래 그림과 반대쪽을 보고 있으면 뒤집는다 (프레임이 바뀔 때 홱 돌지 않게)
            if frame != "sleep":
                flipped = img.transpose(Image.FLIP_LEFT_RIGHT)
                if mask_iou(flipped, base_small) > mask_iou(img, base_small) + 0.02:
                    img = flipped
                    print(f"  {name}_{frame}: 방향 뒤집음")
            img.save(OUT_SMALL / f"{name}_{frame}.png", optimize=True)
            continue
        if frame == "sleep":
            img = sleep_frame(clean)
        else:
            img = walk_frame(clean, 1 if frame == "walk1" else -1)
        # 기본 그림과 같은 비율로 줄여야 프레임이 바뀔 때 크기가 튀지 않는다.
        size = (max(1, round(img.width * base_scale)), max(1, round(img.height * base_scale)))
        img.resize(size, Image.NEAREST).save(OUT_SMALL / f"{name}_{frame}.png", optimize=True)


def main():
    OUT_LARGE.mkdir(parents=True, exist_ok=True)
    OUT_SMALL.mkdir(parents=True, exist_ok=True)
    for src in sorted(RAW.glob("*.png")):
        name = src.stem
        if name.rsplit("_", 1)[-1] in FRAMES:
            continue  # 직접 그린 프레임은 make_frames 에서 쓴다
        clean = trim(remove_background(Image.open(src)))
        fit(clean, LARGE_SIZE, Image.LANCZOS).save(OUT_LARGE / src.name, optimize=True)
        small_px = STAGE_DP[stage_of(name)] * SMALL_SCALE
        fit(clean, small_px, Image.NEAREST).save(OUT_SMALL / src.name, optimize=True)
        make_frames(name, clean, small_px)
        print(f"{name}: trimmed {clean.size} -> small {small_px}px")


if __name__ == "__main__":
    main()
