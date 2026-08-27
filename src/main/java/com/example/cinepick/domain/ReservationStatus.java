package com.example.cinepick.domain;

/**
 * 예매 중계 이력의 상태.
 * CinePick 은 예매를 직접 처리하지 못하고 링크만 연결하므로, 실제 예매 여부는 사용자에게 물어
 * PENDING 을 CONFIRMED / CANCELED 로 확정한다.
 */
public enum ReservationStatus {

    /** 예매 사이트로 이동만 한 상태. 사용자에게 확인받기 전 */
    PENDING("확인 대기"),

    /** 사용자가 "실제로 예매했다"고 확정한 상태 */
    CONFIRMED("예매 완료"),

    /** 사용자가 "예매까지 가지 않았다"고 답한 상태 */
    CANCELED("취소함");

    private final String label;

    ReservationStatus(String label) {
        this.label = label;
    }

    /** 화면에 그대로 출력할 한글 라벨 (템플릿에 분기문을 두지 않으려고 서버에서 정한다) */
    public String getLabel() {
        return label;
    }
}
