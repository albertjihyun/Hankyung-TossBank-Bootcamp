// 행사 상세 페이지: 카운트다운 + 잔여석 폴링 (TECH_SPEC §0/§6 — 폴링 기본)
(function () {
    const detail = document.querySelector('.detail');
    if (!detail) return;

    const eventId = detail.getAttribute('data-event-id');
    const openAtStr = detail.getAttribute('data-open-at');
    const POLL_MS = 3000; // application.yml openrun.poll-interval-seconds 와 동일 의도

    const $remaining = document.getElementById('seat-remaining');
    const $fill = document.getElementById('seat-fill');
    const $status = document.getElementById('ev-status');
    const $reserveBtn = document.getElementById('reserve-btn');
    const $countdownBox = document.getElementById('countdown');
    const $timer = document.getElementById('countdown-timer');

    const STATUS_LABEL = { SCHEDULED: '오픈예정', OPEN: '오픈중', CLOSED: '마감' };

    /* ===== 카운트다운 ===== */
    let openAt = openAtStr ? new Date(openAtStr) : null;

    function pad(n) { return String(n).padStart(2, '0'); }

    function tickCountdown() {
        if (!$countdownBox || !openAt) return;
        const diff = openAt - new Date();
        if (diff <= 0) {
            $timer.textContent = '00:00:00';
            $countdownBox.textContent = '오픈되었습니다! 잠시 후 갱신됩니다…';
            return;
        }
        const h = Math.floor(diff / 3600000);
        const m = Math.floor((diff % 3600000) / 60000);
        const s = Math.floor((diff % 60000) / 1000);
        $timer.textContent = `${pad(h)}:${pad(m)}:${pad(s)}`;
    }

    /* ===== 잔여석 폴링 ===== */
    async function pollSeats() {
        try {
            const res = await fetch(`/api/events/${eventId}/seats`, { headers: { 'Accept': 'application/json' } });
            if (!res.ok) return;
            const data = await res.json();

            if ($remaining) $remaining.textContent = data.remaining;
            if ($fill && data.capacity > 0) {
                $fill.style.width = (data.reserved * 100 / data.capacity) + '%';
            }
            if ($status) {
                $status.textContent = STATUS_LABEL[data.status] || data.status;
                $status.className = 'status status-' + data.status.toLowerCase();
            }
            // 상태가 OPEN 으로 바뀌면 예약 버튼 활성화
            if ($reserveBtn && data.status === 'OPEN') {
                $reserveBtn.disabled = false;
            }
        } catch (e) {
            // 네트워크 일시 오류는 무시하고 다음 주기에 재시도
        }
    }

    // 시작
    if ($countdownBox && openAt) {
        tickCountdown();
        setInterval(tickCountdown, 1000);
    }
    pollSeats();
    setInterval(pollSeats, POLL_MS);
})();
