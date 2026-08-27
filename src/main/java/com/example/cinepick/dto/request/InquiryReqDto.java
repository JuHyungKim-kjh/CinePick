package com.example.cinepick.dto.request;

import lombok.Getter;
import lombok.Setter;

/**
 * 1:1 상담 접수 폼의 입력값.
 * 작성자는 담지 않는다 — 폼에서 받으면 남의 이름으로 문의를 남길 수 있다.
 * 회원은 서버가 Authentication 에서 찾는다.
 */
@Getter
@Setter
public class InquiryReqDto {

    /** 문의 유형. 비워 보내면 서비스가 "기타"로 채운다 */
    private String category;

    private String title;

    private String content;
}
