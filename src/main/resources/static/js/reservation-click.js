/*
 * 상세페이지에서 예매사 링크를 누른 사실을 서버에 기록.
 * 여기서 preventDefault() 하면 팝업 차단에 걸려 예매 사이트가 안 열린다 —
 * 이동은 브라우저에 맡기고 기록만 옆에서 보낸다.
 */
(function () {
    const links = document.querySelectorAll('[data-reservation-site]');
    if (links.length === 0) return; // 게스트에게는 서버가 이 속성을 붙이지 않습니다

    links.forEach(function (link) {
        link.addEventListener('click', function () {
            const params = new URLSearchParams();
            params.append('movieCd', link.dataset.movieCd);
            params.append('site', link.dataset.reservationSite);

            fetch('/reservations/click', {
                method: 'POST',
                headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                body: params.toString(),
                // 탭을 곧바로 떠나도 요청이 끊기지 않도록
                keepalive: true
            }).catch(function (err) {
                // 실패해도 이동은 이미 진행 중이라 콘솔에만 남긴다
                console.error('[예매 이력 기록 실패]', err);
            });
        });
    });
})();
