// 백엔드 미구현 상태: 모든 버튼 동작 비활성화
// - '연결 & 사전 점검' 클릭 시 아무 것도 하지 않음
// - '다음 단계' 등 모든 버튼 기본 disabled 상태 (HTML에서 이미 disabled)
// - 로그창도 비워둠 (append 없음)

// 혹시 실수로 이벤트가 남아있을 수 있으니 방지 차원에서 아래처럼 막아둠
(function () {
    const noop = (e) => { if (e && typeof e.preventDefault === 'function') e.preventDefault(); return false; };

    // 존재하면 이벤트 제거/차단
    const ids = [
        'btnPrecheck', 'nextBtn0',
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
            el.onclick = noop;        // 클릭 무시
            el.addEventListener('click', noop, true);
        }
    });

    // 진행률/단계 전환도 고정 (초기 표시만, 변경 없음)
    const progressFill = document.getElementById('progressFill');
    const progressText = document.getElementById('progressText');
    if (progressFill) progressFill.style.width = '0%';
    if (progressText) progressText.textContent = '시작 대기 중... (0/8)';

    // 로그창은 빈 상태 유지
    const logContainer = document.getElementById('logContainer');
    if (logContainer) logContainer.innerHTML = '';
})();