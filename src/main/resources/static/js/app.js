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

// ===== 상태 표시 갱신 =====
function updateConnectionStatus(connected) {
    const dot = document.getElementById("statusDot");
    const text = document.getElementById("statusText");
    if (!dot || !text) return;
    if (connected) {
        dot.classList.add("connected");
        text.textContent = "연결됨";
    } else {
        dot.classList.remove("connected");
        text.textContent = "연결되지 않음";
    }
}

// ===== 진행률/단계 공통 =====
const TOTAL_STEPS = 8; // 0~7
let currentStep = 0;

function setProgressByStep(stepIdx) {
    const fill = document.getElementById("progressFill");
    const txt  = document.getElementById("progressText");
    const pct = Math.max(0, Math.min(100, (stepIdx / (TOTAL_STEPS - 1)) * 100));
    if (fill) fill.style.width = `${pct}%`;
    if (txt)  txt.textContent = `단계 진행 중... (${stepIdx + 1}/${TOTAL_STEPS})`;
}

function setActiveStep(stepIdx) {
    currentStep = stepIdx;

    // 본문 패널
    document.querySelectorAll(".step-content").forEach(el => {
        const s = Number(el.getAttribute("data-step"));
        el.classList.toggle("active", s === stepIdx);
    });

    // 사이드바
    document.querySelectorAll(".step-item").forEach(el => {
        const s = Number(el.getAttribute("data-step"));
        el.classList.toggle("active", s === stepIdx);
        // 이미 지난 단계는 완료 느낌을 주고 싶다면 complete 클래스도 활용 가능
        el.classList.toggle("complete", s < stepIdx);
    });

    // 진행률
    setProgressByStep(stepIdx);

    // 마지막 단계면 finish 버튼 활성
    const finishBtn = document.getElementById("finishBtn");
    if (finishBtn) finishBtn.disabled = (stepIdx !== TOTAL_STEPS - 1);
}

function enableButton(id, enable = true) {
    const el = document.getElementById(id);
    if (el) el.disabled = !enable;
}

// ===== 초기 잠금: "실행" 계열만 잠가두고, next/finish는 우리가 제어 =====
(function lockActionButtons() {
    const ids = [
        'btnWAS','btnTomcat','btnNginx','btnUpload','btnSecurity','btnDB','btnStart'
    ];
    ids.forEach(id => {
        const el = document.getElementById(id);
        if (el) {
            el.disabled = true;
            // 클릭 무력화
            const noop = (e) => { if (e?.preventDefault) e.preventDefault(); return false; };
            el.onclick = noop;
            el.addEventListener('click', noop, true);
        }
    });
})();

// ===== 진행률/로그 초기화 =====
(function initUI() {
    const logContainer = document.getElementById('logContainer');
    if (logContainer) logContainer.innerHTML = '';
    setActiveStep(0); // 첫 화면
    updateConnectionStatus(false);

    // HTML 기본값 유지: nextBtn0~nextBtn6, finishBtn은 HTML에서 disabled 상태
    // (nextBtn0은 사전점검 성공 시 활성화)
})();

// ===== 단계 이동 공통 처리 =====
function goNext(fromIdx) {
    const target = fromIdx + 1;
    // 다음 단계로 화면 전환
    setActiveStep(target);

    // 다음 단계의 "다음 단계" 버튼을 선제적으로 열어둔다(연습용 UX)
    if (target < TOTAL_STEPS - 1) {
        enableButton(`nextBtn${target}`, true);
    } else {
        // 마지막 단계면 finish만 활성
        enableButton('finishBtn', true);
    }
}

// ===== next/finish 버튼 배선 =====
(function wireNextButtons() {
    for (let i = 0; i < TOTAL_STEPS - 1; i++) {
        const btn = document.getElementById(`nextBtn${i}`);
        if (!btn) continue;
        btn.addEventListener('click', (e) => {
            e.preventDefault();
            if (btn.disabled) return;
            addLog(`다음 단계로 이동: ${i + 1} → ${i + 2}`, 'info');
            goNext(i);
        });
    }

    const finish = document.getElementById('finishBtn');
    if (finish) {
        finish.addEventListener('click', (e) => {
            e.preventDefault();
            if (finish.disabled) return;
            setActiveStep(TOTAL_STEPS - 1);
            addLog('설치 마법사 완료! 🎉', 'success');
        });
    }
})();

// ===== 사전점검 버튼 동작: /api/precheck 호출 → 로그 출력 및 nextBtn0 활성화 =====
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
                    const name = c.checkFactorName ?? '';
                    const message = c.message ?? '';
                    const msVal = c.ms;
                    const type = c.ok ? 'success' : 'error';
                    const ms = (msVal != null) ? ` (${msVal}ms)` : '';

                    const text = name ? `${name}: ${message}${ms}` : `${message}${ms}`;
                    addLog(text, type);

                    if ((/포트|연결/i.test(name) || /TCP/i.test(name)) && c.ok) {
                        connected = true;
                    }
                });
            } else {
                addLog('응답 형식이 예상과 다릅니다. (checks 배열 없음)', 'error');
            }

            updateConnectionStatus(connected);
            addLog('사전점검 종료', 'info');

            // ✅ 연결 성공 시, 다음 단계 버튼 활성화
            if (connected) {
                enableButton('nextBtn0', true);
                addLog('다음 단계 버튼이 활성화되었습니다.', 'success');
            } else {
                addLog('연결이 확인되지 않아 다음 단계 이동을 잠시 막았습니다.', 'error');
            }
        } catch (err) {
            addLog(`오류: ${err}`, 'error');
            updateConnectionStatus(false);
        }
    });
})();

// ===== 2단계: WAS 구성 실행(업로드→압축해제→설치→방화벽→서비스) =====
const API_BASE = "http://localhost:8081";  // 스프링 서버 포트

// 사용자 홈 고정 경로(/home/{username}/WAS.tar) 계산
function buildHomeTarPath(username) {
    return (username === 'root') ? '/root/WAS.tar' : `/home/${username}/WAS.tar`;
}

// Step 1 화면에 들어왔을 때 버튼 살리기 + 핸들러 연결
(function wireStep1_WAS() {
    const btn = document.getElementById('btnWAS');
    if (!btn) return;

    // Step 전환될 때마다 활성화/비활성 제어를 위해 감시
    const obs = new MutationObserver(() => {
        const step1Panel = document.querySelector('.step-content[data-step="1"]');
        const active = step1Panel && step1Panel.classList.contains('active');
        btn.disabled = !active;  // Step1이 활성일 때에만 버튼 활성
    });
    obs.observe(document.body, { subtree: true, attributes: true, attributeFilter: ['class'] });

    btn.addEventListener('click', async (e) => {
        e.preventDefault();
        if (btn.disabled) return;

        const ip   = (document.getElementById('serverIP')?.value || '').trim();
        const user = (document.getElementById('username')?.value || '').trim();
        const pw   = (document.getElementById('password')?.value || '').trim();
        const file = document.getElementById('wasFile')?.files?.[0];

        if (!ip || !user || !pw) {
            addLog('IP/사용자명/비밀번호를 입력하세요.', 'error');
            return;
        }
        if (!file) {
            addLog('업로드할 WAS 패키지 파일을 선택하세요.', 'error');
            return;
        }

        // 진행 시작 로그
        addLog(`WAS 구성 시작: ${user}@${ip}`, 'info');

        try {
            // 멀티파트 폼 준비
            const fd = new FormData();
            fd.append('file', file);
            fd.append('ip', ip);
            fd.append('username', user);
            fd.append('password', pw);

            // 백엔드가 /home/{username}/WAS.tar로 고정 업로드하도록 이미 반영했다면
            // remotePath는 안 보내도 되지만, 호환성을 위해 같이 보냄(있어도 무시됨)
            fd.append('remotePath', buildHomeTarPath(user));

            // 압축해제 위치는 표준 배치 경로로 (/opt/app 권장)
            fd.append('extractTo', '/opt/app');

            // 원샷 엔드포인트 호출 (멀티파트)
            const res = await fetch(`${API_BASE}/api/was/deploy-oneclick`, { method: 'POST', body: fd });
            if (!res.ok) {
                addLog(`요청 실패: HTTP ${res.status}`, 'error');
                return;
            }
            const data = await res.json(); // { ip, user, results: [...] }

            // 단계별 로그 출력
            if (Array.isArray(data.results)) {
                data.results.forEach(r => {
                    const line = `${r.name}: ${r.ok ? '성공' : '실패'}${r.durationMs != null ? ` (${r.durationMs}ms)` : ''}`;
                    addLog(line, r.ok ? 'success' : 'error');
                    if (r.message) addLog(r.message, r.ok ? 'info' : 'error');
                });
            } else {
                addLog('응답 형식이 예상과 다릅니다. (results 배열 없음)', 'error');
                return;
            }

            // 전부 성공하면 다음 단계 버튼 열기
            const allOk = data.results.every(r => r.ok);
            if (allOk) {
                addLog('WAS 구성 완료! 다음 단계로 이동 가능합니다.', 'success');
                enableButton('nextBtn1', true);
            } else {
                addLog('일부 단계가 실패했습니다. 로그를 확인하세요.', 'error');
            }
        } catch (err) {
            addLog(`오류: ${err}`, 'error');
        }
    });
})();
