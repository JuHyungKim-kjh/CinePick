/*
 * 평점 관리 화면의 저장 처리.
 * 언제든 고칠 수 있으므로 저장 후에도 입력 상태를 그대로 둔다.
 */
(function () {
    const rows = document.querySelectorAll('.rating-row');
    if (rows.length === 0) return;

    function selectedScore(row) {
        const checked = row.querySelector('.star-rating input:checked');
        return checked ? checked.value : null;
    }

    function showFeedback(row, message, isError) {
        const box = row.querySelector('.rating-feedback');
        if (!box) return;
        box.textContent = message;
        box.className = 'rating-feedback ' + (isError ? 'is-error' : 'is-success');
    }

    rows.forEach(function (row) {
        const saveBtn = row.querySelector('.rating-save-btn');
        if (!saveBtn) return;

        saveBtn.addEventListener('click', function () {
            const score = selectedScore(row);
            if (!score) {
                showFeedback(row, '별점을 선택해주세요.', true);
                return;
            }

            const reviewInput = row.querySelector('.rating-review-input');
            const review = reviewInput ? reviewInput.value : '';

            const originalLabel = saveBtn.textContent;
            saveBtn.disabled = true;
            saveBtn.textContent = '저장 중';

            const params = new URLSearchParams();
            params.append('movieCd', row.dataset.movieCd);
            params.append('score', score);
            params.append('review', review);

            fetch('/ratings/save', {
                method: 'POST',
                headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                body: params.toString()
            })
                .then(function (res) {
                    return res.json().then(function (data) {
                        return { ok: res.ok, data: data };
                    });
                })
                .then(function (result) {
                    saveBtn.disabled = false;

                    if (!result.ok) {
                        showFeedback(row, result.data.message || '저장하지 못했어요.', true);
                        saveBtn.textContent = originalLabel;
                        return;
                    }

                    showFeedback(row, result.data.message, false);
                    // 한 번이라도 저장했으면 그 뒤로는 '수정'
                    saveBtn.textContent = '수정';
                })
                .catch(function (err) {
                    showFeedback(row, '저장 중 문제가 생겼어요. 잠시 후 다시 시도해주세요.', true);
                    console.error('[평점 저장 실패]', err);
                    saveBtn.disabled = false;
                    saveBtn.textContent = originalLabel;
                });
        });
    });
})();
