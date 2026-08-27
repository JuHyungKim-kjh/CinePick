package com.example.cinepick.service;

import com.example.cinepick.domain.CrawlSnapshot;
import com.example.cinepick.repository.CrawlSnapshotRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 크롤링 결과를 DB 에 넣고 빼는 일만 맡는다.
 *
 * {@link MovieCrawlerScheduler} 에서 분리한 이유는 실패를 다루는 방식이 다르기 때문이다.
 * 크롤링이 실패하면 화면에 보여줄 것이 없어 사용자가 알아야 하지만, 스냅샷 저장·복원이
 * 실패하는 것은 <b>서비스가 계속 돌아가는 데 아무 지장이 없다</b>(다음 기동이 조금 느려질 뿐).
 * 그래서 여기서는 모든 예외를 삼키고 로그만 남긴다 — 부르는 쪽이 try/catch 로 감쌀 필요가 없다.
 */
@Service
@RequiredArgsConstructor
public class CrawlSnapshotStore {

    private final CrawlSnapshotRepository crawlSnapshotRepository;

    /**
     * 스냅샷 전용 매퍼. 스프링이 관리하는 것을 주입받지 않고 직접 만든다.
     *
     * 하나는 <b>주입받을 수 없기 때문</b>이다 — Spring Boot 4 는 웹 계층 JSON 에 Jackson 3
     * ({@code tools.jackson})를 쓰므로 여기서 필요한 2.x {@code ObjectMapper} 빈은 등록되지 않는다.
     *
     * 다른 하나는 <b>공유하지 않는 편이 안전하기 때문</b>이다. 이 JSON 은 화면에 나가는 응답이
     * 아니라 저장 포맷이라, 응답 규칙(널 제외·이름 전략 등)이 바뀔 때 저장 포맷까지 조용히
     * 바뀌면 예전에 저장해 둔 스냅샷을 못 읽게 된다.
     */
    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 이보다 오래된 스냅샷은 복원하지 않는다.
     * 갱신 주기가 6시간이라 하루가 지났다면 상영표가 이미 바뀌었을 가능성이 높고,
     * 그런 데이터로 문을 열면 <b>종영한 영화에 예매 버튼이 붙는다</b> — 기다리는 편이 낫다.
     */
    private static final Duration MAX_AGE = Duration.ofHours(24);

    /**
     * 크롤링 결과를 저장한다. 행은 하나만 유지하고 매번 덮어쓴다.
     * 실패해도 예외를 던지지 않는다 — 방금 받은 크롤링 결과는 이미 메모리 캐시에 들어가 있다.
     */
    @Transactional
    public void save(MovieCrawlerService.CrawlResult result) {
        if (result == null || result.nowShowing.isEmpty()) {
            return;   // 빈 결과를 덮어쓰면 다음 기동 때 복원할 것이 없어진다
        }
        try {
            CrawlSnapshot snapshot = crawlSnapshotRepository
                    .findById(CrawlSnapshot.SINGLETON_ID)
                    .orElseGet(CrawlSnapshot::new);

            snapshot.setPayload(objectMapper.writeValueAsString(result));
            snapshot.setSavedAt(LocalDateTime.now());
            snapshot.setNowShowingCount(result.nowShowing.size());
            snapshot.setUpcomingCount(result.upcoming.size());

            crawlSnapshotRepository.save(snapshot);
            System.out.println("[스냅샷] 저장 완료. 현재상영작 "
                    + result.nowShowing.size() + "개, 상영예정작 " + result.upcoming.size() + "개");
        } catch (Exception e) {
            System.out.println("[스냅샷] 저장 실패(서비스는 계속 진행): " + e.getMessage());
        }
    }

    /**
     * 저장해 둔 결과를 꺼낸다. 없거나 너무 오래됐거나 읽지 못하면 null 이다.
     *
     * @return 복원한 결과, 쓸 수 없으면 null
     */
    @Transactional(readOnly = true)
    public MovieCrawlerService.CrawlResult load() {
        try {
            CrawlSnapshot snapshot = crawlSnapshotRepository
                    .findById(CrawlSnapshot.SINGLETON_ID)
                    .orElse(null);
            if (snapshot == null) {
                System.out.println("[스냅샷] 저장된 결과 없음 (최초 기동)");
                return null;
            }

            Duration age = Duration.between(snapshot.getSavedAt(), LocalDateTime.now());
            if (age.compareTo(MAX_AGE) > 0) {
                System.out.println("[스냅샷] " + age.toHours() + "시간 전 데이터라 복원하지 않음");
                return null;
            }

            MovieCrawlerService.CrawlResult result =
                    objectMapper.readValue(snapshot.getPayload(), MovieCrawlerService.CrawlResult.class);
            System.out.println("[스냅샷] 복원 완료 (" + age.toMinutes() + "분 전 데이터, 현재상영작 "
                    + result.nowShowing.size() + "개)");
            return result;
        } catch (Exception e) {
            // 구조가 바뀌어 역직렬화가 깨지는 경우가 대표적이다. 그때는 그냥 새로 크롤링한다
            System.out.println("[스냅샷] 복원 실패(새로 크롤링함): " + e.getMessage());
            return null;
        }
    }
}
