package com.example.cinepick.dto.request;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 취향 저장 요청. 두 화면이 서로 다른 모양으로 채운다.
 * - 설문 팝업: genres 3개 + moods 3개. 고른 순서가 곧 순위
 * - 취향 수정 화면: axisNames/axisValues 로 13개 축 값 전체 + surveyWeight
 *
 * 두 목록을 index 로 짝지어 읽는다 — Map 이면 파라미터 이름에 한글 축 이름이 들어간다.
 */
@Getter
@Setter
public class SurveyReqDto {
    private List<String> genres;
    private List<String> moods;

    // 취향 수정 화면의 축 슬라이더. 같은 순서로 짝지어진다 (axisNames[i] ↔ axisValues[i])
    private List<String> axisNames;
    private List<Integer> axisValues;

    // 취향 수정 화면의 슬라이더 값. 설문 팝업은 장르/분위기만 보내므로 여기서는 null 이 된다.
    // 원시 타입(int)이면 미전송 시 0 으로 덮여 기존 설정이 파괴되므로 반드시 래퍼 타입이어야 한다
    private Integer surveyWeight;
    private Integer genreDiversity;
    private Integer moodIntensity;
}
