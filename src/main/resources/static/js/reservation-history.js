/*
 * 예매/취소 내역 화면에서 미확인 건을 그 자리에서 확정.
 * 처리 후 상태 배지와 버튼을 다시 그려야 해서 새로고침한다.
 */
(function () {
    const buttons = document.querySelectorAll('.history-decide');
    if (buttons.length === 0) return;

    buttons.forEach(function (btn) {
        btn.addEventListener('click', function () {
            const id = btn.dataset.id;
            const booked = btn.dataset.booked === 'true';

            // 같은 행의 버튼을 모두 잠가 두 번 눌리는 것을 막는다
            const row = btn.closest('.history-row');
            row.querySelectorAll('.history-decide').forEach(function (b) { b.disabled = true; });

            fetch('/reservations/' + id + '/' + (booked ? 'confirm' : 'cancel'), { method: 'POST' })
                .then(function (res) {
                    if (!res.ok) throw new Error('HTTP ' + res.status);
                    window.location.reload();
                })
                .catch(function (err) {
                    console.error('[예매 확인 실패]', err);
                    row.querySelectorAll('.history-decide').forEach(function (b) { b.disabled = false; });
                });
        });
    });
})();
