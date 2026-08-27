package com.example.cinepick.dto.response;

import lombok.Builder;
import lombok.Getter;

/**
 * 평점 관리 화면의 한 줄.
 * 목록 기준은 "확정된 예매가 있는 영화". 같은 영화를 여러 번 예매해도 한 줄만 나온다.
 */
@Getter
@Builder
public class RatingResDto {

    private String movieCd;
    private String title;
    private String posterUrl;
    private String genre;

    /** 아직 평점을 남기지 않았으면 0 */
    private int score;
    private String review;

    /** 평점을 남긴 적이 있는지 (저장/수정 문구 결정) */
    private boolean rated;

    /** 마지막 예매 확정일 (yyyy.MM.dd) */
    private String reservedAt;

    /** 평점을 남긴 날 (yyyy.MM.dd). 없으면 null */
    private String ratedAt;
}
