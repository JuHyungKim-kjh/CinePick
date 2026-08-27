package com.example.cinepick.repository;

import com.example.cinepick.domain.Reservation;
import com.example.cinepick.domain.ReservationStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * movie 연관관계가 LAZY 라, 포스터·제목을 쓰는 조회에는 @EntityGraph 로 한 번에 가져온다.
 * 붙이지 않으면 목록 건수만큼 추가 쿼리가 나간다(N+1).
 */
public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    /** 팝업에 띄울 미확인 건 하나. 오래된 것부터 */
    @EntityGraph(attributePaths = "movie")
    Optional<Reservation> findFirstByMemberIdAndStatusOrderByClickedAtAsc(Long memberId,
                                                                         ReservationStatus status);

    long countByMemberIdAndStatus(Long memberId, ReservationStatus status);

    /**
     * 같은 영화에 이미 잡혀 있는 미확인 건.
     * 예매사를 여러 개 눌러보는 것은 한 번의 예매 시도이므로 행을 새로 만들지 않고 갱신한다.
     */
    Optional<Reservation> findFirstByMemberIdAndMovieIdAndStatus(Long memberId, Long movieId,
                                                                ReservationStatus status);

    /** 예매/취소 내역 화면 (최근 클릭 순) */
    @EntityGraph(attributePaths = "movie")
    List<Reservation> findByMemberIdOrderByClickedAtDesc(Long memberId);

    /** 평점 화면·취향 점수 계산용. 확정된 예매만 최근 확정 순으로 */
    @EntityGraph(attributePaths = "movie")
    List<Reservation> findByMemberIdAndStatusOrderByDecidedAtDesc(Long memberId,
                                                                 ReservationStatus status);

    /**
     * 소유권까지 함께 확인하는 단건 조회.
     * id 만 바꿔 남의 예매를 확정/취소하는 것을 막으려고 조회 단계에서 회원을 함께 건다.
     */
    Optional<Reservation> findByIdAndMemberId(Long id, Long memberId);

    /**
     * 평점 저장 권한 확인용.
     * 확정한 예매가 있는 영화만 평가할 수 있고, 회원 조건이 함께 걸려 있어
     * 남의 예매를 근거로 평점을 남길 수 없다.
     */
    boolean existsByMemberIdAndMovieIdAndStatus(Long memberId, Long movieId, ReservationStatus status);
}
