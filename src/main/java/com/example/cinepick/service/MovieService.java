package com.example.cinepick.service;

import com.example.cinepick.domain.Movie;
import com.example.cinepick.dto.response.MovieDetailResDto;
import com.example.cinepick.dto.response.MovieListResDto;
import com.example.cinepick.repository.MovieRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
public class MovieService {

    private final OpenApiService openApiService;
    private final MovieRepository movieRepository;
    private final MovieCrawlerScheduler movieCrawlerScheduler;
    private final MovieCrawlerService movieCrawlerService;
    private final SiteLinkUpdater siteLinkUpdater;

    /**
     * 1. [데이터 초기화] DB 가 비어 있을 때만 TMDB 에서 기본 목록을 내려받는다.
     * 예전에는 @PostConstruct 였는데, 그러면 빈 생성 단계에서 외부 API 를 5번 호출하느라
     * Tomcat 이 포트를 열기도 전에 서버가 멈춰 있었다. {@link StartupWarmupService} 가
     * 서버가 뜬 뒤에 호출한다.
     */
    public void seedIfEmpty() {
        if (movieRepository.count() == 0) {
            System.out.println("TMDB API에서 초기 영화 데이터를 불러옵니다...");

            for (int i = 1; i <= 5; i++) {
                JsonNode response = openApiService.getTmdbNowPlaying(i);
                JsonNode list = response.path("results");

                if (list.isArray()) {
                    for (JsonNode node : list) {
                        String movieId = node.path("id").asText();
                        if (movieRepository.findByMovieCd(movieId).isPresent()) continue;

                        Movie movie = new Movie();
                        movie.setMovieCd(movieId);
                        movie.setTitle(node.path("title").asText());
                        movie.setTitleEn(node.path("original_title").asText());
                        movie.setOpenDate(node.path("release_date").asText());
                        movie.setGenre(openApiService.getGenreNames(node.path("genre_ids")));
                        movie.setDirector("상세페이지 참조");

                        String posterPath = node.path("poster_path").asText();
                        movie.setPosterUrl((posterPath != null && !posterPath.equals("null") && !posterPath.isEmpty())
                                ? "https://image.tmdb.org/t/p/w500" + posterPath
                                : "/images/Logo.png");

                        movieRepository.save(movie);
                    }
                }
            }
            System.out.println("TMDB 초기 데이터 세팅 완료!");
        }
    }

    // 2. [전체 DB 정렬]
    public Map<String, Object> getCombinedMovieList(String keyword, int page, String sort) {
        Sort dbSort = "alpha".equals(sort)
                ? Sort.by(Sort.Direction.ASC, "title")
                : Sort.by(Sort.Direction.DESC, "openDate");

        Pageable pageable = PageRequest.of(page - 1, 10, dbSort);

        Page<Movie> moviePage;
        if (keyword != null && !keyword.trim().isEmpty()) {
            moviePage = movieRepository.findByTitleContaining(keyword, pageable);
        } else {
            moviePage = movieRepository.findAll(pageable);
        }

        List<MovieListResDto> dtoList = moviePage.getContent().stream().map(m ->
                MovieListResDto.builder()
                        .movieCd(m.getMovieCd())
                        .title(m.getTitle())
                        .openDate(m.getOpenDate())
                        .genre(m.getGenre())
                        .director(m.getDirector())
                        .posterUrl(m.getPosterUrl())
                        .build()
        ).collect(Collectors.toList());

        Map<String, Object> result = new HashMap<>();
        result.put("movies", dtoList);
        result.put("totalCount", (int) moviePage.getTotalElements());
        return result;
    }

    // 3. [상세페이지] TMDB 상세 정보 + DB 의 상영 상태·사이트 링크 결합
    public MovieDetailResDto getMovieDetail(String movieId) {
        JsonNode movieInfo = openApiService.getTmdbMovieDetail(movieId);

        if (movieInfo == null || movieInfo.isMissingNode() || movieInfo.has("status_code")) {
            throw new IllegalArgumentException("영화 정보를 찾을 수 없습니다.");
        }

        List<String> genres = new ArrayList<>();
        for (JsonNode genreNode : movieInfo.path("genres")) {
            genres.add(genreNode.path("name").asText());
        }

        String nation = "정보 없음";
        JsonNode countries = movieInfo.path("production_countries");
        if (countries.isArray() && countries.size() > 0) {
            nation = countries.get(0).path("name").asText();
        }

        String releaseDate = movieInfo.path("release_date").asText();
        String prdtYear = (releaseDate.length() >= 4) ? releaseDate.substring(0, 4) : "";

        String posterPath = movieInfo.path("poster_path").asText();
        String posterUrl = (posterPath != null && !posterPath.equals("null") && !posterPath.isEmpty())
                ? "https://image.tmdb.org/t/p/w500" + posterPath
                : "/images/Logo.png";

        JsonNode credits = movieInfo.path("credits");
        String director = "정보 없음";
        for (JsonNode crew : credits.path("crew")) {
            if ("Director".equals(crew.path("job").asText())) {
                director = crew.path("name").asText();
                break;
            }
        }

        List<String> actorList = new ArrayList<>();
        JsonNode cast = credits.path("cast");
        if (cast.isArray()) {
            for (int i = 0; i < Math.min(cast.size(), 3); i++) {
                actorList.add(cast.get(i).path("name").asText());
            }
        }

        // DB 에 저장된 영화 정보 (크롤링으로 모은 사이트별 링크 + 상영 상태 판단용)
        Movie dbMovie = movieRepository.findByMovieCd(movieId).orElse(null);
        String movieStatus = determineMovieStatus(dbMovie);

        String dDayLabel = null;
        String upcomingDateLabel = null;

        if ("UPCOMING".equals(movieStatus) && dbMovie != null) {
            String cleanedTitle = movieCrawlerService.cleanTitle(dbMovie.getTitle());
            List<String> rawInfoList = movieCrawlerScheduler.getCachedResult().upcomingReleaseInfo.get(cleanedTitle);

            if (rawInfoList != null && !rawInfoList.isEmpty()) {
                LocalDate earliestDate = null;
                String earliestRaw = null;

                // 여러 사이트의 개봉일 중 가장 빠른 날짜를 채택
                // (그 날짜가 지나면 해당 사이트가 현재상영작으로 넘어가며 예매 버튼이 뜨는 구조)
                for (String rawInfo : rawInfoList) {
                    try {
                        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d{4})\\.(\\d{2})\\.(\\d{2})").matcher(rawInfo);
                        if (m.find()) {
                            LocalDate parsed = LocalDate.of(
                                    Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)));
                            if (earliestDate == null || parsed.isBefore(earliestDate)) {
                                earliestDate = parsed;
                                earliestRaw = rawInfo;
                            }
                        }
                    } catch (Exception e) {
                        System.out.println("개봉일 파싱 실패: " + rawInfo + " (" + e.getMessage() + ")");
                    }
                }

                if (earliestDate != null) {
                    long days = ChronoUnit.DAYS.between(LocalDate.now(), earliestDate);
                    dDayLabel = (days <= 0) ? "D-Day" : "D-" + days;
                    String suffix = earliestRaw.contains("재개봉") ? "재개봉" : "개봉";
                    upcomingDateLabel = earliestDate.getYear() + "년 " + earliestDate.getMonthValue()
                            + "월 " + earliestDate.getDayOfMonth() + "일 " + suffix + " 예정";
                }
            }
        }

        if (dbMovie != null) {
            String cleanedTitle = movieCrawlerService.cleanTitle(dbMovie.getTitle());
            siteLinkUpdater.apply(dbMovie, movieCrawlerScheduler.getCachedResult().nowShowingLinks.get(cleanedTitle));
        }

        return MovieDetailResDto.builder()
                .movieCd(movieInfo.path("id").asText())
                .title(movieInfo.path("title").asText())
                .titleEn(movieInfo.path("original_title").asText())
                .openDate(releaseDate)
                .genre(String.join(", ", genres))
                .prdtYear(prdtYear)
                .nationAlt(nation)
                .director(director)
                .showTm(movieInfo.path("runtime").asText() + "분")
                .actors(String.join(", ", actorList))
                .posterUrl(posterUrl)
                .plot(movieInfo.path("overview").asText())
                .movieStatus(movieStatus)
                .cgvUrl(dbMovie != null ? dbMovie.getCgvUrl() : null)
                .megaboxUrl(dbMovie != null ? dbMovie.getMegaboxUrl() : null)
                .lotteUrl(dbMovie != null ? dbMovie.getLotteUrl() : null)
                .dDayLabel(dDayLabel)
                .upcomingDateLabel(upcomingDateLabel)
                .build();
    }

    // 3-1. 캐시된 크롤링 결과로 현재상영작 / 상영예정작 / 그 외를 판단
    //
    // 주의: 크롤링 전에 부르면 아무것도 일치하지 않아 전부 ENDED 가 된다(상영 중인 영화가
    // 종영으로 뜨고 예매 버튼이 사라짐). 이 메서드를 쓰는 화면은 WebConfig 의 시작 게이트
    // 대상이어야 한다 — 새 화면에서 부르게 되면 그 경로도 함께 등록할 것
    private String determineMovieStatus(Movie dbMovie) {
        if (dbMovie == null) return "ENDED"; // DB에 없으면 상영 정보를 알 수 없으니 '그 외'로 처리

        MovieCrawlerService.CrawlResult crawlResult = movieCrawlerScheduler.getCachedResult();
        String cleanedTitle = movieCrawlerService.cleanTitle(dbMovie.getTitle());

        boolean isNowShowing = crawlResult.nowShowing.stream()
                .anyMatch(t -> movieCrawlerService.cleanTitle(t).equals(cleanedTitle));
        if (isNowShowing) return "NOW";

        boolean isUpcoming = crawlResult.upcoming.stream()
                .anyMatch(t -> movieCrawlerService.cleanTitle(t).equals(cleanedTitle));
        if (isUpcoming) return "UPCOMING";

        return "ENDED";
    }
}
