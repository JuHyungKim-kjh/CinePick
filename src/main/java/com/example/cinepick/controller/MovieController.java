package com.example.cinepick.controller;

import com.example.cinepick.dto.response.MovieDetailResDto;
import com.example.cinepick.dto.response.MovieListResDto;
import com.example.cinepick.service.MovieService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/movies")
@RequiredArgsConstructor
public class MovieController {
    private final MovieService movieService;


    @GetMapping
    public String movieList(@RequestParam(required = false) String keyword,
                            @RequestParam(defaultValue = "1") int page,
                            @RequestParam(defaultValue = "latest") String sort, // 정렬 파라미터 추가
                            Model model) {

        Map<String, Object> result = movieService.getCombinedMovieList(keyword, page, sort);

        @SuppressWarnings("unchecked")
        List<MovieListResDto> movies = (List<MovieListResDto>) result.get("movies");
        int totalCount = (int) result.get("totalCount");

        int totalPages = (int) Math.ceil((double) totalCount / 10);
        int blockStart = ((page - 1) / 10) * 10 + 1;
        int blockEnd = Math.min(blockStart + 9, totalPages);

        model.addAttribute("movies", movies);
        model.addAttribute("currentPage", page);
        model.addAttribute("startPage", blockStart);
        model.addAttribute("endPage", blockEnd);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("currentSort", sort); // 현재 선택된 정렬 방식 전달

        return "movie/list";
    }

    @GetMapping("/detail/{id}")
    public String movieDetail(@PathVariable String id, Authentication authentication, Model model) {
        MovieDetailResDto movie = movieService.getMovieDetail(id);

        model.addAttribute("movie", movie);
        model.addAttribute("movieStatus", movie.getMovieStatus()); // 정상 작동

        // 예매사 링크 클릭 기록은 로그인 사용자만 — 게스트에게는 스크립트를 붙이지 않는다
        boolean isLoggedIn = authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
        model.addAttribute("isLoggedIn", isLoggedIn);

        return "movie/detail";
    }
}