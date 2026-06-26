#!/usr/bin/env python3
"""
고해상도 원본(fashion-product-images-dataset)에서 필요한 id의 이미지만 병렬 다운로드.
small 데이터셋(60x80)과 달리 ~1080x1440 고해상도라 화질 문제가 근본 해결된다.

전체 zip(~11GB) 대신, 선별된 id만 Kaggle 단일파일 다운로드 API로 받는다(~1GB).

사용법:
  python download-highres.py <IDS_DIR> <OUT_DIR> [WORKERS]
  - IDS_DIR: 기준 id 목록 폴더(보통 frontend/public/products 의 {id}.jpg)
  - OUT_DIR: 고해상도 원본을 저장할 폴더
"""
import os
import sys
import time
from concurrent.futures import ThreadPoolExecutor, as_completed

import requests

OWNER = "paramaggarwal"
DATASET = "fashion-product-images-dataset"
PATH_PREFIX = "fashion-dataset/fashion-dataset/images"  # 데이터셋 내부 경로


def token() -> str:
    p = os.path.join(os.path.expanduser("~"), ".kaggle", "access_token")
    with open(p) as f:
        return f.read().strip()


def download_one(session: requests.Session, pid: str, out_dir: str) -> str:
    dst = os.path.join(out_dir, f"{pid}.jpg")
    if os.path.isfile(dst) and os.path.getsize(dst) > 2000:
        return "skip"
    enc = f"{PATH_PREFIX}/{pid}.jpg".replace("/", "%2F")
    url = f"https://www.kaggle.com/api/v1/datasets/download/{OWNER}/{DATASET}/{enc}"
    for attempt in range(4):
        try:
            r = session.get(url, timeout=40, allow_redirects=True)
            if r.status_code == 200 and len(r.content) > 2000:
                with open(dst, "wb") as f:
                    f.write(r.content)
                return "ok"
            if r.status_code == 404:
                return "404"
        except Exception:
            pass
        time.sleep(1.5 * (attempt + 1))
    return "fail"


def main():
    ids_dir = sys.argv[1]
    out_dir = sys.argv[2]
    workers = int(sys.argv[3]) if len(sys.argv) > 3 else 16
    os.makedirs(out_dir, exist_ok=True)

    ids = [f[:-4] for f in os.listdir(ids_dir) if f.lower().endswith(".jpg")]
    print(f"[hr] target {len(ids)} images, {workers} workers")

    tok = token()
    headers = {"Authorization": f"Bearer {tok}"}

    counts = {"ok": 0, "skip": 0, "404": 0, "fail": 0}
    done = 0
    # 스레드별 세션
    local = {}

    def task(pid: str) -> str:
        sess = local.get(id(__import__("threading").current_thread()))
        if sess is None:
            sess = requests.Session()
            sess.headers.update(headers)
            local[id(__import__("threading").current_thread())] = sess
        return download_one(sess, pid, out_dir)

    with ThreadPoolExecutor(max_workers=workers) as ex:
        futs = {ex.submit(task, pid): pid for pid in ids}
        for fut in as_completed(futs):
            counts[fut.result()] = counts.get(fut.result(), 0) + 1
            done += 1
            if done % 500 == 0:
                print(f"  ... {done}/{len(ids)}  {counts}")

    print(f"[done] {counts}")
    if counts["fail"]:
        print("일부 실패 — 스크립트를 다시 실행하면 실패분만 재시도(이어받기)합니다.", file=sys.stderr)


if __name__ == "__main__":
    main()
