/**
 * 서버 준비 중 대기 화면(/loading) 전용.
 * 상태를 1초마다 물어보다 준비가 끝나면 원래 주소로 이동한다.
 * 서버 값은 DOM data 속성으로만 읽는다 — .loading-shell 의 data-next = 이동할 주소
 */
(function () {
    'use strict';

    var POLL_INTERVAL = 1000;

    // 이 시간을 넘기면 "오래 걸린다" 안내를 덧붙인다 (첫 실행은 30초 안팎이 정상)
    var SLOW_AFTER_SECONDS = 15;

    document.addEventListener('DOMContentLoaded', function () {
        var shell = document.querySelector('.loading-shell');
        if (!shell) return;

        var next = shell.getAttribute('data-next') || '/';
        var phaseEl = document.getElementById('loadingPhase');
        var barEl = document.getElementById('loadingBar');
        var hintEl = document.getElementById('loadingHint');
        var elapsedEl = document.getElementById('loadingElapsed');

        var defaultHint = hintEl ? hintEl.textContent : '';
        var done = false;

        poll();

        function poll() {
            if (done) return;

            // no-store 가 없으면 브라우저가 첫 응답(ready:false)을 재사용해 영영 안 넘어간다
            fetch('/system/status', { cache: 'no-store', headers: { 'Accept': 'application/json' } })
                .then(function (res) {
                    if (!res.ok) throw new Error('HTTP ' + res.status);
                    return res.json();
                })
                .then(function (status) {
                    render(status);

                    if (status.ready) {
                        done = true;
                        // replace 여야 뒤로가기로 이 대기 화면에 다시 걸리지 않는다
                        window.location.replace(next);
                        return;
                    }
                    setTimeout(poll, POLL_INTERVAL);
                })
                .catch(function () {
                    // 준비 중에는 서버가 재시작 중일 수 있다. 실패해도 계속 두드린다
                    setWarning('서버와 연결을 기다리고 있어요. 잠시 후 자동으로 다시 시도해요.');
                    setTimeout(poll, POLL_INTERVAL);
                });
        }

        function render(status) {
            if (phaseEl && status.phase) phaseEl.textContent = status.phase;
            if (barEl) barEl.style.width = (status.percent || 0) + '%';

            var track = document.querySelector('.loading-track');
            if (track) track.setAttribute('aria-valuenow', String(status.percent || 0));

            var seconds = status.elapsedSeconds || 0;
            if (elapsedEl) elapsedEl.textContent = seconds > 0 ? seconds + '초 경과' : '';

            if (status.degraded) {
                setWarning('일부 상영 정보를 불러오지 못했어요. 화면은 그대로 이용하실 수 있어요.');
            } else if (seconds >= SLOW_AFTER_SECONDS) {
                setHint('예매 사이트 3곳의 상영표를 확인하는 중이라 30초쯤 걸려요. 조금만 더 기다려 주세요.');
            } else {
                setHint(defaultHint);
            }
        }

        function setHint(text) {
            if (!hintEl) return;
            hintEl.classList.remove('is-warning');
            hintEl.textContent = text;
        }

        function setWarning(text) {
            if (!hintEl) return;
            hintEl.classList.add('is-warning');
            hintEl.textContent = text;
        }
    });
})();
