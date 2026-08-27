package com.example.cinepick.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 회원이 남긴 1:1 상담 한 건.
 *
 * {@link Faq} 와 성격이 정반대다. FAQ 는 운영자가 모두에게 쓰는 공개 글이고 이쪽은 회원 개인의 글이다.
 * 그래서 목록은 언제나 본인 것만 보여야 하고({@code findByMemberIdOrderByCreatedAtDesc}),
 * 화면 전체가 로그인 뒤에 있다(SecurityConfig 의 {@code /cs/inquiry/**}).
 * 답변은 운영자가 /cs/admin/inquiries 에서 단다.
 */
@Entity
@Getter
@Setter
public class Inquiry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    /** 문의 유형. "예매" / "추천" / "계정" / "기타" — FAQ 분류와 같은 이름 */
    @Column(nullable = false, length = 20)
    private String category = "기타";

    @Column(nullable = false, length = 200)
    private String title;

    /** 문의 내용. 줄바꿈을 살려 보여주므로 화면에서 white-space: pre-line 을 쓴다 */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InquiryStatus status = InquiryStatus.WAITING;

    /** 운영자 답변. 답변 전에는 null */
    @Column(columnDefinition = "TEXT")
    private String answer;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    /** 답변이 달린 시각. WAITING 인 동안에는 null */
    private LocalDateTime answeredAt;

    /** 답변이 실제로 채워졌는지. 상태만 믿지 않고 내용까지 확인한다 */
    public boolean isAnswered() {
        return status == InquiryStatus.ANSWERED && answer != null && !answer.isBlank();
    }
}
