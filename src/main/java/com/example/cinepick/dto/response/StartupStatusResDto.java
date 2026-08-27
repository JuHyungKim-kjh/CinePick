package com.example.cinepick.dto.response;

import lombok.Builder;
import lombok.Getter;

/**
 * 로딩 화면이 1초마다 물어보는 서버 준비 상태.
 * 화면은 이 값만 보고 움직인다 — 준비 단계가 늘어나도 phase 문구만 바뀌고 화면은 그대로다.
 */
@Getter
@Builder
public class StartupStatusResDto {

    /** true 가 되는 순간 화면이 원래 가려던 주소로 이동한다 */
    private boolean ready;

    /** 사용자에게 그대로 보여줄 현재 단계 문구 ("CGV 현재상영작을 확인했어요") */
    private String phase;

    /** 진행률 0~100. 단계 수를 화면이 몰라도 되도록 서버가 환산해 넘긴다 */
    private int percent;

    /** 준비를 시작한 뒤 흐른 시간(초). 오래 걸릴 때 안내 문구를 바꾸는 데 쓴다 */
    private long elapsedSeconds;

    /**
     * 크롤링이 실패했지만 화면은 열어준 상태인지.
     * 실패해도 ready 는 true 로 올린다(사용자를 대기 화면에 가둘 수 없다). 대신 이 값으로
     * "일부 정보가 비어 있을 수 있다"고 알린다.
     */
    private boolean degraded;
}
