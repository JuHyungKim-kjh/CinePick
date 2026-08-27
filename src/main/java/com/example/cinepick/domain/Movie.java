package com.example.cinepick.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
public class Movie {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // 우리 DB의 내부 PK

    @Column(unique = true, nullable = false)
    private String movieCd; // KOBIS 영화 코드 (중복 저장 방지용 기준값)

    @Column(nullable = false)
    private String title; // 영화명 (movieNm)

    private String titleEn; // 영문 영화명 (movieNmEn)
    private String openDate; // 개봉일 (openDt)
    private String genre; // 대표 장르 (repGenreNm)
    private String director; // 감독 (directors 배열의 첫 번째 이름)

    // KOBIS 에 없어 다른 경로로 채우는 UI 표시용 필드
    private String posterUrl; // 영화 포스터 이미지 링크

    private String cgvUrl;     // CGV 상세페이지 링크 (상영 안 하면 null)
    private String megaboxUrl; // 메가박스 상세페이지 링크
    private String lotteUrl;   // 롯데시네마 상세페이지 링크

    @Column(columnDefinition = "double default 0.0")
    private Double averageRating; // CinePick 자체 평점 평균

    // AI 가 분위기를 판단할 근거로 삼는 줄거리. TMDB 상세에서 가져온다
    @Column(columnDefinition = "TEXT")
    private String overview;

    // ===== AI 분위기 분석 결과 캐시 (0~100) =====
    // moodTaggedAt 이 null 이면 아직 분석 전이며 장르 조합 기반 분석으로 대체된다
    private Integer moodCheerful;   // 유쾌
    private Integer moodTense;      // 긴장
    private Integer moodSad;        // 슬픔
    private Integer moodCatharsis;  // 카타르시스
    private Integer moodMystic;     // 신비

    private LocalDateTime moodTaggedAt;

    // 분석 시도 횟수. 반복 실패하는 영화를 무한 재시도하며 비용을 태우지 않기 위한 안전장치
    @Column(nullable = false, columnDefinition = "int default 0")
    private int moodTagAttempts = 0;

    /** AI 분위기 분석이 끝난 영화인지 */
    public boolean hasMoodTags() {
        return moodTaggedAt != null
                && moodCheerful != null && moodTense != null && moodSad != null
                && moodCatharsis != null && moodMystic != null;
    }
}