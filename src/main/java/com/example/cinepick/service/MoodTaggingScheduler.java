package com.example.cinepick.service;

import com.example.cinepick.domain.Movie;
import com.example.cinepick.repository.MovieRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 아직 AI 분위기 분석이 되지 않은 영화를 배치로 분석해 DB 에 저장한다.
 *
 * <b>화면 렌더링 경로에서는 LLM 을 호출하지 않는다.</b> 여기서 미리 채운 값을
 * {@link LlmMoodTagger} 가 읽으므로 사용자 요청은 항상 DB 조회 속도로 끝난다.
 * 성공하면 moodTaggedAt 이 찍혀 다시 대상이 되지 않고, 실패는 {@link #MAX_ATTEMPTS} 회에서
 * 포기한다 — 무한 재시도로 비용이 새지 않게 하는 장치다.
 */
@Service
@RequiredArgsConstructor
public class MoodTaggingScheduler {

    private final MovieRepository movieRepository;
    private final LlmApiService llmApiService;
    private final OpenApiService openApiService;

    /** 이 횟수만큼 실패하면 더 시도하지 않는다 */
    public static final int MAX_ATTEMPTS = 3;

    /** 호출 사이 간격(ms). 속도 제한에 걸리지 않도록 여유를 둔다 */
    private static final long CALL_INTERVAL_MS = 400;

    @Value("${api.claude.auto-tag:true}")
    private boolean autoTag;

    /** 한 회차에 처리할 편수 */
    @Value("${api.claude.batch-size:20}")
    private int batchSize;

    /** 기동 1분 후 첫 실행, 이후 6시간마다 */
    @Scheduled(initialDelay = 60_000, fixedRate = 6 * 60 * 60 * 1000)
    public void tagPendingMovies() {
        if (!autoTag) return;
        if (!llmApiService.isEnabled()) return; // 키가 없으면 조용히 건너뜁니다

        List<Movie> pending = movieRepository
                .findByMoodTaggedAtIsNullAndMoodTagAttemptsLessThan(MAX_ATTEMPTS);
        if (pending.isEmpty()) {
            reportAbandoned();
            return;
        }

        int limit = Math.min(batchSize, pending.size());
        System.out.println("[분위기 분석] 미분석 " + pending.size() + "편 중 " + limit + "편을 처리합니다.");

        int success = 0;
        int failed = 0;
        for (int i = 0; i < limit; i++) {
            if (tagOne(pending.get(i).getId())) success++;
            else failed++;

            try {
                Thread.sleep(CALL_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        int remaining = movieRepository
                .findByMoodTaggedAtIsNullAndMoodTagAttemptsLessThan(MAX_ATTEMPTS).size();
        System.out.println("[분위기 분석] 완료. 성공 " + success + "편 / 실패 " + failed + "편"
                + " (남은 미분석: " + remaining + "편)");

        reportAbandoned();
    }

    /**
     * 한 편을 분석해 저장한다. 트랜잭션을 편 단위로 끊어 한 편의 실패가 나머지에 번지지 않게 한다.
     * 실패하면 시도 횟수를 올려 저장하므로 같은 영화를 무한정 다시 부르지 않는다.
     */
    @Transactional
    public boolean tagOne(Long movieId) {
        Movie movie = movieRepository.findById(movieId).orElse(null);
        if (movie == null) return false;

        ensureOverview(movie);

        return llmApiService.analyzeMood(movie).map(scores -> {
            movie.setMoodCheerful(scores.cheerful());
            movie.setMoodTense(scores.tense());
            movie.setMoodSad(scores.sad());
            movie.setMoodCatharsis(scores.catharsis());
            movie.setMoodMystic(scores.mystic());
            movie.setMoodTaggedAt(LocalDateTime.now());
            movieRepository.save(movie);
            return true;
        }).orElseGet(() -> {
            int attempts = movie.getMoodTagAttempts() + 1;
            movie.setMoodTagAttempts(attempts);
            movieRepository.save(movie);

            if (attempts >= MAX_ATTEMPTS) {
                System.out.println("[분위기 분석] " + MAX_ATTEMPTS + "회 실패로 포기: " + movie.getTitle()
                        + " (" + movie.getGenre() + ")");
            }
            return false;
        });
    }

    /** 재시도 한도를 넘겨 포기한 영화를 모아 알린다 (원인 확인용) */
    private void reportAbandoned() {
        List<Movie> abandoned = movieRepository
                .findByMoodTaggedAtIsNullAndMoodTagAttemptsGreaterThanEqual(MAX_ATTEMPTS);
        if (abandoned.isEmpty()) return;

        System.out.println("[분위기 분석] 포기한 영화 " + abandoned.size() + "편"
                + " (장르 조합 기반 분석으로 대체됩니다):");
        abandoned.forEach(m -> System.out.println("   - " + m.getTitle() + " (" + m.getGenre() + ")"));
    }

    /** 줄거리가 비어 있으면 TMDB 에서 한 번 받아와 저장한다 (분석 정확도) */
    private void ensureOverview(Movie movie) {
        if (movie.getOverview() != null && !movie.getOverview().isBlank()) return;

        try {
            JsonNode detail = openApiService.getTmdbMovieDetail(movie.getMovieCd());
            String overview = detail.path("overview").asText();
            if (overview != null && !overview.isBlank()) {
                movie.setOverview(overview);
                movieRepository.save(movie);
            }
        } catch (Exception e) {
            // 줄거리는 있으면 좋은 정보일 뿐이다. 없으면 제목·장르만으로 분석한다
            System.out.println("[분위기 분석] 줄거리 조회 실패: " + movie.getTitle());
        }
    }
}
