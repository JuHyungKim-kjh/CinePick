(function () {
    function showSurveyError(message) {
        const errorBox = document.getElementById('surveyPromptError');
        if (errorBox) {
            errorBox.textContent = message;
            errorBox.style.display = 'block';
        }
        console.error('[설문조사 저장 실패]', message);
    }

    const overlay = document.getElementById('surveyPromptOverlay');
    if (!overlay) return;

    const isLoggedIn = overlay.dataset.loggedIn === 'true';
    const autoShow = overlay.dataset.autoShow === 'true';

    // autoShow="true"(홈)일 때만 로딩 즉시 자동으로 띄움
    if (autoShow) {
        overlay.style.display = 'flex';
    }

    const closeBtn = document.getElementById('surveyPromptClose');
    if (closeBtn) {
        closeBtn.addEventListener('click', function () {
            overlay.style.display = 'none';
            // 로그인 사용자는 건너뛰기 이력을 회원 정보에 남긴다 (게스트는 서버가 세션에 표시)
            if (isLoggedIn) {
                fetch('/survey/dismiss', { method: 'POST' }).catch(() => {});
            }
        });
    }

    const screenWelcome = document.getElementById('promptScreenWelcome');
    const screenGenre = document.getElementById('promptScreenGenre');
    const screenMood = document.getElementById('promptScreenMood');
    const startBtn = document.getElementById('surveyStartBtn');
    const genreNextBtn = document.getElementById('promptGenreNextBtn');
    const moodPrevBtn = document.getElementById('promptMoodPrevBtn');
    const submitBtn = document.getElementById('promptSubmitBtn');

    function showScreen(screen) {
        [screenWelcome, screenGenre, screenMood].forEach(s => s.classList.add('d-none'));
        screen.classList.remove('d-none');
    }

    if (startBtn) {
        startBtn.addEventListener('click', function () {
            showScreen(screenGenre);
        });
    }

    const MAX_RANK = 3;
    let selectedGenres = [];
    let selectedMoods = [];

    function setupRankGrid(gridId, selectedArr, onChange) {
        document.getElementById(gridId).querySelectorAll('.survey-rank-btn').forEach(btn => {
            btn.addEventListener('click', function () {
                const value = btn.dataset.value;
                const idx = selectedArr.indexOf(value);
                if (idx !== -1) selectedArr.splice(idx, 1);
                else if (selectedArr.length < MAX_RANK) selectedArr.push(value);
                renderRankGrid(gridId, selectedArr);
                onChange();
            });
        });
    }

    function renderRankGrid(gridId, selectedArr) {
        document.getElementById(gridId).querySelectorAll('.survey-rank-btn').forEach(btn => {
            const rank = selectedArr.indexOf(btn.dataset.value);
            const oldBadge = btn.querySelector('.survey-rank-badge');
            if (oldBadge) oldBadge.remove();
            if (rank !== -1) {
                btn.classList.add('selected');
                const badge = document.createElement('span');
                badge.className = 'survey-rank-badge';
                badge.textContent = String(rank + 1);
                btn.appendChild(badge);
            } else {
                btn.classList.remove('selected');
            }
        });
    }

    setupRankGrid('promptGenreGrid', selectedGenres, function () {
        genreNextBtn.disabled = selectedGenres.length !== MAX_RANK;
    });
    setupRankGrid('promptMoodGrid', selectedMoods, function () {
        submitBtn.disabled = selectedMoods.length !== MAX_RANK;
    });

    if (genreNextBtn) {
        genreNextBtn.addEventListener('click', function () {
            showScreen(screenMood);
        });
    }
    if (moodPrevBtn) {
        moodPrevBtn.addEventListener('click', function () {
            showScreen(screenGenre);
        });
    }

    if (submitBtn) {
        submitBtn.addEventListener('click', function () {
            submitBtn.disabled = true;
            submitBtn.textContent = '저장 중...';

            const params = new URLSearchParams();
            selectedGenres.forEach(g => params.append('genres', g));
            selectedMoods.forEach(m => params.append('moods', m));

            fetch('/survey/submit', {
                method: 'POST',
                headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                body: params.toString()
            })
                .then(function (res) {
                    return res.text().then(function (text) {
                        if (!res.ok) {
                            throw new Error('HTTP ' + res.status + ' - ' + text);
                        }
                        return text;
                    });
                })
                .then(function () {
                    window.location.href = '/recommendations';
                })
                .catch(function (err) {
                    showSurveyError(err.message);
                    submitBtn.disabled = false;
                    submitBtn.textContent = '취향 분석 시작하기';
                });
        });
    }
})();