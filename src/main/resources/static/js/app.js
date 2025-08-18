// ===== 로그 유틸 =====
function addLog(message, type = "info") {
    const box = document.getElementById("logContainer");
    if (!box) return;
    const div = document.createElement("div");
    div.className = `log-line ${type}`;
    div.textContent = `[${new Date().toLocaleTimeString()}] ${message}`;
    box.appendChild(div);
    box.scrollTop = box.scrollHeight;
}

// ===== 상태표시 갱신 =====
function updateConnectionStatus(connected) {
    const dot = document.getElementById("statusDot");        // 빨간/초록 점
    const text = document.getElementById("statusText");       // "연결됨/연결되지 않음"
    if (!dot || !text) return;
    if (connected) {
        dot.classList.add("connected");
        text.textContent = "연결됨";
    } else {
        dot.classList.remove("connected");
        text.textContent = "연결되지 않음";
    }
}

// ===== 다른 버튼들은 여전히 무동작 처리 =====
(function lockOthers() {
    const noop = (e) => { if (e && typeof e.preventDefault === "function") e.preventDefault(); return false; };
    const ids = [
        /* 사전점검은 제외하고 나머지만 잠금 */
        'nextBtn0',
        'btnWAS', 'nextBtn1',
        'btnTomcat', 'nextBtn2',
        'btnNginx', 'nextBtn3',
        'btnUpload', 'nextBtn4',
        'btnSecurity', 'nextBtn5',
        'btnDB', 'nextBtn6',
        'btnStart', 'finishBtn'
    ];
    ids.forEach(id => {
        const el = document.getElementById(id);
        if (el) {
            el.disabled = true;
            el.onclick = noop;
            el.addEventListener('click', noop, true);
        }
    });
})();

// ===== 진행률 텍스트/바 초기화(고정 표시) =====
(function initProgress() {
    const progressFill = document.getElementById('progressFill');
    const progressText = document.getElementById('progressText');
    if (progressFill) progressFill.style.width = '0%';
    if (progressText) progressText.textContent = '시작 대기 중... (0/8)';
    const logContainer = document.getElementById('logContainer');
    if (logContainer) logContainer.innerHTML = '';
    // 초기 상태표시
    updateConnectionStatus(false);
})();

// ===== 사전점검 버튼 동작: /api/precheck 호출 → 로그 출력 =====
(function wirePrecheck() {
    const btn = document.getElementById('btnPrecheck');
    if (!btn) return;

    btn.addEventListener('click', async (e) => {
        e.preventDefault();

        const ip   = (document.getElementById('serverIP')?.value || '').trim();
        const user = (document.getElementById('username')?.value || '').trim();

        if (!ip || !user) {
            addLog('IP와 사용자명을 입력하세요.', 'error');
            return;
        }

        // 이전 로그 비우고 시작 메시지
        const box = document.getElementById('logContainer');
        if (box) box.innerHTML = '';
        addLog(`사전점검 시작: ${user}@${ip}`, 'info');

        try {
            const res = await fetch(`/api/precheck?ip=${encodeURIComponent(ip)}&user=${encodeURIComponent(user)}`);
            if (!res.ok) {
                addLog(`요청 실패: HTTP ${res.status}`, 'error');
                updateConnectionStatus(false);
                return;
            }
            const data = await res.json(); // { ip, user, checks: [...] }

            let connected = false;

            if (Array.isArray(data.checks)) {
                data.checks.forEach(c => {
                    // ▶ 서버 JSON 키에 맞춰서 읽기
                    const name = c.checkFactorName ?? '';       // ex) "DNS/호스트 확인", "핑(ICMP)", "TCP 포트22"
                    const message = c.message ?? '';
                    const msVal = c.ms;
                    const type = c.ok ? 'success' : 'error';
                    const ms = (msVal != null) ? ` (${msVal}ms)` : '';

                    const text = name ? `${name}: ${message}${ms}` : `${message}${ms}`;
                    addLog(text, type);

                    // 연결 상태 판단 기준(자유롭게 조정 가능)
                    if ((/포트|연결/i.test(name) || /TCP/i.test(name)) && c.ok) {
                        connected = true;
                    }
                });
            } else {
                addLog('응답 형식이 예상과 다릅니다. (checks 배열 없음)', 'error');
            }

            updateConnectionStatus(connected);
            addLog('사전점검 종료', 'info');
        } catch (err) {
            addLog(`오류: ${err}`, 'error');
            updateConnectionStatus(false);
        }
    });
})();
