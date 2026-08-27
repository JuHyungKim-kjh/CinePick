package com.example.cinepick.service;

import com.example.cinepick.dto.response.MovieListResDto;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class OpenApiService {

    @Value("${api.tmdb.key}")
    private String tmdbKey;

    private final RestTemplate restTemplate = new RestTemplate();

    // 장르 ID → 이름 캐시
    private final Map<Integer, String> genreMap = new HashMap<>();

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

    // TMDB: 영화 상세 (출연진 포함)
    public JsonNode getTmdbMovieDetail(String movieId) {
        // append_to_response=credits 로 배우·감독까지 한 번에 가져온다
        String url = "https://api.themoviedb.org/3/movie/" + movieId + "?api_key=" + tmdbKey + "&language=ko-KR&append_to_response=credits";
        return restTemplate.getForObject(url, JsonNode.class);
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
    public JsonNode searchTmdbByTitle(String title) {
        String url = "https://api.themoviedb.org/3/search/movie?api_key=" + tmdbKey + "&language=ko-KR&query=" + title;
        try {
            JsonNode response = restTemplate.getForObject(url, JsonNode.class);
            JsonNode results = response.path("results");
            if (results.isArray() && results.size() > 0) {
                return results.get(0); // 검색된 가장 정확한 첫 번째 영화 반환
            }
        } catch (Exception e) {
            System.out.println("TMDB 검색 실패: " + title);
        }
        return null;
    }
}