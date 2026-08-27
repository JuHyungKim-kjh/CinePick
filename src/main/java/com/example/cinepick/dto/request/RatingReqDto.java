package com.example.cinepick.dto.request;

import lombok.Getter;
import lombok.Setter;

/** 평점 관리 화면에서 별점과 한줄 리뷰를 저장할 때 쓰는 입력값. */
@Getter
@Setter
public class RatingReqDto {

    /** 어떤 영화에 대한 평점인지 (KOBIS 영화 코드) */
    private String movieCd;

    /** 별점 1~5 */
    private int score;

    /** 한줄 리뷰. 선택 입력이라 비어 있을 수 있다 */
    private String review;
}
