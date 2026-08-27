package com.example.cinepick.service;

import com.example.cinepick.controller.SurveyController;
import com.example.cinepick.domain.Member;
import com.example.cinepick.domain.Movie;
import com.example.cinepick.domain.PreferenceSurvey;
import com.example.cinepick.dto.response.AiRecommendResDto;
import com.example.cinepick.repository.MovieRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 사용자 취향 점수와 영화 데이터를 대조해 추천 목록을 만든다.
 * 추천 근거는 오직 그 사용자의 취향과 이용 성향이다 —
 * 제작 국가·규모·개봉 상태처럼 "어떤 영화인가"로 거르는 축은 두지 않는다.
 */
@Service
@RequiredArgsConstructor
public class RecommendService {

    private final MovieRepository movieRepository;
    private final PreferenceService preferenceService;
    private final MoodTagger moodTagger;

    /** 분위기가 이 값 이상이면 그 영화의 "두드러지는 분위기"로 본다 (기준은 MoodTagger) */
    private static final int MOOD_PRESENCE_THRESHOLD = MoodTagger.PRESENCE_THRESHOLD;

    /**
     * 메인페이지 큐레이션 목록. 설문 전 회원이면 빈 목록을 돌려준다
     * (가짜 추천을 만들지 않고 화면에서 설문 유도 문구를 띄운다).
     */
    @Transactional(readOnly = true)
    public List<AiRecommendResDto> curateForMain(Member member, int limit) {
        Optional<PreferenceSurvey> surveyOpt = preferenceService.findSurvey(member);
        if (surveyOpt.isEmpty()) return List.of();
        PreferenceSurvey survey = surveyOpt.get();

        Map<String, Integer> genrePref = preferenceService.genreScores(member, SurveyController.GENRE_OPTIONS);
        Map<String, Integer> moodPref = preferenceService.moodScores(member, SurveyController.MOOD_OPTIONS);
        if (genrePref.isEmpty() && moodPref.isEmpty()) return List.of();

        // 분위기 슬라이더가 높을수록 분위기 적합도의 비중이 커진다 (0.5 ~ 1.5)
        double moodWeight = 0.5 + survey.getMoodIntensity() / 100.0;

        List<Scored> scored = new ArrayList<>();
        for (Movie movie : movieRepository.findAll()) {
            Scored s = score(movie, genrePref, moodPref, moodWeight);
            // 취향과 접점이 없는 영화(0점)와, 기피 쪽이 더 커서 합계가 음수인 영화를 함께 걸러낸다
            if (s.total > 0) scored.add(s);
        }
        if (scored.isEmpty()) return List.of();

        scored.sort(Comparator.comparingDouble((Scored s) -> s.total).reversed());

        List<Scored> picked = pick(scored, limit, survey.getGenreDiversity());
        double topScore = scored.get(0).total;

        List<AiRecommendResDto> result = new ArrayList<>();
        for (Scored s : picked) {
            result.add(toDto(s, topScore));
        }
        return result;
    }

    // ---------------------------------------------------------------- 점수 산출

    private Scored score(Movie movie, Map<String, Integer> genrePref, Map<String, Integer> moodPref, double moodWeight) {
        Scored s = new Scored();
        s.movie = movie;

        // 장르 적합도: 영화 장르를 설문 장르로 묶은 뒤(모험→액션, 범죄→스릴러 등)
        // 거기 걸린 사용자 선호 점수를 더한다.
        //
        // 선호 점수는 음수일 수 있고(낮은 평점이 쌓인 장르) 그대로 감점으로 쓴다.
        // 다만 matchedGenres 에는 넣지 않는다 — 이 집합은 대표 장르(leadGenre) 선정용이라
        // 기피 장르가 대표가 되면 안 된다
        for (String genre : GenreBasedMoodTagger.splitGenres(movie.getGenre())) {
            String canonical = PreferenceService.canonicalGenre(genre);
            Integer pref = genrePref.get(canonical);
            if (pref == null || pref == 0) continue;
            if (!s.scoredGenres.add(canonical)) continue; // 「액션, 모험」처럼 같은 장르로 묶인 값의 중복 가산 방지

            s.genreFit += pref;
            if (pref > 0) s.matchedGenres.add(canonical);
        }

        // 분위기 적합도: 영화의 분위기 강도(0~100)와 사용자 선호 점수를 곱해 더한다
        Map<String, Integer> movieMoods = moodTagger.tag(movie);
        movieMoods.forEach((mood, strength) -> {
            Integer pref = moodPref.get(mood);
            if (pref == null || pref == 0 || strength <= 0) return;

            s.moodFit += pref * strength / 100.0;
            if (pref > 0 && strength >= MOOD_PRESENCE_THRESHOLD) {
                s.matchedMoods.add(mood);
            }
        });

        s.total = s.genreFit + s.moodFit * moodWeight;
        return s;
    }

    /**
     * 상위 후보에서 실제로 보여줄 목록을 고른다.
     * 장르 다양성 슬라이더가 낮으면 점수 순서를 그대로 따르고, 높으면 대표 장르가 겹치지 않는
     * 작품을 먼저 채워 한 장르로 쏠리는 것을 막는다.
     */
    private List<Scored> pick(List<Scored> scored, int limit, int genreDiversity) {
        if (genreDiversity < 60) {
            return scored.subList(0, Math.min(limit, scored.size()));
        }

        List<Scored> picked = new ArrayList<>();
        Set<String> usedGenres = new LinkedHashSet<>();

        // 1차: 아직 안 쓴 대표 장르를 가진 작품 우선
        for (Scored s : scored) {
            if (picked.size() >= limit) break;
            String lead = s.leadGenre();
            if (lead != null && usedGenres.add(lead)) {
                picked.add(s);
            }
        }
        // 2차: 자리가 남으면 점수 순으로 채움
        for (Scored s : scored) {
            if (picked.size() >= limit) break;
            if (!picked.contains(s)) picked.add(s);
        }
        return picked;
    }

    private AiRecommendResDto toDto(Scored s, double topScore) {
        return AiRecommendResDto.builder()
                .movieCd(s.movie.getMovieCd())
                .title(s.movie.getTitle())
                .posterUrl(s.movie.getPosterUrl())
                .genre(s.movie.getGenre())
                .openDate(s.movie.getOpenDate())
                .matchPercent(topScore <= 0 ? 0 : (int) Math.round(s.total * 100.0 / topScore))
                .build();
    }

    /** 한 영화의 채점 결과를 잠시 들고 있는 그릇 */
    private static class Scored {
        Movie movie;
        double genreFit;
        double moodFit;
        double total;
    /** 이미 점수를 매긴 장르(중복 가산 방지). 감점된 기피 장르도 포함 */
        final Set<String> scoredGenres = new LinkedHashSet<>();
    /** 그중 취향에 <b>맞은</b> 장르만. 대표 장르를 고르는 데 쓴다 */
        final Set<String> matchedGenres = new LinkedHashSet<>();
        final Set<String> matchedMoods = new LinkedHashSet<>();

    /** 장르 쏠림 판단 기준이 되는 대표 장르 (취향과 맞은 장르 우선, 없으면 첫 번째) */
        String leadGenre() {
            if (!matchedGenres.isEmpty()) return matchedGenres.iterator().next();
            String[] all = GenreBasedMoodTagger.splitGenres(movie.getGenre());
            return all.length == 0 ? null : all[0];
        }
    }

    /** 취향 프로필 카드용 상위 항목 추출 (점수가 0인 항목은 제외) */
    public static List<String> topLabels(Map<String, Integer> scores, int count) {
        Map<String, Integer> sorted = new LinkedHashMap<>(scores);
        return sorted.entrySet().stream()
                .filter(e -> e.getValue() > 0)
                .sorted((a, b) -> b.getValue() - a.getValue())
                .limit(count)
                .map(Map.Entry::getKey)
                .toList();
    }
}
