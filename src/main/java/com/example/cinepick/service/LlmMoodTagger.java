package com.example.cinepick.service;

import com.example.cinepick.domain.Movie;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AI 가 분석해 DB 에 저장해둔 분위기 점수를 읽어온다.
 * 실시간 LLM 호출은 하지 않는다 — 분석은 {@link MoodTaggingScheduler} 가 배치로 미리 돌린다.
 * 미분석·실패 영화는 {@link GenreBasedMoodTagger} 로 대체된다.
 */
@Component
@Primary
@RequiredArgsConstructor
public class LlmMoodTagger implements MoodTagger {

    private final GenreBasedMoodTagger fallback;

    @Override
    public Map<String, Integer> tag(Movie movie) {
        if (!movie.hasMoodTags()) {
            return fallback.tag(movie);
        }

        Map<String, Integer> result = new LinkedHashMap<>();
        result.put(GenreBasedMoodTagger.CHEERFUL, movie.getMoodCheerful());
        result.put(GenreBasedMoodTagger.TENSE, movie.getMoodTense());
        result.put(GenreBasedMoodTagger.SAD, movie.getMoodSad());
        result.put(GenreBasedMoodTagger.CATHARSIS, movie.getMoodCatharsis());
        result.put(GenreBasedMoodTagger.MYSTIC, movie.getMoodMystic());
        return result;
    }
}
