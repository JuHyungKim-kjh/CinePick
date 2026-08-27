package com.example.cinepick.service;

import com.example.cinepick.domain.Movie;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 영화가 가진 장르들의 분위기 가중치를 합산해 분위기 프로필을 만든다.
 * 단일 장르가 아니라 조합을 보므로 「액션+SF+모험」과 「액션+드라마」가 다른 결과를 낸다.
 */
@Component
public class GenreBasedMoodTagger implements MoodTagger {

    // 분위기 축 (레이더 차트 축 순서와 동일하게 유지)
    static final String CHEERFUL = "유쾌";
    static final String TENSE = "긴장";
    static final String SAD = "슬픔";
    static final String CATHARSIS = "카타르시스";
    static final String MYSTIC = "신비";

    /** TMDB 장르(한글) → {유쾌, 긴장, 슬픔, 카타르시스, 신비} 가중치 0~3 */
    private static final Map<String, int[]> GENRE_MOOD_WEIGHTS = new LinkedHashMap<>();

    static {
        //                             유쾌 긴장 슬픔 카타 신비
        GENRE_MOOD_WEIGHTS.put("코미디", new int[]{3, 0, 0, 1, 0});
        GENRE_MOOD_WEIGHTS.put("가족", new int[]{3, 0, 1, 1, 0});
        GENRE_MOOD_WEIGHTS.put("음악", new int[]{2, 0, 1, 2, 0});
        GENRE_MOOD_WEIGHTS.put("애니메이션", new int[]{2, 0, 1, 1, 1});
        GENRE_MOOD_WEIGHTS.put("모험", new int[]{2, 1, 0, 2, 1});
        GENRE_MOOD_WEIGHTS.put("액션", new int[]{1, 3, 0, 2, 0});
        GENRE_MOOD_WEIGHTS.put("스릴러", new int[]{0, 3, 0, 1, 2});
        GENRE_MOOD_WEIGHTS.put("공포", new int[]{0, 3, 0, 0, 2});
        GENRE_MOOD_WEIGHTS.put("범죄", new int[]{0, 3, 1, 1, 1});
        GENRE_MOOD_WEIGHTS.put("미스터리", new int[]{0, 2, 0, 1, 3});
        GENRE_MOOD_WEIGHTS.put("SF", new int[]{0, 1, 0, 1, 3});
        GENRE_MOOD_WEIGHTS.put("판타지", new int[]{1, 0, 0, 1, 3});
        GENRE_MOOD_WEIGHTS.put("드라마", new int[]{0, 0, 3, 2, 0});
        GENRE_MOOD_WEIGHTS.put("로맨스", new int[]{1, 0, 2, 2, 0});
        GENRE_MOOD_WEIGHTS.put("역사", new int[]{0, 1, 2, 3, 0});
        GENRE_MOOD_WEIGHTS.put("전쟁", new int[]{0, 2, 2, 3, 0});
        GENRE_MOOD_WEIGHTS.put("서부", new int[]{0, 2, 1, 2, 0});
        GENRE_MOOD_WEIGHTS.put("다큐멘터리", new int[]{0, 0, 1, 2, 1});
        GENRE_MOOD_WEIGHTS.put("TV 영화", new int[]{1, 1, 1, 1, 0});
    }

    static final String[] MOOD_AXES = {CHEERFUL, TENSE, SAD, CATHARSIS, MYSTIC};

    @Override
    public Map<String, Integer> tag(Movie movie) {
        int[] total = new int[MOOD_AXES.length];

        for (String genre : splitGenres(movie.getGenre())) {
            int[] weights = GENRE_MOOD_WEIGHTS.get(genre);
            if (weights == null) continue; // 매핑표에 없는 장르는 분위기 판단에 쓰지 않습니다
            for (int i = 0; i < total.length; i++) {
                total[i] += weights[i];
            }
        }

        return normalize(total);
    }

    /** "액션, SF, 모험" 형태의 문자열을 장르 목록으로 자른다 */
    static String[] splitGenres(String genre) {
        if (genre == null || genre.isBlank()) return new String[0];

        String[] parts = genre.split(",");
        for (int i = 0; i < parts.length; i++) {
            parts[i] = parts[i].trim();
        }
        return parts;
    }

    /** 가장 강한 분위기를 100 으로 두고 나머지를 상대 환산. PreferenceService 의 환산 방식과 같다 */
    private Map<String, Integer> normalize(int[] scores) {
        int top = 0;
        for (int score : scores) top = Math.max(top, score);

        Map<String, Integer> result = new LinkedHashMap<>();
        for (int i = 0; i < MOOD_AXES.length; i++) {
            result.put(MOOD_AXES[i], top == 0 ? 0 : (int) Math.round(scores[i] * 100.0 / top));
        }
        return result;
    }
}
