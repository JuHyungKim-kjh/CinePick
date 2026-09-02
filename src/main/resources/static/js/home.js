/**
 * 메인페이지(/) 전용.
 * Swiper 초기화(무비차트, AI 큐레이션 2벌, 박스오피스)와 큐레이션 탭 전환을 맡는다.
 */
(function () {
    'use strict';

    document.addEventListener('DOMContentLoaded', function () {
        initMovieSwiper();
        initSideSwipers();
        initCurationTabs();
    });

    /**
     * 무비차트 슬라이더. 가운데 온 포스터가 확대되고 번호 배지가 빨갛게 바뀐다
     * (CSS 의 .swiper-slide-active 규칙이 처리 — 여기서는 Swiper 설정만).
     */
    function initMovieSwiper() {
        var el = document.querySelector('.movieSwiper');
        if (!el) return;

        // loop 는 최소 노출 개수(5)의 배 이상 슬라이드가 있어야 자연스럽다.
        // 크롤링 결과가 적을 때 억지로 켜면 어색해지거나 콘솔 경고만 남는다.
        var slideCount = el.querySelectorAll('.swiper-slide').length;

        new Swiper('.movieSwiper', {
            slidesPerView: 1,
            spaceBetween: 15,
            grabCursor: true,
            centeredSlides: true,
            loop: slideCount > 10,
            // 화살표는 슬라이더 밖(#shellMovie 안)에 있다 — 안에 두면 잘린다
            navigation: {
                nextEl: '#shellMovie .swiper-button-next',
                prevEl: '#shellMovie .swiper-button-prev',
            },
            breakpoints: {
                // 가운데 포스터가 있어야 하므로 홀수 개수만 쓴다 (짝수면 정중앙이 없다)
                576: { slidesPerView: 3, spaceBetween: 20 },
                992: { slidesPerView: 5, spaceBetween: 24 }
            }
        });
    }

    /** 탭 전환 때 update() 를 불러야 하므로 만든 인스턴스를 id 로 들고 있는다. */
    var sideSwipers = {};

    /** AI 큐레이션 / 박스오피스는 좁은 칸에 들어가므로 노출 개수를 따로 잡는다. */
    function initSideSwipers() {
        // 화살표가 .swiper 밖에 있으므로 껍데기(shell)를 기준으로 찾는다
        ['#shell-curationNow', '#shell-curationAll', '#shellBoxOffice'].forEach(function (shell) {
            var el = document.querySelector(shell + ' .swiper');
            if (!el) return;

            sideSwipers[el.id] = new Swiper(el, {
                slidesPerView: 2,
                spaceBetween: 12,
                grabCursor: true,
                navigation: {
                    nextEl: shell + ' .swiper-button-next',
                    prevEl: shell + ' .swiper-button-prev',
                },
                breakpoints: {
                    768: { slidesPerView: 3, spaceBetween: 14 },
                    992: { slidesPerView: 3, spaceBetween: 14 },
                    1200: { slidesPerView: 4, spaceBetween: 16 }
                }
            });
        });
    }

    /**
     * AI 큐레이션 탭. 두 목록을 이미 다 내려받았으므로 서버를 다시 다녀오지 않는다.
     * 탭 버튼과 빈 안내의 '전체 작품 추천 보기' 가 같은 data 속성을 쓴다.
     */
    function initCurationTabs() {
        var buttons = document.querySelectorAll('[data-curation-target]');
        if (!buttons.length) return;

        buttons.forEach(function (btn) {
            btn.addEventListener('click', function () {
                activate(btn.dataset.curationTarget);
            });
        });
    }

    function activate(targetId) {
        document.querySelectorAll('.curation-pane').forEach(function (pane) {
            pane.classList.toggle('d-none', pane.id !== 'pane-' + targetId);
        });
        document.querySelectorAll('.curation-tab').forEach(function (tab) {
            tab.classList.toggle('is-active', tab.dataset.curationTarget === targetId);
        });

        // 숨어 있는 동안 만들어진 Swiper 는 폭을 0 으로 재서 슬라이드가 겹쳐 보인다.
        // 화면에 나온 뒤 한 번 다시 재게 한다
        var swiper = sideSwipers[targetId];
        if (swiper) swiper.update();
    }
})();
