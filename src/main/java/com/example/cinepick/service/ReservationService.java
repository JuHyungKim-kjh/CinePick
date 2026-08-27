package com.example.cinepick.service;

import com.example.cinepick.domain.Member;
import com.example.cinepick.domain.Movie;
import com.example.cinepick.domain.Reservation;
import com.example.cinepick.domain.ReservationStatus;
import com.example.cinepick.dto.response.ReservationResDto;
import com.example.cinepick.repository.MovieRepository;
import com.example.cinepick.repository.ReservationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 예매 중계 이력을 기록하고 확정한다.
 * CinePick 은 예매를 대신 처리하지 못하고 링크만 연결하므로, 서버가 아는 것은 "링크를 눌렀다"뿐이다.
 * 실제 예매 여부는 사용자에게 직접 물어 확정한다.
 */
@Service
@RequiredArgsConstructor
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final MovieRepository movieRepository;

    private static final DateTimeFormatter DATE_TIME_LABEL = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm");

    /** 탭 복귀 확인의 유예. 이 시간이 지나야 "예매하셨나요?"를 묻는다 */
    private static final Duration RETURN_GRACE = Duration.ofMinutes(1);

    /** 예매사 코드 → 화면에 보여줄 이름 */
    private static final Map<String, String> SITE_LABELS = Map.of(
            "CGV", "CGV",
            "MEGABOX", "메가박스",
            "LOTTE", "롯데시네마"
    );

    // ---------------------------------------------------------------- 기록

    /**
     * 예매사 링크를 누른 사실을 미확인(PENDING)으로 남긴다.
     * 같은 영화에 미확인 건이 있으면 새 행 대신 갱신한다(예매사를 몇 개 눌러도 한 번의 시도다).
     * 이동 주소는 클라이언트가 보낸 값이 아니라 DB 의 예매사 링크에서 꺼낸다 — 임의 주소 심기 방지.
     *
     * @return 기록에 성공했으면 true
     */
    @Transactional
    public boolean recordClick(Member member, String movieCd, String site) {
        if (member == null || movieCd == null || site == null) return false;

        String siteCode = site.trim().toUpperCase();
        if (!SITE_LABELS.containsKey(siteCode)) return false;

        Movie movie = movieRepository.findByMovieCd(movieCd).orElse(null);
        if (movie == null) return false;

        Reservation reservation = reservationRepository
                .findFirstByMemberIdAndMovieIdAndStatus(member.getId(), movie.getId(), ReservationStatus.PENDING)
                .orElseGet(() -> {
                    Reservation fresh = new Reservation();
                    fresh.setMember(member);
                    fresh.setMovie(movie);
                    return fresh;
                });

        reservation.setSite(siteCode);
        reservation.setSiteUrl(siteUrlOf(movie, siteCode));
        reservation.setClickedAt(LocalDateTime.now());
        reservationRepository.save(reservation);
        return true;
    }

    // ---------------------------------------------------------------- 확정

    /**
     * 미확인 건을 예매 완료 또는 취소로 확정한다.
     * id 만 바꿔 남의 이력을 조작할 수 없도록 <b>반드시 회원 조건을 함께 걸어</b> 조회한다.
     *
     * @return 확정했으면 true (남의 예매이거나 이미 확정된 건이면 false)
     */
    @Transactional
    public boolean decide(Long memberId, Long reservationId, boolean booked) {
        Reservation reservation = reservationRepository
                .findByIdAndMemberId(reservationId, memberId)
                .orElse(null);

        // 이미 답한 건을 다시 눌러도 상태가 뒤집히지 않게 막는다
        if (reservation == null || !reservation.isPending()) return false;

        reservation.setStatus(booked ? ReservationStatus.CONFIRMED : ReservationStatus.CANCELED);
        reservation.setDecidedAt(LocalDateTime.now());
        reservationRepository.save(reservation);
        return true;
    }

    // ---------------------------------------------------------------- 조회

    /** 확인 팝업에 띄울 미확인 건 하나. 없으면 비어 있다 */
    @Transactional(readOnly = true)
    public Optional<ReservationResDto> findOldestPending(Long memberId) {
        return reservationRepository
                .findFirstByMemberIdAndStatusOrderByClickedAtAsc(memberId, ReservationStatus.PENDING)
                .map(this::toDto);
    }

    /**
     * 예매 사이트에 다녀와 이 탭으로 돌아왔을 때 물어볼 미확인 건.
     *
     * 클릭한 지 얼마 안 된 건은 건너뛴다 — 예매 사이트를 열자마자 영화 정보를 다시 보려고
     * 돌아오는 경우가 흔한데, 그때마다 물으면 아직 예매 중인 사람을 붙잡게 된다.
     * 유예 중이라도 페이지를 이동하면 서버 렌더 경로({@link #findOldestPending})를 타므로 곧 보인다.
     */
    @Transactional(readOnly = true)
    public Optional<ReservationResDto> findPendingForReturn(Long memberId) {
        LocalDateTime askAfter = LocalDateTime.now().minus(RETURN_GRACE);

        return reservationRepository
                .findFirstByMemberIdAndStatusOrderByClickedAtAsc(memberId, ReservationStatus.PENDING)
                .filter(r -> r.getClickedAt() != null && r.getClickedAt().isBefore(askAfter))
                .map(this::toDto);
    }

    /** 아직 확인받지 못한 건이 몇 개 남았는지 (팝업에 "1 / 3" 처럼 표시) */
    @Transactional(readOnly = true)
    public long countPending(Long memberId) {
        return reservationRepository.countByMemberIdAndStatus(memberId, ReservationStatus.PENDING);
    }

    /** 예매/취소 내역 화면 목록 (최근 클릭 순) */
    @Transactional(readOnly = true)
    public List<ReservationResDto> findHistory(Long memberId) {
        return reservationRepository.findByMemberIdOrderByClickedAtDesc(memberId)
                .stream()
                .map(this::toDto)
                .toList();
    }

    /** 확정된 예매 목록 (최근 확정 순). 평점 화면과 취향 점수 계산이 공유한다 */
    @Transactional(readOnly = true)
    public List<Reservation> findConfirmed(Long memberId) {
        return reservationRepository.findByMemberIdAndStatusOrderByDecidedAtDesc(
                memberId, ReservationStatus.CONFIRMED);
    }

    // ---------------------------------------------------------------- 변환

    private ReservationResDto toDto(Reservation r) {
        Movie movie = r.getMovie();
        return ReservationResDto.builder()
                .id(r.getId())
                .movieCd(movie.getMovieCd())
                .title(movie.getTitle())
                .posterUrl(movie.getPosterUrl())
                .genre(movie.getGenre())
                .site(SITE_LABELS.getOrDefault(r.getSite(), r.getSite()))
                .siteUrl(r.getSiteUrl())
                .clickedAt(format(r.getClickedAt()))
                .decidedAt(format(r.getDecidedAt()))
                .status(r.getStatus().name())
                .statusLabel(r.getStatus().getLabel())
                .pending(r.isPending())
                .ratingAvailable(r.allowsRating())
                .build();
    }

    private String siteUrlOf(Movie movie, String siteCode) {
        return switch (siteCode) {
            case "CGV" -> movie.getCgvUrl();
            case "MEGABOX" -> movie.getMegaboxUrl();
            case "LOTTE" -> movie.getLotteUrl();
            default -> null;
        };
    }

    private String format(LocalDateTime dateTime) {
        return dateTime == null ? null : dateTime.format(DATE_TIME_LABEL);
    }
}
