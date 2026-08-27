package com.example.cinepick.domain;

/** 1:1 상담 한 건의 처리 상태. */
public enum InquiryStatus {

    /** 접수만 된 상태. 아직 답변이 없다 */
    WAITING("답변 대기"),

    /** 운영자가 답변을 남긴 상태 */
    ANSWERED("답변 완료");

    private final String label;

    InquiryStatus(String label) {
        this.label = label;
    }

    /** 화면에 그대로 출력할 한글 라벨 (템플릿에 분기문을 두지 않으려고 서버에서 정한다) */
    public String getLabel() {
        return label;
    }
}
