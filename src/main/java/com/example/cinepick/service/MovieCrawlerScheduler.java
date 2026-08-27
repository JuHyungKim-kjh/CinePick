package com.example.cinepick.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MovieCrawlerScheduler {

    private final MovieCrawlerService movieCrawlerService;
    private final CrawlSnapshotStore crawlSnapshotStore;

    /** 갱신 주기 6시간. @Scheduled 는 상수만 받으므로 필드로 뽑아둔다 */
    private static final long REFRESH_INTERVAL = 6 * 60 * 60 * 1000L;

    // 최신 크롤링 결과 캐시 (여러 스레드에서 읽으므로 volatile 필수)
    private volatile MovieCrawlerService.CrawlResult cachedResult = new MovieCrawlerService.CrawlResult();

    /**
     * 6시간마다 자동 갱신.
     *
     * <b>시작 직후 1회는 여기서 돌지 않는다</b> — initialDelay 를 주기와 같게 준 이유다.
     * 최초 크롤링은 {@link StartupWarmupService} 가 진행 상황을 받아가며 돌린다.
     * 여기에도 즉시 실행이 걸리면 크롬이 두 개 뜨면서 같은 일을 두 번 한다.
     */
    @Scheduled(initialDelay = REFRESH_INTERVAL, fixedRate = REFRESH_INTERVAL)
    public void refresh() {
        refresh(null);
    }

    public void refresh(MovieCrawlerService.ProgressListener listener) {
        System.out.println("[크롤링 스케줄러] 갱신 시작...");
        try {
            MovieCrawlerService.CrawlResult result = movieCrawlerService.getCrawledMovieTitles(listener);

            // 캐시를 먼저 바꾼다. 저장이 실패하더라도 방금 받은 결과는 이미 서비스에 반영된 상태다
            this.cachedResult = result;
            crawlSnapshotStore.save(result);

            System.out.println("[크롤링 스케줄러] 완료. 현재상영작 "
                    + cachedResult.nowShowing.size() + "개, 상영예정작 "
                    + cachedResult.upcoming.size() + "개");
        } catch (Exception e) {
            System.out.println("[크롤링 스케줄러] 실패: " + e.getMessage());
            // 실패해도 이전 캐시는 유지된다 → 빈 화면을 보여주지 않는다
        }
    }

    /**
     * 저장해 둔 크롤링 결과를 캐시에 올린다. 기동 직후 {@link StartupWarmupService} 가 부른다.
     *
     * 성공하면 크롤링을 기다리지 않고 바로 화면을 열 수 있다. 데이터가 몇 시간 지난 것일 수는
     * 있지만 갱신 주기가 어차피 6시간이라 실질 차이가 없고, 새 크롤링이 곧 이어서 덮어쓴다.
     *
     * @return 복원해서 쓸 수 있으면 true, 저장된 것이 없거나 너무 오래됐으면 false
     */
    public boolean restoreFromSnapshot() {
        MovieCrawlerService.CrawlResult saved = crawlSnapshotStore.load();
        if (saved == null || saved.nowShowing.isEmpty()) {
            return false;
        }
        this.cachedResult = saved;
        return true;
    }

    public MovieCrawlerService.CrawlResult getCachedResult() {
        return cachedResult;
    }
}