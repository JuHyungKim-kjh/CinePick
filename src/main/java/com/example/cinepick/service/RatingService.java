package com.example.cinepick.service;

import com.example.cinepick.domain.Member;
import com.example.cinepick.domain.Movie;
import com.example.cinepick.domain.MovieRating;
import com.example.cinepick.domain.Reservation;
import com.example.cinepick.domain.ReservationStatus;
import com.example.cinepick.dto.request.RatingReqDto;
import com.example.cinepick.dto.response.RatingResDto;
import com.example.cinepick.repository.MovieRatingRepository;
import com.example.cinepick.repository.MovieRepository;
import com.example.cinepick.repository.ReservationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 평점과 한줄 리뷰 관리.
 *
 * 규칙 두 가지:
 * 1) 평점은 확정한 예매가 있는 영화에만. 횟수 제한 없이 언제든 고칠 수 있다
 * 2) 몇 번을 예매하든 평점은 회원+영화당 한 행. 목록의 영화 단위 접기와
 *    {@code MovieRating} 의 unique 제약이 그 역할을 한다
 */
@Service
@RequiredArgsConstructor
public class RatingService {

    private final MovieRatingRepository movieRatingRepository;
    private final ReservationRepository reservationRepository;
    private final MovieRepository movieRepository;

    private static final DateTimeFormatter DATE_LABEL = DateTimeFormatter.ofPattern("yyyy.MM.dd");

    private static final int MIN_SCORE = 1;
    private static final int MAX_SCORE = 5;
    private static final int MAX_REVIEW_LENGTH = 200; // MovieRating.review 컬럼 길이와 맞춰야 합니다

    /** 저장 결과. 화면에 그대로 보여줄 메시지를 함께 돌려준다 */
    public record SaveResult(boolean ok, String message) {
        static SaveResult ok(String message) {
            return new SaveResult(true, message);
        }

        static SaveResult fail(String message) {
            return new SaveResult(false, message);
        }
    }

    // ---------------------------------------------------------------- 조회

    /**
     * 평점 관리 화면 목록.
     * 기준은 "확정된 예매가 있는 영화" — 평점을 아직 안 남겼어도 입력할 자리로 보여주고,
     * 취소한 예매는 나타나지 않는다.
     */
    @Transactional(readOnly = true)
    public List<RatingResDto> findRatingList(Long memberId) {
        List<Reservation> confirmed = reservationRepository
                .findByMemberIdAndStatusOrderByDecidedAtDesc(memberId, ReservationStatus.CONFIRMED);
        if (confirmed.isEmpty()) return List.of();

        // 평점을 영화별로 한 번에 가져온다 (목록 건수만큼 조회하지 않으려고)
        Map<Long, MovieRating> ratings = movieRatingRepository.findByMemberId(memberId).stream()
                .collect(Collectors.toMap(r -> r.getMovie().getId(), Function.identity(), (a, b) -> a));

        // 같은 영화를 여러 번 예매했을 수 있으므로 영화 단위로 접는다.
        // 이 접기가 "재예매해도 평점 줄은 하나"를 만드는 부분이라 건드리면 안 된다.
        // 정렬이 최근 확정 순이라, 먼저 들어온 것이 그 영화의 최근 예매다
        Map<Long, Movie> movies = new LinkedHashMap<>();
        Map<Long, LocalDateTime> lastDecidedAt = new HashMap<>();

        for (Reservation r : confirmed) {
            Long movieId = r.getMovie().getId();
            movies.putIfAbsent(movieId, r.getMovie());
            lastDecidedAt.putIfAbsent(movieId, r.getDecidedAt());
        }

        List<RatingResDto> result = new ArrayList<>();
        movies.forEach((movieId, movie) -> {
            MovieRating rating = ratings.get(movieId);
            result.add(RatingResDto.builder()
                    .movieCd(movie.getMovieCd())
                    .title(movie.getTitle())
                    .posterUrl(movie.getPosterUrl())
                    .genre(movie.getGenre())
                    .score(rating == null ? 0 : rating.getScore())
                    .review(rating == null ? null : rating.getReview())
                    .rated(rating != null)
                    .reservedAt(format(lastDecidedAt.get(movieId)))
                    .ratedAt(rating == null ? null : format(rating.getUpdatedAt()))
                    .build());
        });
        return result;
    }

    // ---------------------------------------------------------------- 저장

    /**
     * 평점 저장. 이미 남긴 평점이 있으면 덮어쓴다(행이 늘지 않는다).
     * 확정한 예매가 없는 영화에는 저장하지 않는다 — 화면에는 평가 가능한 영화만 보여주지만
     * 요청을 직접 만들어 보낼 수도 있으므로 서버에서도 확인한다.
     */
    @Transactional
    public SaveResult save(Member member, RatingReqDto reqDto) {
        if (reqDto.getScore() < MIN_SCORE || reqDto.getScore() > MAX_SCORE) {
            return SaveResult.fail("별점은 1점에서 5점 사이로 선택해주세요.");
        }

        String review = normalizeReview(reqDto.getReview());
        if (review != null && review.length() > MAX_REVIEW_LENGTH) {
            return SaveResult.fail("한줄 리뷰는 " + MAX_REVIEW_LENGTH + "자까지 쓸 수 있어요.");
        }

        Movie movie = movieRepository.findByMovieCd(reqDto.getMovieCd()).orElse(null);
        if (movie == null) {
            return SaveResult.fail("영화 정보를 찾을 수 없습니다.");
        }

        // 확정한 예매가 있는지 확인한다. 이 조회가 곧 권한 검사다
        // (회원 조건이 함께 걸려 있어 남의 예매를 근거로는 저장할 수 없다)
        boolean canRate = reservationRepository.existsByMemberIdAndMovieIdAndStatus(
                member.getId(), movie.getId(), ReservationStatus.CONFIRMED);
        if (!canRate) {
            return SaveResult.fail("예매를 확정한 영화에만 평점을 남길 수 있어요.");
        }

        MovieRating rating = movieRatingRepository
                .findByMemberIdAndMovieId(member.getId(), movie.getId())
                .orElseGet(() -> {
                    MovieRating fresh = new MovieRating();
                    fresh.setMember(member);
                    fresh.setMovie(movie);
                    return fresh;
                });
        boolean isUpdate = rating.getId() != null;

        rating.setScore(reqDto.getScore());
        rating.setReview(review);
        rating.setUpdatedAt(LocalDateTime.now());
        movieRatingRepository.save(rating);

        return SaveResult.ok(isUpdate ? "평점을 수정했어요." : "평점을 남겼어요.");
    }

    private String normalizeReview(String review) {
        if (review == null) return null;
        String trimmed = review.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String format(LocalDateTime dateTime) {
        return dateTime == null ? null : dateTime.format(DATE_LABEL);
    }
}
