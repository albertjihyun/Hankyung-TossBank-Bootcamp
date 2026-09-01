#!/bin/bash
# 살래말래 systemd unit 설치 스크립트
# 여러 번 실행해도 안전 (멱등)
# 사용법: sudo bash deploy/install.sh

set -e

DEPLOY_DIR="$(cd "$(dirname "$0")" && pwd)"

echo "[install] unit 파일 복사 중..."
cp "$DEPLOY_DIR/sallae-notifier.service" /etc/systemd/system/
cp "$DEPLOY_DIR/sallae-notifier.timer"   /etc/systemd/system/
cp "$DEPLOY_DIR/sallae-web.service"      /etc/systemd/system/

echo "[install] systemctl daemon-reload..."
systemctl daemon-reload

echo "[install] 서비스 활성화 및 시작..."
systemctl enable --now sallae-notifier.timer
systemctl enable --now sallae-web.service

echo "[install] 완료."
echo "  타이머 상태: systemctl list-timers | grep sallae"
echo "  알림 로그:  journalctl -u sallae-notifier"
echo "  웹 로그:    journalctl -u sallae-web"
