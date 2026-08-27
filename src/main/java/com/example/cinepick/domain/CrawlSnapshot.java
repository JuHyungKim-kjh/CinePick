package com.example.cinepick.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 마지막 크롤링 결과를 통째로 담아 둔 스냅샷. <b>항상 한 행만 유지한다</b>({@link #SINGLETON_ID}).
 *
 * 크롤링 결과는 재시작하면 사라지는 인메모리 캐시라, 서버를 켤 때마다 30초를 다시 기다려야 했다.
 * 배포하면 배포·크래시·유휴 슬립마다 그 대기가 반복되므로 DB 에 남겨 두고 기동 즉시 복원한다.
 *
 * 컬럼을 나누지 않고 JSON 한 덩어리로 담는 이유: 이 데이터는 조건으로 조회하는 대상이 아니라
 * 언제나 통째로 읽어 메모리에 올리는 스냅샷이다. 정규화하면 테이블만 늘고 얻는 것이 없다.
 */
@Entity
@Getter
@Setter
public class CrawlSnapshot {

    /** 이 테이블이 갖는 유일한 행의 id. 새 결과는 행을 추가하지 않고 이 행을 덮어쓴다 */
    public static final long SINGLETON_ID = 1L;

    @Id
    private Long id = SINGLETON_ID;

    /** 직렬화한 CrawlResult. 제목·링크·순위가 모두 들어가므로 LONGTEXT 가 필요하다 */
    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String payload;

    /** 이 스냅샷이 얼마나 오래된 것인지. 복원할 때 로그로 남긴다 */
    @Column(nullable = false)
    private LocalDateTime savedAt = LocalDateTime.now();

    /** 복원 로그에 쓰는 요약. payload 를 열어보지 않고도 규모를 알 수 있다 */
    @Column(nullable = false)
    private int nowShowingCount;

    @Column(nullable = false)
    private int upcomingCount;
}
