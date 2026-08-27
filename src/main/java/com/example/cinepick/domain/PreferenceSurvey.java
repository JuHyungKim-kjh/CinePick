package com.example.cinepick.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

@Entity
@Getter
@Setter
public class PreferenceSurvey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false, unique = true)
    private Member member;

    // 선호 장르 1~3순위.
    // 취향 수정이 슬라이더로 바뀐 뒤로 이 값은 '입력'이 아니라 axisValues 상위 3개에서 파생되는
    // '표시용 요약'이다. 배너의 선호 장르 TOP, 순위 보기 토글, AI 인사이트 문구가 쓴다
    @Column(nullable = false)
    private String genreRank1;
    @Column(nullable = false)
    private String genreRank2;
    @Column(nullable = false)
    private String genreRank3;

    // 선호 분위기 1~3순위
    @Column(nullable = false)
    private String moodRank1;
    @Column(nullable = false)
    private String moodRank2;
    @Column(nullable = false)
    private String moodRank3;

    /**
     * 축(장르 8 + 분위기 5)별 선호도 0~100. <b>취향 점수의 진짜 입력값.</b>
     * @ElementCollection 인 이유는 설문에 종속된 값 묶음이기 때문 — 설문이 지워지면 같이 지워지고
     * 장르가 늘어도 스키마를 바꿀 필요가 없다.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "preference_axis", joinColumns = @JoinColumn(name = "survey_id"))
    @MapKeyColumn(name = "axis", length = 40)
    @Column(name = "axis_value", nullable = false)
    private Map<String, Integer> axisValues = new LinkedHashMap<>();

    /**
     * 내가 고른 취향이 최종 분석에서 차지할 지분 0~100. 나머지는 관람 이력 몫.
     * 예전에는 설문이 고정 점수라 관람 편수가 늘수록 저절로 밀렸고 되돌릴 방법이 없었다.
     * 이제 두 출처를 각각 정규화한 뒤 이 비율로 섞으므로 편수가 늘어도 지분은 그대로다.
     */
    /*
     * columnDefinition 으로 DB 기본값까지 못박는다.
     * ddl-auto: update 는 기존 행이 있는 테이블에 not null 컬럼을 추가할 때 0 으로 채운다.
     * 그러면 이미 가입해 있던 회원의 판단 비중이 0% 가 되어 자기 취향이 반영되지 않는 상태로 시작한다.
     * (같은 함정을 reservation.rating_chance_used 에서 한 번 겪었다)
     */
    @Column(nullable = false, columnDefinition = "int not null default 50")
    private int surveyWeight = 50;

    // 추천 해석 강도 (0~100). '어떤 영화인가'가 아니라 '이 사람의 취향을 어떻게 해석할지'를 정하는 값
    @Column(nullable = false)
    private int genreDiversity = 50; // 낮음: 상위 장르에 집중 / 높음: 여러 장르를 고르게

    @Column(nullable = false)
    private int moodIntensity = 50;  // 부드럽게 ~ 강하게

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    // 취향을 수정할 때마다 갱신된다. createdAt 은 필드 초기화자라 신규 엔티티에서만 값이 잡히므로
    // "분석 업데이트" 날짜는 이 필드로 표시해야 수정 시점이 반영된다
    @Column(nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PrePersist
    @PreUpdate
    void touchUpdatedAt() {
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 설문 팝업에서 3개만 골랐을 때 각 순위에 넣어줄 슬라이더 초기값.
     * 팝업은 여전히 "3개를 순서대로" 받는다 — 가입 직후에 슬라이더 13개를 들이밀 수는 없다.
     */
    public static int seedValueForRank(int rank) {
        return switch (rank) {
            case 1 -> 100;
            case 2 -> 70;
            case 3 -> 40;
            default -> 0;
        };
    }

    /**
     * 축 하나의 선호도.
     * axisValues 가 비어 있으면(= 슬라이더 도입 전에 설문을 마친 회원) 저장된 순위에서 값을
     * 만들어낸다. 덕분에 기존 회원 데이터를 마이그레이션 없이 그대로 쓴다.
     */
    public int axisValue(String axis) {
        Integer stored = axisValues.get(axis);
        if (stored != null) return stored;
        return seedValueForRank(rankOf(axis));
    }

    /**
     * 저장된 순위에서 몇 번째인지. 없으면 0.
     * List.of 가 아니라 Arrays.asList 인 이유: 저장된 적 없는 설문은 순위 컬럼이 전부 null 인데
     * List.of 는 null 원소를 받으면 NPE 를 낸다.
     */
    public int rankOf(String axis) {
        int genreIndex = Arrays.asList(genreRank1, genreRank2, genreRank3).indexOf(axis);
        if (genreIndex >= 0) return genreIndex + 1;

        int moodIndex = Arrays.asList(moodRank1, moodRank2, moodRank3).indexOf(axis);
        return moodIndex < 0 ? 0 : moodIndex + 1;
    }
}
