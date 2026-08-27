package com.example.cinepick.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 예매사 링크를 눌러 이동한 사건 하나의 기록.
 *
 * 실제 예매 여부는 사용자에게 물어야만 알 수 있으므로 클릭 시점에는 PENDING 으로 두고
 * 팝업으로 확정한다. CONFIRMED 가 되면 그 영화에 평점을 남길 자격이 생긴다
 * (평점 자체는 회원+영화당 한 행 — {@link MovieRating}).
 */
@Entity
@Getter
@Setter
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "movie_id", nullable = false)
    private Movie movie;

    /** 이동한 예매사 (CGV / MEGABOX / LOTTE) */
    private String site;

    /** 이동한 링크. 내역 화면에서 같은 곳으로 다시 갈 수 있도록 남긴다 */
    @Column(length = 1000)
    private String siteUrl;

    private LocalDateTime clickedAt = LocalDateTime.now();

    @Enumerated(EnumType.STRING)
    private ReservationStatus status = ReservationStatus.PENDING;

    /** 사용자가 확인/취소를 답한 시각. PENDING 인 동안에는 null */
    private LocalDateTime decidedAt;

    /** 아직 사용자 확인을 받지 못한 건인지 */
    public boolean isPending() {
        return status == ReservationStatus.PENDING;
    }

    /** 평점을 남기거나 고칠 수 있는 예매인지 (확정한 영화만 평가 가능) */
    public boolean allowsRating() {
        return status == ReservationStatus.CONFIRMED;
    }
}
