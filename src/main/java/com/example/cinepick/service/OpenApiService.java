package com.example.cinepick.service;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class OpenApiService {

    @Value("${api.tmdb.key}")
    private String tmdbKey;

    /**
     * 타임아웃을 반드시 준다. 기본 {@code new RestTemplate()} 은 connect·read 모두 <b>무제한</b>이라,
     * TMDB 가 응답을 멈추면 톰캣 요청 스레드가 영원히 붙잡힌다. 메인 화면이 TMDB 검색을 요청 안에서
     * 부르므로 그대로 두면 한 번의 장애가 사이트 전체를 세운다.
     *
     * <b>{@code RestTemplateBuilder} 를 쓰면 컴파일이 깨진다</b> — Spring Boot 4 에서 그 클래스는
     * spring-boot-restclient 모듈로 빠졌고 이 프로젝트의 spring-boot-starter-webmvc 는 끌어오지 않는다.
     */
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);

    private final RestTemplate restTemplate = createRestTemplate();

    private static RestTemplate createRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT);
        factory.setReadTimeout(READ_TIMEOUT);
        return new RestTemplate(factory);
    }

    // 장르 ID → 이름 캐시
    private final Map<Integer, String> genreMap = new HashMap<>();

    /*
     * ---------------------------------------------------------------- 제목 검색 캐시
     *
     * 메인 화면은 크롤링 제목이 DB 제목과 안 맞을 때마다 요청 안에서 이 검색을 부른다. 그런데
     * 결과를 기억하지 않아 <b>같은 제목을 매 요청 다시 물어봤다</b> — 실측으로 상영예정작 탭이
     * 매 요청 45번의 TMDB 왕복(6.4초)을 반복했다. 저장되지 않는 경우가 둘 있어 영구히 반복된다:
     *   ① TMDB 에 없는 제목(라이브뷰잉·특별상영·재개봉 표기) — 저장할 것이 없다
     *   ② movieCd 로 기존 행을 재사용한 경우 — DB 제목은 그대로라 다음에도 매칭이 실패한다
     *
     * 그래서 <b>"못 찾았다"는 사실까지 기억</b>하는 것이 이 캐시의 존재 이유다.
     * ConcurrentHashMap 은 값에 null 을 못 넣으므로 엔트리로 한 겹 감싼다.
     *
     * 의존성을 늘리지 않은 이유: spring-boot-starter-cache 의 기본 구현(ConcurrentMapCacheManager)에는
     * TTL 이 없어 이번 요구의 핵심("실패를 기억하되 영원히는 아니게")을 못 채운다. Caffeine 을 더해도
     * @Cacheable 로는 성공과 실패에 서로 다른 수명을 줄 수 없다. 이 프로젝트에는 이미 비싼 것을
     * 메모리에 들고 있는 선례가 있다(MovieCrawlerScheduler.cachedResult, 아래 genreMap).
     */

    /** 성공: 제목→TMDB id 는 사실상 불변이라 길게 둔다. 잘못된 첫 매칭을 언젠가 바로잡을 여지만 남긴다 */
    private static final long HIT_TTL_MS = Duration.ofHours(24).toMillis();

    /**
     * 결과 0건: 기억해서 재호출을 막되 <b>영구는 안 된다</b> — 나중에 TMDB 에 등록될 수 있다.
     * 크롤링 주기와 같은 6시간이면 "상영표가 한 번 바뀌는 동안 한 번은 다시 물어본다"가 된다.
     */
    private static final long MISS_TTL_MS = Duration.ofHours(6).toMillis();

    /**
     * 호출 자체가 실패(타임아웃·TMDB 장애): <b>"없음"으로 6시간 기억하면 안 된다</b> —
     * 일시적 장애를 오답으로 굳히게 된다. 그렇다고 캐시를 안 하면 TMDB 가 죽었을 때
     * 매 요청이 (제목 수 × 5초)를 쌓는다. 짧게 기억해 둘 다 막는다.
     */
    private static final long ERROR_TTL_MS = Duration.ofMinutes(1).toMillis();

    /** 무한히 자라지 않는다는 보장. 크롤링 제목이 수백 개 규모라 실제로는 닿지 않는다 */
    private static final int MAX_CACHE_ENTRIES = 2000;

    /** {@code result == null} 이면 "TMDB 에 없더라"를 기억한 것 — 이 캐시의 존재 이유가 그 null 이다 */
    private record SearchEntry(JsonNode result, long expiresAt) {
        boolean isExpired(long now) {
            return now >= expiresAt;
        }
    }

    private final Map<String, SearchEntry> searchCache = new ConcurrentHashMap<>();

    /**
     * 실제로 나간 TMDB 검색 요청 수와 캐시로 막은 수.
     * 메인이 느릴 때 "요청 한 번에 외부 API 를 몇 번 불렀나"가 유일한 단서라 상시로 센다(비용이 없다).
     */
    private final AtomicLong tmdbSearchCalls = new AtomicLong();
    private final AtomicLong searchCacheHits = new AtomicLong();

    public long getTmdbSearchCalls() {
        return tmdbSearchCalls.get();
    }

    public long getSearchCacheHits() {
        return searchCacheHits.get();
    }

    // 서버 시작 시 TMDB 장르 목록을 한 번만 적재
    @PostConstruct
    public void initGenres() {
        String url = "https://api.themoviedb.org/3/genre/movie/list?api_key=" + tmdbKey + "&language=ko-KR";
        try {
            JsonNode response = restTemplate.getForObject(url, JsonNode.class);
            for (JsonNode genre : response.path("genres")) {
                genreMap.put(genre.path("id").asInt(), genre.path("name").asText());
            }
        } catch(Exception e) {
            System.out.println("장르 초기화 실패");
        }
    }

    // TMDB: 현재 상영중 목록 (DB 초기화용)
    public JsonNode getTmdbNowPlaying(int page) {
        String url = "https://api.themoviedb.org/3/movie/now_playing?api_key=" + tmdbKey + "&language=ko-KR&page=" + page;
        return restTemplate.getForObject(url, JsonNode.class);
    }

    /**
     * TMDB: 영화 상세 (출연진 포함). <b>없는 id 면 null</b>.
     *
     * <p>null 을 돌려주는 이유: TMDB 는 없는 id 에 HTTP 404 를 주고 RestTemplate 은 그걸
     * {@code HttpClientErrorException} 으로 던진다. 그대로 두면 그 예외가 컨트롤러까지 올라가
     * <b>"없는 영화"가 서버 오류(500)로 표시됐다.</b> 사용자 잘못(없는 주소)인데 서버 잘못으로
     * 보이고, 검색엔진도 없는 페이지를 살아 있는 것으로 취급한다.
     *
     * <p>여기서 잡는 것은 <b>404 뿐</b>이다. 타임아웃이나 TMDB 장애(5xx)는 실제로 서버 문제이므로
     * 그대로 올려보내 500 화면이 뜨게 둔다.
     */
    public JsonNode getTmdbMovieDetail(String movieId) {
        // append_to_response=credits 로 배우·감독까지 한 번에 가져온다
        String url = "https://api.themoviedb.org/3/movie/" + movieId + "?api_key=" + tmdbKey + "&language=ko-KR&append_to_response=credits";
        try {
            return restTemplate.getForObject(url, JsonNode.class);
        } catch (HttpClientErrorException.NotFound e) {
            return null;
        }
    }

    // 장르 ID 배열 → 한글 장르명 문자열
    public String getGenreNames(JsonNode genreIds) {
        if (!genreIds.isArray()) return "";
        List<String> names = new ArrayList<>();
        for (JsonNode idNode : genreIds) {
            names.add(genreMap.getOrDefault(idNode.asInt(), ""));
        }
        return String.join(", ", names);
    }

    /**
     * 제목으로 TMDB 를 검색해 가장 잘 맞는 한 편을 돌려준다. 못 찾으면 null.
     *
     * 결과는 성공·결과없음·호출실패를 <b>구분해서</b> 캐시한다(위 TTL 상수 참조).
     * 예전에는 셋을 전부 null 로 뭉개서, 캐시를 넣더라도 일시적 장애를 "없는 영화"로
     * 굳혀 버릴 수 있었다.
     */
    public JsonNode searchTmdbByTitle(String title) {
        if (title == null || title.isBlank()) return null;

        long now = System.currentTimeMillis();
        SearchEntry cached = searchCache.get(title);
        if (cached != null && !cached.isExpired(now)) {
            searchCacheHits.incrementAndGet();
            return cached.result();
        }

        // 제목에 & # + 같은 글자가 들어가도 쿼리가 깨지지 않도록 인코딩해서 붙인다
        String url = UriComponentsBuilder
                .fromUriString("https://api.themoviedb.org/3/search/movie")
                .queryParam("api_key", tmdbKey)
                .queryParam("language", "ko-KR")
                .queryParam("query", title)
                .toUriString();

        tmdbSearchCalls.incrementAndGet();
        try {
            JsonNode response = restTemplate.getForObject(url, JsonNode.class);
            JsonNode results = (response == null) ? null : response.path("results");

            JsonNode best = (results != null && results.isArray() && results.size() > 0)
                    ? results.get(0)   // 검색된 가장 정확한 첫 번째 영화
                    : null;

            // best 가 null 이면 "TMDB 에 없는 제목"이라는 사실을 기억한다 (재호출 방지)
            putInCache(title, best, now + (best != null ? HIT_TTL_MS : MISS_TTL_MS));
            return best;

        } catch (Exception e) {
            System.out.println("TMDB 검색 실패: " + title + " (" + e.getMessage() + ")");
            // 장애는 짧게만 기억한다 — 길게 두면 일시적 실패가 '없는 영화'로 굳는다
            putInCache(title, null, now + ERROR_TTL_MS);
            return null;
        }
    }

    private void putInCache(String title, JsonNode result, long expiresAt) {
        if (searchCache.size() >= MAX_CACHE_ENTRIES) {
            // LRU 를 직접 짤 값어치는 없다. 상한에 닿으면 비우고 다시 채운다
            searchCache.clear();
        }
        searchCache.put(title, new SearchEntry(result, expiresAt));
    }
}
