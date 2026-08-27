package com.example.cinepick.dto.response;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class KobisMovieDto {
    private String movieCd;    // 영화코드
    private String movieNm;    // 영화명(국문)
    private String movieNmEn;  // 영화명(영문)
    private String openDt;     // 개봉일
    private String repGenreNm; // 대표 장르명

    // 감독 정보는 배열로 내려온다
    private List<DirectorDto> directors;

    // 첫 번째 감독 이름만
    public String getFirstDirector() {
        if (directors != null && !directors.isEmpty()) {
            return directors.get(0).getPeopleNm();
        }
        return "정보 없음";
    }

    @Getter
    @Setter
    public static class DirectorDto {
        private String peopleNm; // 감독명
    }
}