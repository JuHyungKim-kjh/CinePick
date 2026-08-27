package com.example.cinepick.service;

import com.example.cinepick.domain.Movie;
import com.example.cinepick.dto.response.MovieListResDto;
import com.example.cinepick.repository.MovieRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CGV 무비차트 · 롯데시네마 TOP · 메가박스 박스오피스의 순위를 합쳐 인기 순위를 만든다.
 * 취향 정보가 없는 방문자에게 개인화 추천 대신 보여줄 목록이다.
 */
@Service
@RequiredArgsConstructor
public class BoxOfficeService {

    private final MovieCrawlerScheduler movieCrawlerScheduler;
    private final MovieCrawlerService movieCrawlerService;
    private final MovieRepository movieRepository;

    /** 각 사이트에서 순위 집계에 반영할 상위 편수 */
    private static final int RANK_DEPTH = 20;

    /**
     * 3사 순위를 보르다 점수로 합산한다. 1위에 RANK_DEPTH 점, 2위에 RANK_DEPTH-1 점…
     * 한 곳에서만 반짝 오른 영화보다 세 곳 모두 상위인 영화가 위로 올라온다.
     */
    @Transactional(readOnly = true)
    public List<MovieListResDto> getBoxOffice(int limit) {
        Map<String, List<String>> rankedByBrand = movieCrawlerScheduler.getCachedResult().rankedByBrand;
        if (rankedByBrand == null || rankedByBrand.isEmpty()) return List.of();

        // cleanTitle -> 집계 결과
        Map<String, Aggregate> aggregates = new LinkedHashMap<>();

        for (List<String> ranked : rankedByBrand.values()) {
            if (ranked == null) continue;

            int depth = Math.min(RANK_DEPTH, ranked.size());
            for (int i = 0; i < depth; i++) {
                String title = ranked.get(i);
                String key = movieCrawlerService.cleanTitle(title);
                if (key.isEmpty()) continue;

                Aggregate agg = aggregates.computeIfAbsent(key, k -> new Aggregate(title));
                agg.score += (RANK_DEPTH - i); // 상위일수록 높은 점수
                agg.brandCount++;
            }
        }
        if (aggregates.isEmpty()) return List.of();

        List<Aggregate> sorted = new ArrayList<>(aggregates.values());
        sorted.sort(Comparator
                .comparingInt((Aggregate a) -> a.score).reversed()
                // 동점이면 더 많은 사이트에 오른 쪽을 위로 (한 곳 편중보다 고른 인기 우선)
                .thenComparing(Comparator.comparingInt((Aggregate a) -> a.brandCount).reversed()));

        // 크롤링 제목을 DB 의 영화와 맞춰 포스터·장르를 채운다
        List<Movie> allMovies = movieRepository.findAll();
        List<MovieListResDto> result = new ArrayList<>();

        for (Aggregate agg : sorted) {
            if (result.size() >= limit) break;

            Movie matched = findByCleanTitle(allMovies, agg.cleanKey());
            if (matched == null) continue; // DB에 없는 영화는 포스터가 없어 카드로 만들 수 없습니다

            result.add(MovieListResDto.builder()
                    .movieCd(matched.getMovieCd())
                    .title(matched.getTitle())
                    .openDate(matched.getOpenDate())
                    .genre(matched.getGenre())
                    .director(matched.getDirector())
                    .posterUrl(matched.getPosterUrl())
                    .build());
        }
        return result;
    }

    private Movie findByCleanTitle(List<Movie> movies, String cleanKey) {
        for (Movie movie : movies) {
            if (cleanKey.equals(movieCrawlerService.cleanTitle(movie.getTitle()))) {
                return movie;
            }
        }
        return null;
    }

    /** 한 영화의 3사 합산 결과를 담는 그릇 */
    private class Aggregate {
        final String rawTitle;
        int score;
        int brandCount;

        Aggregate(String rawTitle) {
            this.rawTitle = rawTitle;
        }

        String cleanKey() {
            return movieCrawlerService.cleanTitle(rawTitle);
        }
    }
}
