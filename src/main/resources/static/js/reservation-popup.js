/*
 * 예매 확인 팝업 제어.
 * 닫기 수단(X / 배경 / ESC)을 일부러 두지 않았다 — 답하지 않으면 미확인으로 남아 다음 화면에서 다시 뜬다.
 *
 * 뜨는 경로 두 가지:
 *  1) 화면을 새로 그릴 때 — 서버가 내용을 채워 보낸다
 *  2) 예매 사이트에 다녀와 이 탭으로 돌아왔을 때 — 복귀를 감지해 직접 물어본다
 */
(function () {
    const overlay = document.getElementById('reservationConfirmOverlay');
    if (!overlay) return;

    const posterEl = document.getElementById('reservationConfirmPoster');
    const siteEl = document.getElementById('reservationConfirmSite');
    const titleEl = document.getElementById('reservationConfirmTitle');
    const dateEl = document.getElementById('reservationConfirmDate');
    const progressEl = document.getElementById('reservationConfirmProgress');
    const errorEl = document.getElementById('reservationConfirmError');
    const yesBtn = document.getElementById('reservationConfirmYes');
    const noBtn = document.getElementById('reservationConfirmNo');

    function isVisible() {
        return overlay.style.display !== 'none';
    }

    function setBusy(busy) {
        yesBtn.disabled = busy;
        noBtn.disabled = busy;
    }

    function showError(message) {
        errorEl.textContent = message;
        errorEl.style.display = 'block';
    }

    function clearError() {
        errorEl.textContent = '';
        errorEl.style.display = 'none';
    }

    function hide() {
        // DOM 에서 지우지 않고 숨기기만 한다. 껍데기를 다시 채워 쓰기 때문
        overlay.style.display = 'none';
        overlay.removeAttribute('data-reservation-id');
    }

    // 팝업 내용 교체. 다음 미확인 건으로 넘어갈 때와 탭 복귀로 처음 띄울 때 모두 사용
    function showNext(next, remaining) {
        overlay.dataset.reservationId = next.id;
        posterEl.src = next.posterUrl || '/images/Logo.png';
        posterEl.alt = next.title;
        siteEl.textContent = next.site;
        titleEl.textContent = next.title;
        dateEl.textContent = next.clickedAt;
        progressEl.textContent = remaining > 1 ? '확인할 예매가 ' + remaining + '건 더 있어요' : '';
        clearError();
        setBusy(false);
        overlay.style.display = 'flex';
    }

    function decide(booked) {
        const id = overlay.dataset.reservationId;
        if (!id) return;

        clearError();
        setBusy(true);

        fetch('/reservations/' + id + '/' + (booked ? 'confirm' : 'cancel'), { method: 'POST' })
            .then(function (res) {
                if (!res.ok) throw new Error('HTTP ' + res.status);
                return res.json();
            })
            .then(function (data) {
                if (data.next) {
                    showNext(data.next, data.remaining);
                } else {
                    hide();
                }
            })
            .catch(function (err) {
                // 실패하면 팝업을 닫지 않는다 — 확인을 받아내는 것이 이 팝업의 목적
                showError('처리 중 문제가 생겼어요. 잠시 후 다시 시도해주세요.');
                console.error('[예매 확인 실패]', err);
                setBusy(false);
            });
    }

    yesBtn.addEventListener('click', function () { decide(true); });
    noBtn.addEventListener('click', function () { decide(false); });

    // ---- 탭 복귀 감지 ----
    let checking = false;
    let checkTimer = null;

    function checkPending() {
        if (checking || isVisible()) return; // 이미 묻고 있으면 건드리지 않습니다
        checking = true;

        fetch('/reservations/pending')
            .then(function (res) {
                if (!res.ok) throw new Error('HTTP ' + res.status);
                return res.json();
            })
            .then(function (data) {
                // 클릭 직후 건은 서버가 유예로 걸러내므로 비어 있을 수 있다
                if (data.next && !isVisible()) {
                    showNext(data.next, data.remaining);
                }
            })
            .catch(function (err) {
                // 실패해도 사용자가 할 수 있는 일이 없다. 콘솔에만 남긴다
                console.error('[미확인 예매 조회 실패]', err);
            })
            .finally(function () {
                checking = false;
            });
    }

    // 복귀 한 번에 visibilitychange 와 focus 가 연달아 들어오므로 묶어서 한 번만 조회
    function scheduleCheck() {
        clearTimeout(checkTimer);
        checkTimer = setTimeout(checkPending, 200);
    }

    document.addEventListener('visibilitychange', function () {
        if (document.visibilityState === 'visible') scheduleCheck();
    });
    window.addEventListener('focus', scheduleCheck);
    // 뒤로가기(bfcache) 복귀 — 이때는 DOMContentLoaded 가 다시 뛰지 않는다
    window.addEventListener('pageshow', function (event) {
        if (event.persisted) scheduleCheck();
    });
})();
