package com.example.cinepick.dto.response;

import lombok.Builder;
import lombok.Getter;

/**
 * 내 문의 내역 한 줄.
 * 날짜는 서버에서 문자열로 만들어 내려보낸다 — 화면마다 #temporals.format 을 쓰면 형식이 어긋난다.
 */
@Getter
@Builder
public class InquiryResDto {

    private Long id;

    /**
     * 작성자 닉네임. 운영자 답변 화면에서 누구의 문의인지 가리는 값.
     * 이메일이 아닌 이유: 닉네임에 unique 제약이 있어 식별에 모자람이 없고,
     * 화면까지 이메일을 흘려보낼 이유가 없다.
     */
    private String writer;

    private String category;

    private String title;

    private String content;

    /** 상태 한글 라벨 ("답변 대기" / "답변 완료") */
    private String statusLabel;

    /** 답변이 실제로 채워졌는지. 화면은 이 값으로 답변 영역을 그릴지 정한다 */
    private boolean answered;

    /** 운영자 답변. 아직 없으면 null */
    private String answer;

    /** yyyy.MM.dd 로 미리 포맷된 접수 일자 */
    private String createdAt;

    /** yyyy.MM.dd 로 미리 포맷된 답변 일자. 답변 전에는 null */
    private String answeredAt;
}
