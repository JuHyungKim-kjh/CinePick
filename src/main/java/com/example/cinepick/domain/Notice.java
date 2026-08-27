package com.example.cinepick.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 운영자가 올리는 공지사항 한 건.
 * 작성·수정은 /cs/admin, 초기 데이터는 {@link com.example.cinepick.service.CsService#seedIfEmpty()}.
 */
@Entity
@Getter
@Setter
public class Notice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 말머리. "안내" / "이벤트" / "점검" 처럼 목록에서 배지로 보여준다.
     * enum 이 아닌 이유: 운영하면서 늘어나는 값이라 하나 추가할 때마다 배포가 필요해지면 손해다.
     * 색은 화면에서 이름으로 고른다.
     */
    @Column(nullable = false, length = 20)
    private String category = "안내";

    @Column(nullable = false, length = 200)
    private String title;

    /** 본문. 줄바꿈을 살려 보여주므로 화면에서 white-space: pre-line 을 쓴다 */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    /** 상단 고정 여부. 목록 정렬은 항상 pinned 우선, 그다음 최신순 */
    @Column(nullable = false)
    private boolean pinned = false;

    /** 상세를 열 때마다 1 증가 */
    @Column(nullable = false)
    private long viewCount = 0;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
