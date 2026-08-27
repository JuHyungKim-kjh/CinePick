package com.example.cinepick.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class MovieDetailResDto {
    private String movieCd;      // 영화코드
    private String title;        // 영화명 (한글)
    private String titleEn;      // 영화명 (영문)
    private String openDate;     // 개봉일
    private String prdtYear;     // 제작연도

    // 상세 페이지에만 쓰는 값
    private String showTm;       // 상영시간 (러닝타임)
    private String actors;       // 출연 배우진
    private String plot;         // 영화 줄거리 (TMDB)

    private String nationAlt;    // 대표국가
    private String genre;        // 전체 장르
    private String director;     // 감독명
    private String posterUrl;    // 포스터 이미지 URL
    private String movieStatus;  // 상영 상태 (NOW, UPCOMING 등)

    private String cgvUrl;
    private String megaboxUrl;
    private String lotteUrl;

    private String dDayLabel;         // "D-7", "D-Day" 등 (계산 불가하면 null)
    private String upcomingDateLabel; // "2026년 08월 12일 재개봉 예정" 등 (계산 불가하면 null)
}