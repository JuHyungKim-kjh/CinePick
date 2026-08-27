package com.example.cinepick.dto.request;

import lombok.Getter;
import lombok.Setter;

/**
 * 공지사항 작성·수정 폼의 입력값.
 *
 * 작성과 수정이 같은 DTO 를 쓴다. 받는 값이 완전히 같은데 나누면 필드를 하나 더할 때마다
 * 두 곳을 고쳐야 하고, 한쪽만 고치면 조용히 값이 누락된다.
 * 작성자는 받지 않는다 — 운영자만 보내는 요청이고 공지에는 작성자를 표시하지 않는다.
 */
@Getter
@Setter
public class NoticeReqDto {

    /** 말머리. 비우면 서비스가 "안내"로 채운다 */
    private String category;

    private String title;

    private String content;

    /**
     * 상단 고정 여부.
     * 체크박스는 <b>꺼져 있으면 파라미터 자체를 보내지 않는다.</b> boolean 이라 그때 false 가
     * 들어오므로 별도 처리는 필요 없지만, 모르면 "체크를 풀었는데 안 풀린다"를 찾아 헤매게 된다.
     */
    private boolean pinned;
}
