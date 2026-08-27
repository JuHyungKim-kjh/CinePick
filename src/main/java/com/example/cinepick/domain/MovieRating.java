package com.example.cinepick.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 회원이 한 영화에 남긴 평점과 한줄 리뷰.
 * 회원+영화 조합당 한 행. 다시 예매해 평점을 갱신해도 새 행이 생기지 않고 이 행을 덮어쓴다.
 */
@Entity
@Getter
@Setter
@Table(uniqueConstraints = @UniqueConstraint(name = "uk_rating_member_movie",
        columnNames = {"member_id", "movie_id"}))
public class MovieRating {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "movie_id", nullable = false)
    private Movie movie;

    /** 별점 1~5 */
    private int score;

    /** 한줄 리뷰. 선택 입력이라 비어 있을 수 있다 */
    @Column(length = 200)
    private String review;

    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    void touchUpdatedAt() {
        this.updatedAt = LocalDateTime.now();
    }
}
