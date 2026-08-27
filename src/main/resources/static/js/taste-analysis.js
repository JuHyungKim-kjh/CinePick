/**
 * 취향 분석 페이지(/recommendations) 전용.
 * 서버 값은 DOM data 속성과 input value 로만 읽는다.
 *  - #moodBars .pref-bar-row : data-label / data-percent / data-color / data-avoided → 레이더 차트
 *  - .axis-row : data-axis + 내부 input[type=range] 의 value → 취향 조절판
 */
(function () {
    'use strict';

    document.addEventListener('DOMContentLoaded', function () {
        setupSurveyPopupOpener();
        renderMoodRadar();
        setupGenreViewToggle();
        setupPreferenceEditor();
    });

    // ---- 설문 전 화면: "설문하러 가기" 버튼 ----
    function setupSurveyPopupOpener() {
        var openBtn = document.getElementById('openSurveyPopupBtn');
        var overlay = document.getElementById('surveyPromptOverlay');
        if (!openBtn || !overlay) return;

        openBtn.addEventListener('click', function () {
            overlay.style.display = 'flex';
        });
    }

    // ---- 분위기 레이더 차트 (외부 라이브러리 없이 SVG) ----
    function renderMoodRadar() {
        var container = document.getElementById('moodRadar');
        var barsBox = document.getElementById('moodBars');
        if (!container || !barsBox) return;

        var rows = Array.prototype.slice.call(barsBox.querySelectorAll('.pref-bar-row'));
        if (rows.length < 3) return; // 축이 3개 미만이면 도형이 되지 않습니다

        var AVOID_COLOR = '#4DABF7'; // 취향을 낮추는 쪽을 뜻하는 색 (CSS의 .weight-down 과 동일)

        var moods = rows.map(function (row) {
            return {
                label: row.getAttribute('data-label') || '',
                // 서버가 0~100 으로 잘라 내려준다. 음수 반지름은 도형을 뒤집는다
                percent: parseInt(row.getAttribute('data-percent'), 10) || 0,
                color: row.getAttribute('data-color') || '#FA5252',
                // 0 아래로 내려간 축. 꼭짓점이 0% 에 눌려 '이력 없음'과 구분되지 않으므로 색으로 표시
                avoided: row.getAttribute('data-avoided') === 'true'
            };
        });

        var n = moods.length;
        var size = 240;
        var center = size / 2;
        var maxR = 78;

        function point(i, r) {
            var angle = (-90 + i * (360 / n)) * Math.PI / 180;
            return [center + r * Math.cos(angle), center + r * Math.sin(angle)];
        }

        var svg = '<svg viewBox="0 0 ' + size + ' ' + size + '" width="100%" height="240">';

        // 배경 격자
        [0.25, 0.5, 0.75, 1].forEach(function (ratio) {
            var pts = [];
            for (var i = 0; i < n; i++) {
                var p = point(i, maxR * ratio);
                pts.push(p[0] + ',' + p[1]);
            }
            svg += '<polygon points="' + pts.join(' ') + '" fill="none" stroke="#e9ecef" stroke-width="1"/>';
        });

        // 축선
        for (var i = 0; i < n; i++) {
            var axis = point(i, maxR);
            svg += '<line x1="' + center + '" y1="' + center + '" x2="' + axis[0] + '" y2="' + axis[1] +
                '" stroke="#e9ecef" stroke-width="1"/>';
        }

        // 데이터 영역
        var dataPts = [];
        for (var j = 0; j < n; j++) {
            var dp = point(j, maxR * (moods[j].percent / 100));
            dataPts.push(dp[0] + ',' + dp[1]);
        }
        svg += '<polygon points="' + dataPts.join(' ') +
            '" fill="rgba(250,82,82,0.25)" stroke="#FA5252" stroke-width="2" stroke-linejoin="round"/>';

        // 꼭짓점 + 라벨
        for (var k = 0; k < n; k++) {
            var mood = moods[k];
            var vertex = point(k, maxR * (mood.percent / 100));
            svg += '<circle cx="' + vertex[0] + '" cy="' + vertex[1] + '" r="3.5" fill="' +
                (mood.avoided ? AVOID_COLOR : mood.color) + '"/>';

            var label = point(k, maxR + 22);
            svg += '<text x="' + label[0] + '" y="' + label[1] +
                '" font-size="11" font-weight="600" text-anchor="middle" dominant-baseline="middle" fill="' +
                (mood.avoided ? AVOID_COLOR : '#495057') + '">' +
                escapeHtml(mood.label) + (mood.avoided ? ' ▼' : '') + '</text>';
        }

        svg += '</svg>';
        container.innerHTML = svg;
    }

    // ---- 선호 장르: 비율 보기 / 순위 보기 전환 ----
    function setupGenreViewToggle() {
        var toggle = document.getElementById('genreViewToggle');
        if (!toggle) return;

        var buttons = Array.prototype.slice.call(toggle.querySelectorAll('.pref-view-btn'));
        var card = toggle.closest('.card');
        if (!card) return;

        buttons.forEach(function (btn) {
            btn.addEventListener('click', function () {
                var view = btn.getAttribute('data-view');

                buttons.forEach(function (other) {
                    other.classList.toggle('active', other === btn);
                });

                card.querySelectorAll('.pref-bar-percent').forEach(function (el) {
                    el.classList.toggle('d-none', view === 'rank');
                });
                card.querySelectorAll('.pref-rank-mark').forEach(function (el) {
                    el.classList.toggle('d-none', view !== 'rank');
                });
            });
        });
    }

    // ---- 취향 수정: 축 슬라이더 13개 + 판단 비중 + 추천 해석 ----
    function setupPreferenceEditor() {
        var saveBtn = document.getElementById('savePreferenceBtn');
        var genreAxes = document.getElementById('editGenreAxes');
        var moodAxes = document.getElementById('editMoodAxes');
        if (!saveBtn || !genreAxes || !moodAxes) return;

        var axisRows = Array.prototype.slice.call(
            document.querySelectorAll('#editGenreAxes .axis-row, #editMoodAxes .axis-row'));

        var weightSlider = document.getElementById('surveyWeight');
        var genreSlider = document.getElementById('genreDiversity');
        var moodSlider = document.getElementById('moodIntensity');

        // 되돌리기가 쓸 초기 상태
        var initial = {
            axes: axisRows.map(function (row) { return rangeOf(row).value; }),
            weight: weightSlider ? weightSlider.value : null,
            diversity: genreSlider ? genreSlider.value : null,
            intensity: moodSlider ? moodSlider.value : null
        };

        axisRows.forEach(function (row) {
            rangeOf(row).addEventListener('input', function () { syncAxisRow(row); });
            syncAxisRow(row);
        });

        bindSlider(genreSlider, document.getElementById('genreDiversityValue'));
        bindSlider(moodSlider, document.getElementById('moodIntensityValue'));

        if (weightSlider) {
            weightSlider.addEventListener('input', syncWeight);
            syncWeight();
        }

        function syncWeight() {
            var w = parseInt(weightSlider.value, 10);
            syncRangeFill(weightSlider);
            setText('surveyWeightValue', String(w));
            setWidth('weightSplitSurvey', w);
            setWidth('weightSplitHistory', 100 - w);
            setText('weightLabelSurvey', '내 취향 ' + w + '%');
            setText('weightLabelHistory', '관람 기록 ' + (100 - w) + '%');
        }

        // 되돌리기: 저장하지 않고 화면을 처음 상태로 복원
        var resetBtn = document.getElementById('resetPreferenceBtn');
        if (resetBtn) {
            resetBtn.addEventListener('click', function () {
                axisRows.forEach(function (row, i) {
                    rangeOf(row).value = initial.axes[i];
                    syncAxisRow(row);
                });
                if (weightSlider) { weightSlider.value = initial.weight; syncWeight(); }
                if (genreSlider) {
                    genreSlider.value = initial.diversity;
                    syncSliderLabel(genreSlider, document.getElementById('genreDiversityValue'));
                }
                if (moodSlider) {
                    moodSlider.value = initial.intensity;
                    syncSliderLabel(moodSlider, document.getElementById('moodIntensityValue'));
                }
                hideError();
            });
        }

        saveBtn.addEventListener('click', function () {
            saveBtn.disabled = true;
            var originalHtml = saveBtn.innerHTML;
            saveBtn.textContent = '저장 중...';
            hideError();

            // 축 이름과 값을 같은 순서의 평행 배열로 보낸다 — 파라미터 이름에 한글이 들어가지 않도록
            var params = new URLSearchParams();
            axisRows.forEach(function (row) {
                params.append('axisNames', row.getAttribute('data-axis'));
                params.append('axisValues', rangeOf(row).value);
            });
            if (weightSlider) params.append('surveyWeight', weightSlider.value);
            if (genreSlider) params.append('genreDiversity', genreSlider.value);
            if (moodSlider) params.append('moodIntensity', moodSlider.value);

            fetch('/survey/submit', {
                method: 'POST',
                headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                body: params.toString()
            })
                .then(function (res) {
                    return res.text().then(function (text) {
                        if (!res.ok) throw new Error('HTTP ' + res.status + ' - ' + text);
                        return text;
                    });
                })
                .then(function () {
                    window.location.reload();
                })
                .catch(function (err) {
                    showError(err.message);
                    console.error('[취향 수정 저장 실패]', err.message);
                    saveBtn.innerHTML = originalHtml;
                    saveBtn.disabled = false;
                });
        });
    }

    function rangeOf(row) {
        return row.querySelector('.axis-range');
    }

    /** 채워진 구간을 CSS(--pct)에 알린다. WebKit 에 진행 구간 의사요소가 없어 직접 갱신 */
    function syncRangeFill(input) {
        if (!input) return;
        var min = Number(input.min || 0);
        var max = Number(input.max || 100);
        var percent = max === min ? 0 : (Number(input.value) - min) * 100 / (max - min);
        input.style.setProperty('--pct', percent + '%');
    }

    /** 슬라이더 옆 숫자와 '값 0' 흐림 처리를 맞춘다 */
    function syncAxisRow(row) {
        var input = rangeOf(row);
        var value = parseInt(input.value, 10);
        var label = row.querySelector('.axis-value');
        if (label) label.textContent = String(value);
        row.classList.toggle('is-zero', value === 0);
        syncRangeFill(input);
    }

    function setText(id, text) {
        var el = document.getElementById(id);
        if (el) el.textContent = text;
    }

    function setWidth(id, percent) {
        var el = document.getElementById(id);
        if (el) el.style.width = percent + '%';
    }

    // ---- 슬라이더 ----
    function bindSlider(slider, valueLabel) {
        if (!slider) return;
        syncSliderLabel(slider, valueLabel);
        slider.addEventListener('input', function () {
            syncSliderLabel(slider, valueLabel);
        });
    }

    function syncSliderLabel(slider, valueLabel) {
        if (valueLabel) valueLabel.textContent = slider.value;
        syncRangeFill(slider);
    }

    // ---- 유틸 ----
    function showError(message) {
        var box = document.getElementById('preferenceEditError');
        if (!box) return;
        box.textContent = message;
        box.style.display = 'block';
    }

    function hideError() {
        var box = document.getElementById('preferenceEditError');
        if (!box) return;
        box.textContent = '';
        box.style.display = 'none';
    }

    function escapeHtml(text) {
        return String(text)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;');
    }
})();
