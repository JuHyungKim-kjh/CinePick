package com.example.cinepick.repository;

import com.example.cinepick.domain.MovieRating;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MovieRatingRepository extends JpaRepository<MovieRating, Long> {

    /** 회원이 이 영화에 이미 남긴 평점 (회원+영화당 한 행) */
    Optional<MovieRating> findByMemberIdAndMovieId(Long memberId, Long movieId);

    /** 취향 점수 계산용. 장르를 봐야 하므로 movie 를 함께 가져온다 */
    @EntityGraph(attributePaths = "movie")
    List<MovieRating> findByMemberId(Long memberId);
}
