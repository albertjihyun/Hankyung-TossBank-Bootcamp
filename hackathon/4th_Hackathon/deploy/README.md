# deploy/ — Infrastructure as Code (오픈런)

리포만 clone하면 **어디서든(Cloud Shell) 한 번에 재현**되도록 인프라를 코드로 박제한 디렉터리.
수동 런북([../DEPLOY.md](../DEPLOY.md))을 스크립트로 옮긴 것 — 둘 다 같은 구조(매니지드 LB + 멀티존 MIG + MariaDB VM)를 만든다.

## 파일
| 파일 | 역할 |
|------|------|
| `provision.sh` | 전체 인프라 생성(API·Secret·SA·버킷·방화벽·HC·DB VM·템플릿·MIG·LB). 멱등. |
| `teardown.sh` | 전체 삭제(역순). 비용 차단. |
| `db-startup.sh` | DB VM 부팅 시: MariaDB 설치+계정+스키마+시드, jar 빌드 후 버킷 업로드 |
| `startup.sh` | 앱 인스턴스 부팅 시: 인증으로 jar·DB비번 받아 기동 (jar 준비될 때까지 재시도) |

## 쓰는 법 (Cloud Shell)
```bash
git clone https://github.com/albertjihyun/Hankyung-TossBank-Bootcamp.git
cd Hankyung-TossBank-Bootcamp/hackathon/4th_Hackathon
chmod +x deploy/*.sh

export DB_PW='강한비밀번호'        # ★ 비밀은 git에 없음 → 실행 시 주입(필수)
# (선택) export REGION=asia-northeast3

./deploy/provision.sh             # 5~8분 뒤 LB IP로 접속 가능
# ...
./deploy/teardown.sh              # 끝나면 비용 차단
```

## "어디서든 pull → 재현"의 경계 (정직하게)
코드/스크립트는 100% 이 리포에 있다. 다만 **git이 담을 수 없는 전제 3가지**는 실행 환경에 있어야 한다 — 이건 모든 클라우드 배포의 본질:
1. **GCP 프로젝트 + 결제 계정** (각자 자기 것)
2. **gcloud 인증** (Cloud Shell이면 자동)
3. **DB 비밀번호 값** — 보안상 커밋 금지 → `export DB_PW=...`로 주입

즉 *"코드는 git에서, 비밀·계정만 주입"* — 이게 올바른 형태다.

## 자가수렴 설계 (부팅 순서에 무관)
- 앱 인스턴스는 **jar이 버킷에 올라올 때까지**(=DB VM이 빌드·업로드 끝낼 때까지) 다운로드를 재시도한다.
- DB가 아직 안 떠 있으면 앱은 죽고 `Restart=always`로 재시작 → DB 준비되면 자동 연결.
- 그래서 provision은 전부 한 번에 발사해도 각자 수렴한다. 명시적 동기화 불필요.

## 주의
- **깨끗한 상태에서 실행 권장.** 기존에 수동 배포(`openrun-vm` 등)가 있으면 충돌/중복을 피하려 먼저 정리하라.
- DB는 **단일 MariaDB VM**(스펙 준수). 매니지드 HA(Cloud SQL)는 MariaDB 미지원이라 의도적으로 "나중" 설계로 둠 — [ARCHITECTURE.md §3·§4](../ARCHITECTURE.md).
- TLS(HTTPS)는 도메인이 필요하므로 스크립트에는 HTTP(80)까지만. 인증서 절차는 [DEPLOY.md §9-TLS](../DEPLOY.md).
- 디버깅: DB는 `/var/log/openrun-db-startup.log`, 앱은 `/var/log/openrun-startup.log`.
