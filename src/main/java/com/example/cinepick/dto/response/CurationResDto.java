package com.example.cinepick.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * 취향 분석 페이지(/recommendations)에 뿌릴 분석 결과 묶음.
 * 화면이 필요한 값을 여기서 모두 완성해 넘기므로 템플릿에 계산식이 남지 않는다.
 */
@Getter
@Builder
public class CurationResDto {

    private String updatedAtLabel;   // "2026.08.11"
    private String topGenreLabel;    // "액션, 드라마"
    private String topMoodLabel;     // "긴장, 카타르시스"

    // 상단 배너 통계 타일
    private int reservedMovieCount;      // 확정 예매한 영화 편수 (같은 영화를 여러 번 예매해도 1편)
    private String averageRatingLabel;   // "4.5", 평점이 없으면 "-"
    private int ratedCount;              // 평점을 남긴 편수

    /** 설문·예매·평점이 판단에 쓰이는 비중 */
    private List<SourceWeight> weights;

    // AI 취향 인사이트 3분할 카드
    private String insightPattern;   // 선호 패턴
    private String insightEmotion;   // 감정적 경향
    private List<String> insightKeywords; // 취향 키워드 칩

    private List<PrefRow> genreRows; // 전체 장르 (비율 내림차순)
    private List<PrefRow> moodRows;  // 전체 분위기 (레이더 축 고정을 위해 원래 순서 유지)

    // 취향 수정 조절판. 표시용 Rows 와 달리 옵션 목록 순서를 지킨다
    // (슬라이더가 값에 따라 자리를 옮기면 드래그 중에 손이 따라갈 수 없다)
    private List<AxisControl> genreControls;
    private List<AxisControl> moodControls;

    /** 내가 고른 취향이 최종 분석에서 차지할 지분 0~100 */
    private int surveyWeight;

    private int genreDiversity;
    private int moodIntensity;

    private List<SourceStatus> sources; // "분석에 반영된 데이터" 4칸

    /** 막대 한 줄에 필요한 값 묶음. 아이콘·색상까지 정해 템플릿의 분기문을 없앤다 */
    @Getter
    @Builder
    public static class PrefRow {
        private String label;      // "액션"
        private int percent;       // 0~100 (합산 점수가 음수면 0)
        private int rank;          // 설문에서 고른 순위 1~3, 고르지 않았으면 0
        private String iconClass;  // "bi bi-lightning-fill"
        private String colorHex;   // "#FA5252"

        /**
         * 합산 점수. 음수까지 그대로 담는다 (-3 = 낮은 평점이 쌓여 3점만큼 내려간 상태).
         * percent 는 0 에서 잘려 "이력이 없어 0%"인지 "싫어해서 0%"인지 구분되지 않는다.
         */
        private int points;

        /** 점수가 음수인지. 막대에 '기피' 배지를 띄울지 정한다 */
        private boolean avoided;
    }

    /**
     * 취향 수정 조절판의 슬라이더 한 줄.
     * PrefRow(분석 결과)와 일부러 분리했다 — 저쪽은 "계산 결과가 몇 %인가", 이쪽은
     * "사용자가 정한 값이 얼마인가"라 같은 축이어도 두 값은 다르다.
     */
    @Getter
    @Builder
    public static class AxisControl {
        private String label;      // "액션"
        private int value;         // 0~100, 슬라이더 위치
        private String iconClass;  // "bi bi-lightning-fill"
        private String colorHex;   // "#FA5252"
    }

    /**
     * 판단 비중 막대의 한 조각.
     * 고정 비율이 아니라 실제 누적 점수에서 나온 값이다.
     */
    @Getter
    @Builder
    public static class SourceWeight {
        private String label;      // "설문" / "예매" / "평점"
        private String colorHex;   // 막대 조각 색
        private int points;        // 영향력 크기 (부호 없는 절댓값 합)
        private int percent;       // 0~100 (세 조각의 합이 정확히 100)

        /**
         * 이 출처가 취향을 끌어올리는 쪽인지 깎아내리는 쪽인지.
         * 낮은 평점을 많이 남기면 평점이 순감소로 작동한다. 크기(percent)만으로는
         * 방향을 알 수 없으므로 화면의 방향 기호에 쓴다.
         */
        private boolean lowering;
    }

    /** 점수 출처 한 칸. ready=false 는 아직 쌓인 이력이 없다는 뜻 */
    @Getter
    @Builder
    public static class SourceStatus {
        private String label;      // "예매 내역"
        private String iconClass;  // "bi bi-ticket-perforated"
        private boolean ready;     // 실제로 점수에 반영되고 있는지
        private String detail;     // 보조 설명 문구
    }
}
