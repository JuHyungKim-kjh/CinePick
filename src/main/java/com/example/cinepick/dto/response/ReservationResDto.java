package com.example.cinepick.dto.response;

import lombok.Builder;
import lombok.Getter;

/** 예매/취소 내역 화면의 한 줄, 그리고 확인 팝업이 쓰는 값. */
@Getter
@Builder
public class ReservationResDto {

    private Long id;
    private String movieCd;
    private String title;
    private String posterUrl;
    private String genre;

    /** 이동했던 예매사 (CGV / MEGABOX / LOTTE) */
    private String site;
    private String siteUrl;

    /** yyyy.MM.dd HH:mm 로 미리 포맷 (템플릿에 포맷 로직을 두지 않으려고) */
    private String clickedAt;
    private String decidedAt;

    /** PENDING / CONFIRMED / CANCELED */
    private String status;

    /** "확인 대기" 같은 한글 라벨 */
    private String statusLabel;

    /** 아직 확인받지 못한 건인지 (확인/취소 버튼 노출 판단) */
    private boolean pending;

    /** 확정된 예매라 평점을 남기거나 고칠 수 있는지 */
    private boolean ratingAvailable;
}
