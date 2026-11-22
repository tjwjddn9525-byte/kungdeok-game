#!/usr/bin/env python3
"""raw/ui/ 의 제미나이 그림을 앱에서 쓸 UI 스프라이트로 정리한다.

- 여러 개가 그려진 시트: 흰 배경을 지우고(바깥에서부터 채우기), 덩어리를 찾아 격자 칸별로 모은 뒤
  하나씩 잘라 sprites/ui/<이름>.png 로 저장 (긴 변 SIZE px)
- 배경: 오른쪽 아래 제미나이 로고(✦)를 옆 무늬로 덮고 폭 BG_WIDTH 로 줄여 저장
- 앱 아이콘: 모서리 흰색을 지우고 res/drawable/ic_launcher.png 로

사용법: python3 tools/process_ui.py   (저장소 루트, Pillow·numpy 필요)
"""
from collections import deque
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
RAW = ROOT / "raw" / "ui"
OUT = ROOT / "sprites" / "ui"
ICON_OUT = ROOT / "app" / "src" / "main" / "res" / "drawable" / "ic_launcher.png"

SIZE = 256
BG_WIDTH = 720
WHITE_THRESH = 60
# 라벨링은 1/LOW 해상도에서 (빠르게)
LOW = 4

# 시트 이름 → (열, 행, 칸 순서대로 저장할 이름; None 은 빈 칸)
SHEETS = {
    "furniture_sheet": (3, 2, ["furn_box", "furn_plant", "furn_cushion", "furn_scratcher", "furn_picture", None]),
    "accessories_sheet": (3, 2, ["acc_ribbon", "acc_bell", "acc_flower", "acc_glasses", "acc_crown", None]),
    "items_sheet": (3, 2, ["item_bowl", "item_shrimp", "item_yarn", "item_brush", "item_gift", "item_coin"]),
    "mobs_sheet": (3, 2, ["mob_mouse", "mob_fly", "mob_cricket", "mob_ladybug", "mob_spider", "mob_butterfly"]),
    "enemies_sheet": (4, 2, [
        "enemy_mouse", "enemy_fly", "enemy_sparrow", "enemy_lizard",
        "enemy_spider", "enemy_crow", "enemy_sock", "enemy_ghost",
    ]),
    "bosses_sheet": (3, 2, [
        "boss_broom", "boss_robot_vacuum", "boss_hair_dryer",
        "boss_neighbor_dog", "boss_bathtub", "boss_vet_carrier",
    ]),
    "effects_sheet": (4, 2, [
        "fx_hit", "fx_star", "fx_dizzy", "fx_sweat",
        "fx_dust", "fx_sparkle", "fx_heart", "fx_sleep",
    ]),
    "status_icons_sheet": (4, 1, ["status_hunger", "status_mood", "status_clean", "status_sleep"]),
    "care_icons_sheet": (3, 2, ["care_pet", "care_feed", "care_snack", "care_play", "care_brush", "care_sleep"]),
    "tab_icons_sheet": (3, 2, ["tab_room", "tab_status", "tab_dex", "tab_tower", "tab_shop", "tab_settings"]),
}

# 캐릭터 프레임 시트: raw/ui/frames_<형태>.png (걷기1·걷기2·잠자기 한 줄) → raw/<형태>_walk1.png 등 (원본 크기 그대로)
# 크기 맞추기와 방향 맞추기는 tools/process_sprites.py 가 한다.
FRAME_NAMES = ["walk1", "walk2", "sleep"]
RAW_CHAR = ROOT / "raw"

# 바깥과 이어지지 않은 흰 구멍 (목걸이 고리 안쪽 등): 잘라 낸 그림에서 이 비율 위치를 투명하게 채운다
HOLES = {"acc_bell": [(0.5, 0.3)]}

BACKGROUNDS = {"room_background": "bg_room", "tower_background": "bg_tower"}


def remove_background(img: Image.Image) -> Image.Image:
    rgba = img.convert("RGBA")
    w, h = rgba.size
    padded = Image.new("RGBA", (w + 2, h + 2), (255, 255, 255, 255))
    padded.paste(rgba, (1, 1))
    ImageDraw.floodfill(padded, (0, 0), (255, 255, 255, 0), thresh=WHITE_THRESH)
    out = np.array(padded.crop((1, 1, w + 1, h + 1)))
    out[out[..., 3] == 0] = 0
    return Image.fromarray(out)


def label(mask: np.ndarray):
    """4방향 연결 덩어리 라벨 (작은 해상도에서 쓰므로 순수 파이썬 BFS 로 충분)"""
    h, w = mask.shape
    labels = np.zeros((h, w), dtype=np.int32)
    comps = []
    for y0 in range(h):
        row = mask[y0]
        for x0 in np.nonzero(row)[0]:
            if labels[y0, x0]:
                continue
            idx = len(comps) + 1
            q = deque([(y0, x0)])
            labels[y0, x0] = idx
            xs = [x0, x0]
            ys = [y0, y0]
            area = 0
            while q:
                y, x = q.popleft()
                area += 1
                xs[0] = min(xs[0], x); xs[1] = max(xs[1], x)
                ys[0] = min(ys[0], y); ys[1] = max(ys[1], y)
                for ny, nx in ((y - 1, x), (y + 1, x), (y, x - 1), (y, x + 1)):
                    if 0 <= ny < h and 0 <= nx < w and mask[ny, nx] and not labels[ny, nx]:
                        labels[ny, nx] = idx
                        q.append((ny, nx))
            comps.append({"idx": idx, "area": area, "bbox": (xs[0], ys[0], xs[1], ys[1])})
    return labels, comps


def split_sheet(name: str, cols: int, rows: int, names, out_dir: Path = OUT, max_size: int = SIZE):
    img = remove_background(Image.open(RAW / f"{name}.png"))
    w, h = img.size
    alpha = np.array(img)[..., 3] > 0
    small = alpha[::LOW, ::LOW]
    labels, comps = label(small)
    sh, sw = small.shape
    cell_w, cell_h = sw / cols, sh / rows
    min_area = max(4, int(cell_w * cell_h * 0.0004))
    groups = {i: [] for i in range(cols * rows)}
    for c in comps:
        if c["area"] < min_area:
            continue
        x0, y0, x1, y1 = c["bbox"]
        cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
        col = min(cols - 1, int(cx // cell_w))
        row = min(rows - 1, int(cy // cell_h))
        groups[row * cols + col].append(c["idx"])
    arr = np.array(img)
    for i, out_name in enumerate(names):
        if out_name is None:
            continue
        ids = groups[i]
        if not ids:
            print(f"  ! {name}: {i}번 칸이 비어 있음 ({out_name})")
            continue
        keep_small = np.isin(labels, ids)
        keep = np.repeat(np.repeat(keep_small, LOW, axis=0), LOW, axis=1)[:h, :w]
        # 저해상도 경계 때문에 잘린 테두리를 살리려고 한 칸 넓힌다
        keep = keep | np.roll(keep, LOW, 0) | np.roll(keep, -LOW, 0) | np.roll(keep, LOW, 1) | np.roll(keep, -LOW, 1)
        piece = arr.copy()
        piece[~keep] = 0
        p = Image.fromarray(piece)
        bbox = p.getchannel("A").getbbox()
        p = p.crop(bbox)
        for fx, fy in HOLES.get(out_name, []):
            seed = (int(p.width * fx), int(p.height * fy))
            r, g, b, a = p.getpixel(seed)
            if a > 0 and min(r, g, b) > 200:
                ImageDraw.floodfill(p, seed, (0, 0, 0, 0), thresh=WHITE_THRESH)
        s = max_size / max(p.size)
        if s < 1:
            p = p.resize((max(1, round(p.width * s)), max(1, round(p.height * s))), Image.LANCZOS)
        p.save(out_dir / f"{out_name}.png", optimize=True)
        print(f"  {out_name}: {p.size}")


def split_frames(name: str, names, out_dir: Path):
    """캐릭터 프레임 시트(한 줄 세 칸)는 그림끼리 망토·날개가 붙어 있을 수 있어서,
    1/3·2/3 지점 근처에서 불투명한 픽셀이 가장 적은 세로줄을 찾아 자른다."""
    img = remove_background(Image.open(RAW / f"{name}.png"))
    arr = np.array(img)
    w = arr.shape[1]
    cols = (arr[..., 3] > 0).sum(axis=0)
    cuts = [0]
    for k in (1, 2):
        lo, hi = int(w * (k / 3 - 0.12)), int(w * (k / 3 + 0.12))
        window = cols[lo:hi]
        best = np.flatnonzero(window == window.min())
        cuts.append(lo + int(best[len(best) // 2]))
    cuts.append(w)
    for i, out_name in enumerate(names):
        sub = arr[:, cuts[i]:cuts[i + 1]].copy()
        # 자른 곳에 걸려 들어온 옆 그림 조각(꼬리 끝 등)은 버린다: 자른 테두리에 닿는 작은 덩어리
        small = sub[::LOW, ::LOW, 3] > 0
        labels, comps = label(small)
        if comps:
            biggest = max(c["area"] for c in comps)
            sw = small.shape[1]
            drop = [
                c["idx"] for c in comps
                if c["area"] < biggest * 0.08 and (c["bbox"][0] <= 1 or c["bbox"][2] >= sw - 2)
            ]
            if drop:
                # 남길 덩어리 둘레만 살리고 나머지는 지운다 (저해상도 경계의 얇은 찌꺼기까지)
                keep_ids = [c["idx"] for c in comps if c["idx"] not in drop]
                keep = np.repeat(np.repeat(np.isin(labels, keep_ids), LOW, 0), LOW, 1)[:sub.shape[0], :sub.shape[1]]
                keep = keep | np.roll(keep, LOW, 0) | np.roll(keep, -LOW, 0) | np.roll(keep, LOW, 1) | np.roll(keep, -LOW, 1)
                sub[~keep] = 0
        piece = Image.fromarray(sub)
        bbox = piece.getchannel("A").getbbox()
        piece = piece.crop(bbox)
        piece.save(out_dir / f"{out_name}.png", optimize=True)
        print(f"  {out_name}: {piece.size}  (자른 곳 겹침 {int(cols[cuts[i]]) if i else 0})")


def remove_watermark(img: Image.Image) -> Image.Image:
    """오른쪽 아래 ✦ 로고 자리를 바로 왼쪽 무늬로 덮는다"""
    w, h = img.size
    box_w, box_h = int(w * 0.11), int(h * 0.075)
    x0, y0 = w - box_w, h - box_h
    patch = img.crop((x0 - box_w, y0, x0, h))
    out = img.copy()
    out.paste(patch, (x0, y0))
    return out


def main(only=None):
    """[only] 가 있으면 이름에 그 글자가 들어간 것만 다시 만든다 (예: frames, care)"""
    OUT.mkdir(parents=True, exist_ok=True)
    for name, (cols, rows, names) in SHEETS.items():
        if only and only not in name:
            continue
        if (RAW / f"{name}.png").exists():
            print(name)
            split_sheet(name, cols, rows, names)
    for sheet in sorted(RAW.glob("frames_*.png")):
        if only and only not in sheet.stem:
            continue
        form = sheet.stem.removeprefix("frames_")
        print(sheet.stem)
        split_frames(sheet.stem, [f"{form}_{f}" for f in FRAME_NAMES], RAW_CHAR)
    if only:
        return
    for name, out_name in BACKGROUNDS.items():
        src = RAW / f"{name}.png"
        if src.exists():
            img = remove_watermark(Image.open(src).convert("RGB"))
            s = BG_WIDTH / img.width
            img = img.resize((BG_WIDTH, round(img.height * s)), Image.LANCZOS)
            img.save(OUT / f"{out_name}.png", optimize=True)
            print(f"{out_name}: {img.size}")
    icon = RAW / "app_icon.png"
    if icon.exists():
        img = remove_background(Image.open(icon))
        img = img.crop(img.getchannel("A").getbbox()).resize((192, 192), Image.LANCZOS)
        img.save(ICON_OUT, optimize=True)
        print("ic_launcher: 192x192")


if __name__ == "__main__":
    import sys
    main(sys.argv[1] if len(sys.argv) > 1 else None)
