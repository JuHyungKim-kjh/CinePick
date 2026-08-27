package com.example.cinepick.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.Set;

@Getter
@Setter
@Builder
public class MovieListResDto {
    private String movieCd;      // 영화코드
    private String title;        // 영화명 (한글)
    private String openDate;     // 개봉일
    private String genre;        // 대표 장르
    private String director;     // 감독명
    private String posterUrl;    // 썸네일 포스터 URL
}