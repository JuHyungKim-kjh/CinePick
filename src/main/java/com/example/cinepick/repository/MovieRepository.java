package com.example.cinepick.repository;

import com.example.cinepick.domain.Movie;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MovieRepository extends JpaRepository<Movie, Long> {
    // 제목 검색 + 페이징·정렬
    Page<Movie> findByTitleContaining(String keyword, Pageable pageable);

    // 중복 저장 방지용 영화 코드 조회
    Optional<Movie> findByMovieCd(String movieCd);

    // 분위기 분석 대상: 아직 미분석이고 재시도 한도에도 안 걸린 영화
    List<Movie> findByMoodTaggedAtIsNullAndMoodTagAttemptsLessThan(int maxAttempts);

    // 재시도 한도를 넘겨 포기한 영화 (원인 확인용)
    List<Movie> findByMoodTaggedAtIsNullAndMoodTagAttemptsGreaterThanEqual(int maxAttempts);
}
