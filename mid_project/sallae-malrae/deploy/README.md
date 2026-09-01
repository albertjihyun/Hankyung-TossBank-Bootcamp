# 배포 가이드

## 로컬 디버깅 (dev-notify)

실제 발송 없이 로직만 확인 (DRY_RUN):
```bash
DRY_RUN=1 node --env-file=.env.local scripts/dev-notify.js "2026-05-17T03:00:00"
```

실제 발송:
```bash
node --env-file=.env.local scripts/dev-notify.js "2026-05-17T03:00:00"
```

인자 없이 실행하면 현재 시각 기준으로 동작:
```bash
node --env-file=.env.local scripts/dev-notify.js
```

## VM 배포

```bash
sudo bash deploy/install.sh
```

## 로그 확인

```bash
# 알림 발송 로그
journalctl -u sallae-notifier

# 웹 서버 로그
journalctl -u sallae-web

# 타이머 상태 확인
systemctl list-timers | grep sallae
```

## 타이머 수동 트리거 (테스트용)

```bash
sudo systemctl start sallae-notifier.service
```
