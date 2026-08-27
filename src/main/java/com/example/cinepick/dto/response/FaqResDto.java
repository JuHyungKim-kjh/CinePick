package com.example.cinepick.dto.response;

import lombok.Builder;
import lombok.Getter;

/** FAQ 아코디언 한 칸. */
@Getter
@Builder
public class FaqResDto {

    private Long id;

    /** 분류. 화면 상단 탭이 이 값으로 걸러낸다 */
    private String category;

    private String question;

    private String answer;

    /** 같은 분류 안에서의 노출 순서. 목록이 아니라 <b>수정 폼만</b> 쓴다 —
     *  현재 값을 보여주지 않으면 저장할 때마다 순서를 다시 찍어 넣어야 한다 */
    private int sortOrder;
}
