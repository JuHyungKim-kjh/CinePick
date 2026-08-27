package com.example.cinepick.dto.response;

import lombok.Builder;
import lombok.Getter;

/** 공지사항 목록의 한 줄이자 상세 화면이 쓰는 값. */
@Getter
@Builder
public class NoticeResDto {

    private Long id;

    /** "안내" / "이벤트" 같은 말머리 */
    private String category;

    private String title;

    /** 목록에서는 쓰지 않고 상세에서만 쓴다 */
    private String content;

    /** 상단 고정 공지인지 */
    private boolean pinned;

    private long viewCount;

    /** yyyy.MM.dd 로 미리 포맷해 내려보낸다 (템플릿에 포맷 로직을 두지 않으려고) */
    private String createdAt;

    /** 최근 7일 안에 올라온 글인지. 목록의 NEW 배지 판단 */
    private boolean fresh;
}
