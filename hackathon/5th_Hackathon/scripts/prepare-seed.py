#!/usr/bin/env python3
"""
기획서 3-[1]/[2] 단계: Kaggle styles.csv에서 상품 선별 + 이미지 복사 + seed-products.csv 생성.

- masterCategory in {Apparel, Footwear, Accessories} AND 실제 이미지 파일 존재하는 것만 후보
- 후보를 셔플 후 앞 N개 선별 (단순 앞 N행 자르기 금지 → shuffle 후 절단)
- 선별 이미지를 프런트 정적 폴더(public/products/{id}.jpg)로 복사 → Next.js가 서빙
- seed-products.csv: id,name,gender,master_category,sub_category,article_type,base_colour,season,image_url

사용법:
  python prepare-seed.py <DATASET_DIR> <FRONT_PUBLIC_DIR> <OUT_CSV> [COUNT]
"""
import csv, os, random, shutil, sys

ALLOWED = {"Apparel", "Footwear", "Accessories"}


def clean(s: str) -> str:
    # CSV/줄바꿈 깨짐 방지: 쉼표/탭/개행 제거
    return s.replace(",", " ").replace("\t", " ").replace("\r", " ").replace("\n", " ").strip()


def find_dataset(root: str):
    """styles.csv + images/ 가 같이 있는 폴더를 root 아래에서 탐색."""
    for base, dirs, files in os.walk(root):
        if "styles.csv" in files and os.path.isdir(os.path.join(base, "images")):
            return base
    # images 가 한 겹 더 들어간 변형(images/images) 대비
    if os.path.isfile(os.path.join(root, "styles.csv")):
        return root
    return None


def main():
    src_arg = sys.argv[1]
    front_public = sys.argv[2]
    out_csv = sys.argv[3]
    target = int(sys.argv[4]) if len(sys.argv) > 4 else 5000

    ds = find_dataset(src_arg)
    if not ds:
        print(f"[err] styles.csv + images/ 를 {src_arg} 아래에서 못 찾음", file=sys.stderr)
        sys.exit(1)
    styles = os.path.join(ds, "styles.csv")
    images_dir = os.path.join(ds, "images")
    print(f"[info] dataset dir = {ds}")

    candidates = []
    skipped_cat = skipped_img = malformed = 0
    with open(styles, encoding="utf-8") as f:
        f.readline()  # header
        for line in f:
            line = line.rstrip("\n").rstrip("\r")
            if not line:
                continue
            parts = line.split(",", 9)  # 10 컬럼, 마지막(productDisplayName)에 쉼표 허용
            if len(parts) < 10:
                malformed += 1
                continue
            pid, gender, master, sub, article, colour, season, year, usage, name = parts
            if master not in ALLOWED:
                skipped_cat += 1
                continue
            if not os.path.isfile(os.path.join(images_dir, pid + ".jpg")):
                skipped_img += 1
                continue
            candidates.append((pid, name, gender, master, sub, article, colour, season))

    print(f"[info] 후보={len(candidates)}  (제외: 카테고리 {skipped_cat}, 이미지없음 {skipped_img}, 형식오류 {malformed})")

    random.seed(20260626)
    random.shuffle(candidates)
    selected = candidates[:target]

    os.makedirs(front_public, exist_ok=True)
    os.makedirs(os.path.dirname(os.path.abspath(out_csv)), exist_ok=True)

    copied = []
    for r in selected:
        pid = r[0]
        try:
            shutil.copyfile(os.path.join(images_dir, pid + ".jpg"),
                            os.path.join(front_public, pid + ".jpg"))
            copied.append(r)
        except Exception as e:
            print(f"[warn] copy fail {pid}: {e}", file=sys.stderr)

    with open(out_csv, "w", encoding="utf-8", newline="") as f:
        w = csv.writer(f)
        w.writerow(["id", "name", "gender", "master_category", "sub_category",
                    "article_type", "base_colour", "season", "image_url"])
        for (pid, name, gender, master, sub, article, colour, season) in copied:
            w.writerow([pid, clean(name), clean(gender), clean(master), clean(sub),
                        clean(article), clean(colour), clean(season), f"/products/{pid}.jpg"])

    print(f"[done] 선별={len(selected)} 복사={len(copied)} csv={out_csv}")


if __name__ == "__main__":
    main()
