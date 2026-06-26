#!/usr/bin/env python3
"""
Kaggle 'small' 데이터셋 이미지는 60x80px 초저해상도라 카드 크기로 확대되며 뭉개진다.
이 스크립트는 각 이미지를 고품질 리샘플(LANCZOS) + 언샤프 마스크로 업스케일하여
일정한 3:4 규격(기본 600x800)으로 저장한다.

핵심: 원본보다 큰 해상도로 만들어 두면 브라우저는 '다운샘플'(항상 선명)하게 되어,
원본을 직접 '업샘플'(흐릿)하던 문제가 사라진다. (없는 디테일을 만들진 못하지만 인상은 크게 개선)

사용법:
  python enhance-images.py <ORIGINAL_IMAGES_DIR> <TARGET_DIR> [WIDTH] [HEIGHT]
  - ORIGINAL_IMAGES_DIR: Kaggle 원본 images/ 폴더 (60x80 원본)
  - TARGET_DIR: 결과를 쓸 폴더 (보통 frontend/public/products) — 이 폴더의 {id}.jpg 목록을 기준으로 처리
"""
import os
import sys
from PIL import Image, ImageFilter


def main():
    orig_dir = sys.argv[1]
    target_dir = sys.argv[2]
    w = int(sys.argv[3]) if len(sys.argv) > 3 else 600
    h = int(sys.argv[4]) if len(sys.argv) > 4 else 800

    ids = [f for f in os.listdir(target_dir) if f.lower().endswith(".jpg")]
    print(f"[enhance] {len(ids)} images → {w}x{h}")

    sharpen = ImageFilter.UnsharpMask(radius=1.6, percent=120, threshold=2)
    done = fail = 0
    for i, fname in enumerate(ids):
        src = os.path.join(orig_dir, fname)
        if not os.path.isfile(src):
            src = os.path.join(target_dir, fname)  # 원본이 없으면 현재 파일로 폴백
        try:
            with Image.open(src) as im:
                im = im.convert("RGB")
                # 3:4 캔버스에 맞춰 cover 크롭(대부분 이미 3:4라 거의 무손실)
                im = cover_resize(im, w, h)
                im = im.filter(sharpen)
                im.save(os.path.join(target_dir, fname),
                        "JPEG", quality=86, optimize=True, progressive=True)
            done += 1
        except Exception as e:
            fail += 1
            print(f"[warn] {fname}: {e}", file=sys.stderr)
        if (i + 1) % 1000 == 0:
            print(f"  ... {i + 1}/{len(ids)}")

    print(f"[done] enhanced={done} failed={fail}")


def cover_resize(im: "Image.Image", w: int, h: int) -> "Image.Image":
    """object-fit: cover 와 동일하게 비율 유지 + 중앙 크롭 후 목표 크기로."""
    sw, sh = im.size
    scale = max(w / sw, h / sh)
    nw, nh = round(sw * scale), round(sh * scale)
    im = im.resize((nw, nh), Image.LANCZOS)
    left = (nw - w) // 2
    top = (nh - h) // 2
    return im.crop((left, top, left + w, top + h))


if __name__ == "__main__":
    main()
