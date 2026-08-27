package com.example.cinepick.dto.response;

import lombok.Builder;
import lombok.Getter;

/** AI 맞춤형 큐레이션 카드 한 장. */
@Getter
@Builder
public class AiRecommendResDto {

    private String movieCd;
    private String title;
    private String posterUrl;
    private String genre;
    private String openDate;

    /** 취향 적합도 (가장 잘 맞는 작품을 100으로 둔 상대값) */
    private int matchPercent;
}
