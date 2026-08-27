/**
 * 메인페이지(/) 전용. Swiper 3개(무비차트, AI 큐레이션, 박스오피스) 초기화.
 */
(function () {
    'use strict';

    document.addEventListener('DOMContentLoaded', function () {
        initMovieSwiper();
        initSideSwipers();
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
            navigation: {
                nextEl: '.swiper-button-next',
                prevEl: '.swiper-button-prev',
            },
            breakpoints: {
                // 가운데 포스터가 있어야 하므로 홀수 개수만 쓴다 (짝수면 정중앙이 없다)
                576: { slidesPerView: 3, spaceBetween: 20 },
                992: { slidesPerView: 5, spaceBetween: 24 }
            }
        });
    }

    /** AI 큐레이션 / 박스오피스는 좁은 칸에 들어가므로 노출 개수를 따로 잡는다. */
    function initSideSwipers() {
        ['.curationSwiper', '.boxOfficeSwiper'].forEach(function (selector) {
            if (!document.querySelector(selector)) return;
            new Swiper(selector, {
                slidesPerView: 2,
                spaceBetween: 12,
                grabCursor: true,
                navigation: {
                    nextEl: selector + ' .swiper-button-next',
                    prevEl: selector + ' .swiper-button-prev',
                },
                breakpoints: {
                    768: { slidesPerView: 3, spaceBetween: 14 },
                    992: { slidesPerView: 3, spaceBetween: 14 },
                    1200: { slidesPerView: 4, spaceBetween: 16 }
                }
            });
        });
    }
})();
