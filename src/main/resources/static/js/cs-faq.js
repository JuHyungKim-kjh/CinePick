/**
 * 고객지원 화면의 접기/펴기와 분류 거르개.
 * FAQ 와 1:1 상담의 내 문의 내역이 같은 구조(.cs-faq-item)라 한 파일을 공유한다.
 */
document.addEventListener('DOMContentLoaded', function () {

    var items = Array.prototype.slice.call(document.querySelectorAll('.cs-faq-item'));
    if (items.length === 0) return;

    // ---- 접기/펴기 ----
    // 하나를 열 때 나머지를 닫지 않는다 (여러 답을 나란히 읽는 경우가 많음)
    items.forEach(function (item) {
        var question = item.querySelector('.cs-faq-question');
        if (!question) return;
        question.addEventListener('click', function () {
            item.classList.toggle('is-open');
        });
    });

    // ---- 분류 거르개 ----
    var filter = document.querySelector('.cs-filter');
    if (!filter) return;

    var chips = Array.prototype.slice.call(filter.querySelectorAll('.cs-chip'));
    var empty = document.getElementById('faqEmpty');

    filter.addEventListener('click', function (event) {
        var chip = event.target.closest('.cs-chip');
        if (!chip) return;

        chips.forEach(function (c) { c.classList.remove('cs-chip-active'); });
        chip.classList.add('cs-chip-active');

        var selected = chip.dataset.category;
        var shown = 0;

        items.forEach(function (item) {
            var match = (selected === 'all') || (item.dataset.category === selected);
            item.classList.toggle('d-none', !match);
            // 감춘 항목이 열린 채로 남으면 다시 보일 때 뜬금없이 펼쳐져 있음
            if (!match) item.classList.remove('is-open');
            if (match) shown++;
        });

        // 결과가 없으면 카드가 통째로 비어 고장처럼 보임
        if (empty) empty.classList.toggle('d-none', shown > 0);
    });

});
