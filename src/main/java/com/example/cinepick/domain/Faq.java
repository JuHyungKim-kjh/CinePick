package com.example.cinepick.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 자주 묻는 질문 한 쌍(질문 + 답변).
 * 모두에게 공개된 안내라 작성 권한이 운영자(ROLE_ADMIN)에게만 있다.
 * 회원이 직접 묻는 창구는 /cs/inquiry 의 {@link Inquiry} 로 따로 있다.
 */
@Entity
@Getter
@Setter
public class Faq {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 분류. 화면에서 이 값으로 탭을 만들므로 목록의 그룹 키 역할 */
    @Column(nullable = false, length = 20)
    private String category = "기타";

    /** 질문 (목록에 접힌 채로 보이는 줄) */
    @Column(nullable = false, length = 200)
    private String question;

    /** 답변. 펼쳤을 때 나오는 본문이라 줄바꿈을 살린다 */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String answer;

    /**
     * 같은 분류 안에서의 노출 순서. 작을수록 위.
     * 최신순이 아닌 이유: FAQ 는 "많이 묻는 순"으로 읽혀야 하는데 그 순서는 작성 시각과 무관하다.
     */
    @Column(nullable = false)
    private int sortOrder = 0;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
