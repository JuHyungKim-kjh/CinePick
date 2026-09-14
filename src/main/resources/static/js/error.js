/**
 * 서버 오류 화면(error/5xx.html) 전용.
 * '다시 시도' 버튼 하나만 맡는다 — 인라인 onclick 을 쓰지 않으려고 파일로 분리했다.
 */
(function () {
    'use strict';

    document.addEventListener('DOMContentLoaded', function () {
        var retry = document.getElementById('errorRetry');
        if (!retry) return;

        retry.addEventListener('click', function () {
            // reload() 가 아니라 같은 주소로 다시 간다 —
            // reload 는 직전 요청의 메서드·본문을 그대로 재전송하려 든다
            window.location.assign(window.location.href);
        });
    });
})();
