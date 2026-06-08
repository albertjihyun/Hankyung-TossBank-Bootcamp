# DEPLOY.md — GCP VM 배포 가이드 (오픈런)

단일 VM 안에 **Spring 인스턴스 2개(8080/8081) + Nginx 리버스 프록시 + MariaDB(세션·데이터 공유)** 를 띄우고,
Cloudflare로 SSL을 종단한다. 다중 인스턴스 + failover + 세션 유지를 한 VM에서 시연하기 위한 구성이다(TECH_SPEC §9, §12).

```
[사용자] → Cloudflare(SSL) → Nginx(:80, LB) ─┬─ Spring A (:8080, systemd)
                                              └─ Spring B (:8081, systemd)
                                                     └── MariaDB (비즈니스 데이터 + SPRING_SESSION)
```

> 아래 명령은 위에서 아래로 복사-실행 가능. 플레이스홀더만 본인 값으로 치환:
> `YOUR_PROJECT`, `YOUR_GITHUB_REPO`, `your.domain.com`, `<STRONG_DB_PASSWORD>`.

---

## 1. GCP VM 생성 + 방화벽(80/443) 개방

로컬에 `gcloud` CLI가 설치/인증돼 있다고 가정(`gcloud init`).

```bash
# 1-1. VM 생성 (Ubuntu 22.04 LTS, e2-small)
gcloud compute instances create openrun-vm \
  --project=YOUR_PROJECT \
  --zone=asia-northeast3-a \
  --machine-type=e2-small \
  --image-family=ubuntu-2204-lts \
  --image-project=ubuntu-os-cloud \
  --tags=http-server,https-server \
  --boot-disk-size=20GB

# 1-2. 방화벽: 80/443 개방 (태그 http-server/https-server 기본 룰이 없으면 생성)
gcloud compute firewall-rules create allow-http-https \
  --project=YOUR_PROJECT \
  --allow=tcp:80,tcp:443 \
  --target-tags=http-server,https-server \
  --description="Openrun web"

# 1-3. 외부 IP 확인 (Cloudflare A 레코드에 사용)
gcloud compute instances describe openrun-vm --zone=asia-northeast3-a \
  --format='get(networkInterfaces[0].accessConfigs[0].natIP)'

# 1-4. SSH 접속
gcloud compute ssh openrun-vm --zone=asia-northeast3-a
```

> 8080/8081은 **외부에 열지 않는다**(Nginx만 80을 받고 내부에서 프록시). SSH(22)는 GCP 기본 룰로 열려 있다.

---

## 2. Java 21 + MariaDB 설치, DB/사용자 생성

VM 안에서:

```bash
# 2-1. 패키지 갱신 + Java 21 (Temurin) + MariaDB + Nginx
sudo apt-get update
sudo apt-get install -y openjdk-21-jdk mariadb-server nginx
java -version   # openjdk 21 확인

# 2-2. MariaDB 보안 초기화(대화형) — root 비번 설정, 익명계정/원격root 제거
sudo mysql_secure_installation

# 2-3. DB·사용자 생성 + 권한
sudo mysql <<'SQL'
CREATE DATABASE IF NOT EXISTS openrun CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS 'openrun'@'localhost' IDENTIFIED BY '<STRONG_DB_PASSWORD>';
GRANT ALL PRIVILEGES ON openrun.* TO 'openrun'@'localhost';
FLUSH PRIVILEGES;
SQL

# 2-4. 접속 확인
mysql -u openrun -p'<STRONG_DB_PASSWORD>' -e "SHOW DATABASES;" | grep openrun
```

> 비즈니스 테이블(`member/event/reservation`)은 앱 첫 기동 시 JPA `ddl-auto`로,
> 세션 테이블(`SPRING_SESSION*`)은 Spring Session JDBC가 `initialize-schema=always`로 자동 생성한다.
> (운영 권장: 최초 1회만 `ddl-auto=update`로 스키마 생성 후 `prod` 프로파일은 `validate`)

---

## 3. GitHub 클론 → 빌드 → jar 배치

```bash
# 3-1. 앱 디렉터리
sudo mkdir -p /home/ubuntu/app
sudo chown -R ubuntu:ubuntu /home/ubuntu/app

# 3-2. 소스 클론 (4th_Hackathon 하위가 프로젝트 루트)
cd ~
git clone https://github.com/YOUR_GITHUB_REPO.git src
cd src/hackathon/4th_Hackathon

# 3-3. 빌드 (Gradle Wrapper — 별도 Gradle 설치 불필요)
./gradlew clean bootJar
# 산출물: build/libs/openrun-0.0.1-SNAPSHOT.jar

# 3-4. jar 배치 (systemd가 참조하는 고정 경로/이름으로)
cp build/libs/openrun-*.jar /home/ubuntu/app/openrun.jar
```

이후 재배포는 `git pull && ./gradlew clean bootJar && cp build/libs/openrun-*.jar /home/ubuntu/app/openrun.jar && sudo systemctl restart openrun@8080 openrun@8081`.

---

## 4. 환경변수 파일 (openrun.env)

비밀번호는 코드/깃에 올리지 않고 이 파일로만 주입(`.gitignore`에 `*.env` 포함).

```bash
cat > /home/ubuntu/app/openrun.env <<'ENV'
DB_USER=openrun
DB_PASSWORD=<STRONG_DB_PASSWORD>
SPRING_PROFILES_ACTIVE=prod
ENV
chmod 600 /home/ubuntu/app/openrun.env
```

> `application-prod.yml`은 `ddl-auto=validate`, `thymeleaf.cache=true`. DB 자격증명은 위 환경변수에서 주입된다.

---

## 5. systemd 유닛으로 인스턴스 2개(8080/8081) 등록·기동

템플릿 유닛 `openrun@.service` 하나로 포트만 바꿔 두 인스턴스를 띄운다.

```bash
sudo tee /etc/systemd/system/openrun@.service > /dev/null <<'UNIT'
[Unit]
Description=Openrun (port %i)
After=network.target mariadb.service
Wants=mariadb.service

[Service]
User=ubuntu
WorkingDirectory=/home/ubuntu/app
ExecStart=/usr/bin/java -jar /home/ubuntu/app/openrun.jar --server.port=%i --spring.profiles.active=prod
EnvironmentFile=/home/ubuntu/app/openrun.env
SuccessExitStatus=143
Restart=always
RestartSec=3

[Install]
WantedBy=multi-user.target
UNIT

# 데몬 리로드 + 두 인스턴스 부팅 시 자동기동 + 즉시 기동
sudo systemctl daemon-reload
sudo systemctl enable --now openrun@8080 openrun@8081

# 상태 확인 (둘 다 active(running))
sudo systemctl status openrun@8080 openrun@8081 --no-pager

# 헬스체크 (각 인스턴스 직접)
curl -s localhost:8080/actuator/health   # {"status":"UP"}
curl -s localhost:8081/actuator/health   # {"status":"UP"}

# 기동 로그
journalctl -u openrun@8080 -f
```

> 첫 인스턴스가 스키마를 만들 때 두 번째가 동시에 올라와도 `ddl-auto`/세션 스키마는 멱등이라 안전하다.

---

## 6. Nginx 리버스 프록시(upstream 8080,8081)

```bash
sudo tee /etc/nginx/sites-available/openrun > /dev/null <<'NGINX'
upstream openrun {
    server 127.0.0.1:8080 max_fails=2 fail_timeout=5s;
    server 127.0.0.1:8081 max_fails=2 fail_timeout=5s;
}
server {
    listen 80;
    server_name your.domain.com;

    location / {
        proxy_pass http://openrun;
        proxy_set_header Host              $host;
        proxy_set_header X-Real-IP         $remote_addr;
        proxy_set_header X-Forwarded-For   $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
NGINX

# 활성화 + 기본 사이트 제거 + 문법검사 + 재기동
sudo ln -sf /etc/nginx/sites-available/openrun /etc/nginx/sites-enabled/openrun
sudo rm -f /etc/nginx/sites-enabled/default
sudo nginx -t
sudo systemctl restart nginx

# VM에서 LB 경유 확인
curl -s localhost/actuator/health   # {"status":"UP"}
```

---

## 7. Cloudflare 도메인 A 레코드 + SSL(Full)

Cloudflare 대시보드에서:

1. **DNS → Records → Add record**
   - Type `A`, Name `your.domain.com`(또는 `@`), IPv4 = **§1-3의 VM 외부 IP**, Proxy status **Proxied(주황 구름 ON)**.
2. **SSL/TLS → Overview → Encryption mode = `Full`**
   - 사용자↔Cloudflare는 Cloudflare 인증서로 HTTPS, Cloudflare↔VM은 80(HTTP). (오리진까지 암호화하려면 `Full (strict)` + 오리진 인증서 설치)
3. **SSL/TLS → Edge Certificates → Always Use HTTPS = ON** (선택).

전파 후 확인:
```bash
curl -sI https://your.domain.com/actuator/health   # 200, server: cloudflare
```

---

## 8. 동작 확인 — 헬스체크 · 로드밸런싱 · Failover · 세션 유지

### 8-1. 헬스체크
```bash
curl -s localhost:8080/actuator/health
curl -s localhost:8081/actuator/health
curl -s https://your.domain.com/actuator/health
```

### 8-2. 로드밸런싱 로그(요청이 A/B로 분산되는지)
각 인스턴스 로그를 두 창에서 동시에 보며 반복 요청:
```bash
# 창1
journalctl -u openrun@8080 -f
# 창2
journalctl -u openrun@8081 -f
# 창3 — 여러 번 호출하면 두 로그에 번갈아 접근 기록이 찍힌다
for i in $(seq 1 10); do curl -s localhost/actuator/health >/dev/null; done
```

### 8-3. **Failover 시연** (한 인스턴스 stop 후 서비스 지속)
```bash
# A 인스턴스 종료
sudo systemctl stop openrun@8080

# Nginx가 B(8081)로 우회 → 무중단 (max_fails/fail_timeout)
for i in $(seq 1 10); do curl -s -o /dev/null -w "%{http_code}\n" localhost/; done
# → 전부 200 (A가 죽어도 B가 응답)

# 복구
sudo systemctl start openrun@8080
```

### 8-4. 세션 유지(다중 인스턴스 공유) 확인
Spring Session JDBC라 어느 인스턴스로 라우팅돼도 같은 세션을 공유한다.
```bash
# 로그인하여 세션 쿠키 저장 (CSRF 토큰 필요 시 폼 흐름으로 로그인)
curl -s -c cookies.txt localhost/login > /dev/null
# (브라우저로 로그인 후) 한 인스턴스를 내려도 로그인이 유지되는지:
sudo systemctl stop openrun@8080
curl -s -b cookies.txt localhost/me/reservations -o /dev/null -w "%{http_code}\n"  # 200 (로그인 유지)
sudo systemctl start openrun@8080

# DB에 세션이 실제로 저장됐는지
mysql -u openrun -p'<STRONG_DB_PASSWORD>' openrun -e "SELECT COUNT(*) AS sessions FROM SPRING_SESSION;"
```

---

## 9. 트러블슈팅

| 증상 | 원인 | 해결 |
|------|------|------|
| `systemctl start` 후 곧 죽음, 로그에 `Port already in use` | 같은 포트 중복 기동/잔여 프로세스 | `sudo ss -ltnp | grep 808` 로 점유 확인 → `sudo systemctl stop openrun@<port>` 후 잔여 PID `kill` |
| `Access denied for user 'openrun'` | DB 비번/권한 불일치 | `openrun.env`의 `DB_PASSWORD` 확인, §2-3 `GRANT` 재실행, `systemctl restart` |
| `Permission denied` (jar 실행) | 파일 소유/권한 | `sudo chown ubuntu:ubuntu /home/ubuntu/app/openrun.jar`, env는 `chmod 600` |
| **Cloudflare 522** (연결 시간초과) | 오리진(Nginx/80) 미응답 또는 방화벽 | §1-2 방화벽 80 개방 확인, `sudo systemctl status nginx`, VM 내부 `curl localhost/` 200 확인 |
| Cloudflare 521 | 오리진 다운 | 두 인스턴스/Nginx 기동 상태 확인(`systemctl status`) |
| 무한 리다이렉트(SSL) | SSL 모드 `Flexible`인데 앱이 HTTPS 기대 | Cloudflare SSL을 **Full**로 |
| `nginx -t` 실패 | 설정 문법 오류 | 메시지의 라인 수정 후 `sudo systemctl restart nginx` |
| 502 Bad Gateway | upstream(8080/8081) 둘 다 다운 | `systemctl status openrun@8080 openrun@8081`, `journalctl -u openrun@8080 -e` |
| 한글 깨짐 | DB charset | DB를 `utf8mb4`로 생성(§2-3), JDBC URL에 `serverTimezone` 유지 |
| ddl `validate` 실패(첫 배포) | 스키마 미생성 | 최초 1회 `SPRING_PROFILES_ACTIVE` 없이(기본 `update`)로 기동해 스키마 생성 후 `prod`로 전환 |

---

### 부록 — 재배포 한 줄
```bash
cd ~/src && git pull && cd hackathon/4th_Hackathon && \
  ./gradlew clean bootJar && cp build/libs/openrun-*.jar /home/ubuntu/app/openrun.jar && \
  sudo systemctl restart openrun@8080 openrun@8081
```
