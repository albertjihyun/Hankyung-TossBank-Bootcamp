#!/usr/bin/env python3
"""
고해상도 zip에서 필요한 id의 이미지만 꺼내 웹 사이즈로 리사이즈 후 저장.
(전체 압축해제 없이 zip 멤버를 스트리밍으로 처리 → 디스크 절약)

사용법:
  python extract-highres.py <ZIP_PATH> <IDS_DIR> <TARGET_DIR> [WIDTH] [HEIGHT]
  - ZIP_PATH:  fashion-product-images-dataset.zip
  - IDS_DIR:   기준 id 목록 폴더(frontend/public/products 의 {id}.jpg)
  - TARGET_DIR: 결과 저장(보통 IDS_DIR 와 동일 → 덮어쓰기)
"""
import io
import os
import sys
import zipfile

from PIL import Image, ImageFilter

INNER_PREFIX = "fashion-dataset/fashion-dataset/images"


def cover_resize(im, w, h):
    sw, sh = im.size
    scale = max(w / sw, h / sh)
    nw, nh = round(sw * scale), round(sh * scale)
    im = im.resize((nw, nh), Image.LANCZOS)
    left, top = (nw - w) // 2, (nh - h) // 2
    return im.crop((left, top, left + w, top + h))


def main():
    zip_path = sys.argv[1]
    ids_dir = sys.argv[2]
    target_dir = sys.argv[3]
    w = int(sys.argv[4]) if len(sys.argv) > 4 else 900
    h = int(sys.argv[5]) if len(sys.argv) > 5 else 1200
    os.makedirs(target_dir, exist_ok=True)

    ids = [f[:-4] for f in os.listdir(ids_dir) if f.lower().endswith(".jpg")]
    print(f"[extract] {len(ids)} ids → {w}x{h} from {os.path.basename(zip_path)}")

    sharpen = ImageFilter.UnsharpMask(radius=1.0, percent=80, threshold=2)
    ok = miss = fail = 0
    with zipfile.ZipFile(zip_path) as zf:
        names = set(zf.namelist())
        for i, pid in enumerate(ids):
            member = f"{INNER_PREFIX}/{pid}.jpg"
            if member not in names:
                miss += 1
                continue
            try:
                with zf.open(member) as fp:
                    data = fp.read()
                im = Image.open(io.BytesIO(data)).convert("RGB")
                im = cover_resize(im, w, h).filter(sharpen)
                im.save(os.path.join(target_dir, f"{pid}.jpg"),
                        "JPEG", quality=88, optimize=True, progressive=True)
                ok += 1
            except Exception as e:
                fail += 1
                print(f"[warn] {pid}: {e}", file=sys.stderr)
            if (i + 1) % 1000 == 0:
                print(f"  ... {i + 1}/{len(ids)}  ok={ok} miss={miss} fail={fail}")

    print(f"[done] ok={ok} miss={miss} fail={fail}")


if __name__ == "__main__":
    main()
