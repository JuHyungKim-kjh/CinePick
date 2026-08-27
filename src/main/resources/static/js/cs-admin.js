/**
 * 고객지원 관리 화면의 삭제 확인.
 * 버튼이 아니라 폼 submit 을 가로챈다 — 엔터로 제출하는 경로가 남기 때문.
 */
document.addEventListener('DOMContentLoaded', function () {

    document.querySelectorAll('.cs-delete-form').forEach(function (form) {
        form.addEventListener('submit', function (event) {
            var message = form.dataset.confirm || '삭제할까요? 되돌릴 수 없습니다.';
            if (!window.confirm(message)) {
                event.preventDefault();
            }
        });
    });

});
