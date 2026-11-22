#!/usr/bin/env python3
"""PixelLab 으로 캐릭터 공격 애니메이션을 만들고 앱용으로 정리한다.

  python3 tools/pixellab_attack.py generate <형태> [프레임 수=8]
      sprites/large/<형태>.png 를 256px 첫 프레임으로 보내 공격 애니메이션을 만든다.
      결과는 raw/pixellab/<형태>_attack_<n>.png 와 <형태>_attack.json (첫 프레임 위치) 로 저장.
      PixelLab 생성 횟수를 프레임 수만큼 쓴다 (8프레임 = 8회).
  python3 tools/pixellab_attack.py process <형태>
      raw/pixellab 결과를 원래 큰 그림과 같은 크기·위치로 맞춰 sprites/large/<형태>_attack<n>.png 로.

API 키는 환경의 네트워크 비밀값으로 프록시가 붙여 준다 (코드에 키 없음).
"""
import base64
import io
import json
import subprocess
import sys
import time
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
LARGE = ROOT / "sprites" / "large"
OUT_RAW = ROOT / "raw" / "pixellab"
API = "https://api.pixellab.ai/v2"
FIRST = 256

ACTION = (
    "cute cat attack: gathers strength, then swipes forward hard with its front paw "
    "and claws, then recovers to its original pose"
)


def call(method: str, path: str, body=None) -> dict:
    cmd = ["curl", "-sS", "-m", "120", "-X", method, f"{API}{path}", "-H", "Content-Type: application/json"]
    if body is not None:
        cmd += ["--data-binary", "@-"]
    out = subprocess.run(cmd, input=json.dumps(body).encode() if body is not None else None, capture_output=True, check=True)
    return json.loads(out.stdout)


def first_frame(form: str):
    """원래 큰 그림을 256 캔버스 아래 가운데에 놓는다. 그 위치를 함께 돌려준다."""
    im = Image.open(LARGE / f"{form}.png").convert("RGBA")
    im.thumbnail((FIRST, FIRST), Image.LANCZOS)
    canvas = Image.new("RGBA", (FIRST, FIRST), (0, 0, 0, 0))
    x, y = (FIRST - im.width) // 2, FIRST - im.height
    canvas.alpha_composite(im, (x, y))
    return canvas, [x, y, im.width, im.height]


def generate(form: str, frames: int = 8, action: str = ACTION):
    OUT_RAW.mkdir(parents=True, exist_ok=True)
    canvas, rect = first_frame(form)
    buf = io.BytesIO()
    canvas.save(buf, "PNG")
    job = call("POST", "/animate-with-text-v3", {
        "first_frame": {"type": "base64", "base64": base64.b64encode(buf.getvalue()).decode()},
        "action": action,
        "frame_count": frames,
        "no_background": True,
    })
    job_id = job["background_job_id"]
    print("job", job_id)
    while True:
        time.sleep(6)
        res = call("GET", f"/background-jobs/{job_id}")
        if res["status"] == "completed":
            break
        if res["status"] == "failed":
            raise SystemExit(f"실패: {res.get('last_response')}")
        print(" ...", res["status"])
    images = res["last_response"]["images"]
    for i, img in enumerate(images):
        Image.open(io.BytesIO(base64.b64decode(img["base64"]))).save(OUT_RAW / f"{form}_attack_{i}.png")
    json.dump({"rect": rect, "count": len(images), "usage": res.get("usage")}, open(OUT_RAW / f"{form}_attack.json", "w"))
    print(f"{len(images)}장 저장, 사용량 {res.get('usage')}")


def process(form: str):
    meta = json.load(open(OUT_RAW / f"{form}_attack.json"))
    x, y, w, h = meta["rect"]
    base = Image.open(LARGE / f"{form}.png")
    # 첫 장(0)은 보낸 그림 그대로라 빼고, 1번부터 공격 프레임
    n = 0
    for i in range(1, meta["count"]):
        f = Image.open(OUT_RAW / f"{form}_attack_{i}.png").convert("RGBA")
        # 첫 프레임에서 원래 그림이 있던 자리만 잘라 원래 큰 그림 크기로 → 전투 무대에서 기본 그림과 똑같이 겹친다
        piece = f.crop((x, y, x + w, y + h)).resize(base.size, Image.NEAREST)
        n += 1
        piece.save(LARGE / f"{form}_attack{n}.png", optimize=True)
    print(f"{form}: 공격 프레임 {n}장 → sprites/large/{form}_attack1..{n}.png")


if __name__ == "__main__":
    cmd, form = sys.argv[1], sys.argv[2]
    if cmd == "generate":
        generate(form, int(sys.argv[3]) if len(sys.argv) > 3 else 8)
    elif cmd == "process":
        process(form)
