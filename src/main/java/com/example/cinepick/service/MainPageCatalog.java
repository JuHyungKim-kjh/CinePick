package com.example.cinepick.service;

import com.example.cinepick.domain.Movie;
import com.example.cinepick.dto.response.MovieListResDto;
import com.example.cinepick.repository.MovieRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 메인 화면에 올릴 목록을 <b>요청 밖에서 미리 만들어 두는</b> 곳.
 *
 * <p><b>왜 필요한가</b> — 예전에는 {@code MovieService.getMoviesForMainPage(tab)} 가 요청 스레드
 * 안에서 크롤링 제목을 DB 와 맞추고, 못 맞춘 제목마다 TMDB 를 동기로 불렀다. 실측으로 상영예정작
 * 탭이 <b>매 요청 45번의 TMDB 왕복(6.4초)</b>을 반복했다. 검색 결과 캐시를 넣어 두 번째 요청부터는
 * 없앴지만, 그건 완화이지 해결이 아니었다 — 서버를 새로 켤 때마다 누군가 한 명은 10초를 냈다.
 *
 * <p>지금은 크롤링이 끝난 직후 백그라운드에서 여기까지 전부 끝내고, 요청은 완성된 목록을
 * 읽기만 한다. 요청 경로에서 <b>TMDB 호출과 DB 쓰기가 통째로 사라진다.</b>
 *
 * <p><b>크롤링 스케줄러를 주입받지 않는다</b> — {@link MovieCrawlerScheduler} 가 이쪽을 부르므로
 * 서로 참조하면 순환 의존이 된다. 그래서 크롤링 결과를 인자로 받는다.
 */
@Service
@RequiredArgsConstructor
public class MainPageCatalog {

    private final MovieRepository movieRepository;
    private final OpenApiService openApiService;
    private final MovieCrawlerService movieCrawlerService;
    private final SiteLinkUpdater siteLinkUpdater;

    /**
     * 화면에 그대로 내보낼 수 있는 완성품. 갱신은 통째로 교체하므로 읽는 쪽은 락이 필요 없다
     * ({@code MovieCrawlerScheduler.cachedResult} 와 같은 방식).
     */
    public record Prepared(List<MovieListResDto> nowShowing,
                           List<MovieListResDto> upcoming,
                           Set<String> nowShowingMovieCds) {

        static Prepared empty() {
            return new Prepared(List.of(), List.of(), Set.of());
        }

        /** 탭 이름으로 목록을 고른다. 모르는 값은 현재상영작으로 (기존 동작과 같다) */
        public List<MovieListResDto> byTab(String tab) {
            return "upcoming".equals(tab) ? upcoming : nowShowing;
        }
    }

    private volatile Prepared prepared = Prepared.empty();

    public Prepared get() {
        return prepared;
    }

    /**
     * 크롤링 결과로 메인 목록을 다시 만든다. <b>요청 스레드에서 부르지 말 것</b> —
     * {@code useTmdb} 가 true 면 외부 API 를 수십 번 부른다.
     *
     * @param useTmdb DB 에서 못 찾은 제목을 TMDB 에 물어볼지 여부.
     *                <p>스냅샷 복원 직후에는 <b>false</b> 로 부른다. 복원의 목적이 "크롤링을
     *                기다리지 않고 문을 여는 것"인데 여기서 TMDB 를 기다리면 그 목적이 사라진다.
     *                이때 DB 에 없는 영화는 잠시 빠지지만, 뒤이어 도는 크롤링이 true 로 다시
     *                채우고 그 뒤로는 DB 에 남아 있으므로 다음 기동부터는 처음부터 완전하다.
     */
    public void refresh(MovieCrawlerService.CrawlResult crawlResult, boolean useTmdb) {
        if (crawlResult == null) return;

        long startedAt = System.currentTimeMillis();
        long tmdbBefore = openApiService.getTmdbSearchCalls();

        /*
         * 제목 → 영화 인덱스를 한 번만 만든다. 예전에는 크롤링 제목마다 DB 영화 전체를 훑어
         * O(N×M) 이었고 비교마다 cleanTitle 이 정규식을 새로 컴파일했다.
         *
         * 값이 List 인 것이 중요하다. 같은 cleanTitle 을 가진 행이 둘 이상일 수 있는데,
         * 목록 매칭은 첫 번째를 써야 예전 동작과 같고(Map 하나면 마지막 값이 이긴다),
         * 상영작 집합은 전부를 모아야 추천 후보에서 영화가 조용히 빠지지 않는다.
         */
        Map<String, List<Movie>> byCleanTitle = new HashMap<>();
        for (Movie dbMovie : movieRepository.findAll()) {
            byCleanTitle.computeIfAbsent(movieCrawlerService.cleanTitle(dbMovie.getTitle()),
                    k -> new ArrayList<>()).add(dbMovie);
        }

        // 현재상영작을 먼저 돈다 — 사이트별 예매 링크 갱신이 이쪽에만 걸려 있다
        List<MovieListResDto> nowShowing = resolve(crawlResult.nowShowing, byCleanTitle, crawlResult, useTmdb, true);
        List<MovieListResDto> upcoming = resolve(crawlResult.upcoming, byCleanTitle, crawlResult, useTmdb, false);

        this.prepared = new Prepared(
                List.copyOf(nowShowing),
                List.copyOf(upcoming),
                Set.copyOf(collectNowShowingMovieCds(crawlResult, byCleanTitle)));

        System.out.println("[메인 목록 준비] 완료 — 현재상영작 " + nowShowing.size()
                + "편, 상영예정작 " + upcoming.size() + "편"
                + ", TMDB 검색 " + (openApiService.getTmdbSearchCalls() - tmdbBefore) + "회"
                + ", " + (System.currentTimeMillis() - startedAt) + "ms"
                + (useTmdb ? "" : " (DB 만 사용)"));
    }

    /**
     * 크롤링 제목 목록을 화면용 DTO 로 바꾼다.
     *
     * @param updateLinks 사이트별 예매 링크를 갱신할지. 현재상영작에만 링크 정보가 있다
     */
    private List<MovieListResDto> resolve(List<String> crawledTitles,
                                          Map<String, List<Movie>> byCleanTitle,
                                          MovieCrawlerService.CrawlResult crawlResult,
                                          boolean useTmdb,
                                          boolean updateLinks) {
        List<MovieListResDto> resultList = new ArrayList<>();
        // 예매사마다 표기가 달라 크롤링 제목이 둘로 잡혀도 같은 영화라면 화면에는 한 번만 보여준다
        Set<String> shownMovieCds = new HashSet<>();

        for (String title : crawledTitles) {
            String cleanedCrawled = movieCrawlerService.cleanTitle(title);

            List<Movie> candidates = byCleanTitle.get(cleanedCrawled);
            Movie matchedMovie = (candidates == null || candidates.isEmpty()) ? null : candidates.get(0);

            if (matchedMovie == null && useTmdb) {
                matchedMovie = resolveFromTmdb(title, cleanedCrawled, byCleanTitle);
            }
            if (matchedMovie == null) continue;

            /*
             * 사이트별 예매 링크 갱신은 아래 중복 제거보다 <b>먼저</b> 해야 한다 —
             * 표기가 다른 두 제목은 서로 다른 예매사에서 온 것이라, 뒤쪽을 그냥 건너뛰면
             * 그 사이트의 예매 링크를 통째로 잃는다.
             */
            if (updateLinks) {
                siteLinkUpdater.apply(matchedMovie, crawlResult.nowShowingLinks.get(cleanedCrawled));
            }

            if (!shownMovieCds.add(matchedMovie.getMovieCd())) continue;

            resultList.add(MovieListResDto.builder()
                    .movieCd(matchedMovie.getMovieCd())
                    .title(matchedMovie.getTitle())
                    .openDate(matchedMovie.getOpenDate())
                    .genre(matchedMovie.getGenre())
                    .director(matchedMovie.getDirector())
                    .posterUrl(matchedMovie.getPosterUrl())
                    .build());
        }
        return resultList;
    }

    /** DB 에서 못 찾은 제목을 TMDB 에 물어 새 영화를 만들거나 기존 행을 찾아 준다. */
    private Movie resolveFromTmdb(String title, String cleanedCrawled, Map<String, List<Movie>> byCleanTitle) {
        JsonNode tmdbData = openApiService.searchTmdbByTitle(title);
        if (tmdbData == null) return null;

        String movieCd = tmdbData.path("id").asText();

        /*
         * 제목이 달라 인덱스가 놓쳤을 뿐, 같은 영화가 이미 DB 에 있을 수 있다.
         * 라이브뷰잉·재개봉 제목이 대표적인데, cleanTitle() 로는 묶이지 않지만
         * TMDB 에서는 같은 id 로 모인다.
         *
         * 이 확인 없이 save() 하면 movie_cd 유니크 제약에 걸리고, 그 예외가 위로 올라가
         * 메인 화면 전체가 500 이 된다. (실제로 그렇게 터졌다)
         */
        Movie movie = movieRepository.findByMovieCd(movieCd).orElse(null);

        if (movie == null) {
            movie = new Movie();
            movie.setMovieCd(movieCd);
            movie.setTitle(title);
            movie.setTitleEn(tmdbData.path("original_title").asText());
            movie.setOpenDate(tmdbData.path("release_date").asText());
            movie.setGenre(openApiService.getGenreNames(tmdbData.path("genre_ids")));
            movie.setDirector("상세페이지 참조");

            String posterPath = tmdbData.path("poster_path").asText();
            movie.setPosterUrl((posterPath != null && !posterPath.equals("null") && !posterPath.isEmpty())
                    ? "https://image.tmdb.org/t/p/w500" + posterPath
                    : "/images/Logo.png");

            movieRepository.save(movie);
        }

        /*
         * 알아낸 영화를 인덱스에 넣어 두면 뒤에 나올 같은 제목이 TMDB 를 다시 부르지 않는다.
         * 키는 <b>DB 제목이 아니라 크롤링 제목</b>이다 — 찾은 엔티티의 DB 제목은 크롤링 표기와
         * 다른 채로 남아 있어, 그것으로 넣으면 다음 순회에서도 매칭되지 않는다.
         */
        byCleanTitle.computeIfAbsent(cleanedCrawled, k -> new ArrayList<>()).add(movie);
        return movie;
    }

    /**
     * [추천 후보 거르개] 지금 상영 중인 영화의 movieCd 집합.
     *
     * 한 cleanTitle 에 여러 행이 걸릴 수 있으므로 <b>전부</b> 모은다. 하나만 취하면 그 영화가
     * 추천의 '지금 상영 중' 탭에서 조용히 사라진다.
     */
    private Set<String> collectNowShowingMovieCds(MovieCrawlerService.CrawlResult crawlResult,
                                                  Map<String, List<Movie>> byCleanTitle) {
        Set<String> movieCds = new HashSet<>();
        for (String title : crawlResult.nowShowing) {
            List<Movie> matched = byCleanTitle.get(movieCrawlerService.cleanTitle(title));
            if (matched == null) continue;

            for (Movie movie : matched) {
                movieCds.add(movie.getMovieCd());
            }
        }
        return movieCds;
    }

}
