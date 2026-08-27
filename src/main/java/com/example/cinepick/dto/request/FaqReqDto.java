package com.example.cinepick.dto.request;

import lombok.Getter;
import lombok.Setter;

/**
 * FAQ 등록 폼의 입력값.
 * 운영자(ROLE_ADMIN)만 보내는 요청이라 작성자 정보를 받지 않는다.
 */
@Getter
@Setter
public class FaqReqDto {

    /** 분류. 비우면 서비스가 "기타"로 채운다 */
    private String category;

    /** 질문 (= 목록에 보이는 제목) */
    private String question;

    /** 답변 (= 펼쳤을 때 나오는 내용) */
    private String answer;

    /** 같은 분류 안에서의 노출 순서. 작을수록 위 */
    private int sortOrder;
}
