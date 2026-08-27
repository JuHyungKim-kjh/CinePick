package com.example.cinepick.service;

import com.example.cinepick.domain.Movie;

import java.util.Map;

/**
 * 영화 한 편의 감정적 분위기를 점수로 매긴다.
 *
 * movie 테이블에 분위기 컬럼이 없어 어딘가에서 만들어내야 하고, 그 "어딘가"를 갈아끼울 수
 * 있도록 인터페이스로 분리했다. LLM 구현을 붙이더라도 실패·미태깅 영화를 위한
 * 장르 기반 폴백({@link GenreBasedMoodTagger})은 계속 필요하다.
 */
public interface MoodTagger {

    /**
     * 이 값 이상이면 그 영화의 "두드러지는 분위기"로 본다.
     * 추천 점수(RecommendService)와 취향 점수(PreferenceService)가 같은 기준을 써야
     * 화면의 분석과 실제 추천이 어긋나지 않으므로 여기 한곳에 둔다.
     */
    int PRESENCE_THRESHOLD = 50;

    /** 분위기 이름 → 0~100 점수. 가장 강한 분위기가 100 이 되도록 정규화한다 */
    Map<String, Integer> tag(Movie movie);
}
