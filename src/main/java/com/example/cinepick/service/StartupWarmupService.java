package com.example.cinepick.service;

import com.example.cinepick.dto.response.StartupStatusResDto;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/**
 * 서버를 켠 직후의 준비 작업을 백그라운드에서 돌리고 그동안의 상태를 알려준다.
 * @PostConstruct 로 두면 Tomcat 이 포트를 열기 전에 30초를 붙잡혀 대기 화면조차 띄울 수 없다.
 * 그래서 ApplicationReadyEvent(= 포트가 열린 뒤)에 별도 스레드로 시작한다.
 */
@Service
@RequiredArgsConstructor
public class StartupWarmupService {

    private final MovieService movieService;
    private final MovieCrawlerScheduler movieCrawlerScheduler;
    private final CsService csService;

    /** 준비 완료 여부. 여러 요청 스레드가 읽고 워밍업 스레드가 쓰므로 volatile 이어야 한다 (아래 셋도 같음) */
    private volatile boolean ready = false;
    private volatile String phase = "서버를 시작하는 중이에요";
    private volatile int percent = 0;
    private volatile boolean degraded = false;
    private volatile Instant startedAt = Instant.now();

    /**
     * 크롤링 진행률이 차지할 구간. 앞의 0~{@value}% 는 TMDB 초기 적재 몫이다.
     */
    private static final int CRAWL_START_PERCENT = 10;

    /**
     * 포트가 열린 뒤 호출된다.
     * <b>이 메서드는 즉시 반환해야 한다</b> — 여기서 붙잡으면 이벤트를 발행한 스레드가 멈춰
     * 결국 처음 문제로 돌아간다. 그래서 작업은 데몬 스레드에 넘긴다
     * (데몬이어야 준비 중에 서버를 꺼도 JVM 이 남지 않는다).
     */
    @EventListener(ApplicationReadyEvent.class)
    public void startWarmUp() {
        Thread worker = new Thread(this::runWarmUp, "startup-warmup");
        worker.setDaemon(true);
        worker.start();
    }

    private void runWarmUp() {
        startedAt = Instant.now();
        ready = false;
        degraded = false;

        try {
            // 고객지원 게시판 초기 글. DB 접근 몇 번이라 진행률을 따로 나눌 만큼 걸리지 않는다
            csService.seedIfEmpty();

            update("영화 기본 정보를 확인하는 중이에요", 3);
            movieService.seedIfEmpty();

            // 저장해 둔 크롤링 결과가 있으면 그것으로 문을 먼저 연다.
            // 배포 환경에서는 배포·크래시·유휴 슬립마다 재시작이 일어나는데, 그때마다 방문자가
            // 30초를 기다리게 할 수는 없다. 새 크롤링은 문을 연 뒤 이어서 돈다
            boolean restored = movieCrawlerScheduler.restoreFromSnapshot();
            if (restored) {
                update("준비가 끝났어요", 100);
                ready = true;
            }

            if (!restored) {
                update("예매 사이트에서 상영작을 불러오는 중이에요", CRAWL_START_PERCENT);
            }
            // 복원했다면 대기 화면이 이미 사라진 뒤라 진행률을 보낼 곳이 없다
            movieCrawlerScheduler.refresh(restored ? null : this::onCrawlStep);

            // 크롤링이 통째로 실패하면 결과가 비어 있다. 화면은 열어주되 그 사실을 남긴다
            // (복원에 성공했다면 그 데이터가 남아 있으므로 여기서 걸리지 않는다)
            degraded = movieCrawlerScheduler.getCachedResult().nowShowing.isEmpty();

        } catch (Exception e) {
            // 준비에 실패해도 사용자를 로딩 화면에 가둘 수는 없다.
            // 들어오게 해주고, 비어 있는 부분은 화면이 각자 안내한다
            System.out.println("[시작 준비] 실패: " + e.getMessage());
            degraded = true;
        } finally {
            update(degraded ? "일부 정보를 불러오지 못했어요" : "준비가 끝났어요", 100);
            ready = true;
            System.out.println("[시작 준비] 완료 (" + elapsedSeconds() + "초, degraded=" + degraded + ")");
        }
    }

    /** 크롤링 단계 진행률을 전체 진행률로 환산 */
    private void onCrawlStep(String label, int done, int total) {
        int span = 100 - CRAWL_START_PERCENT;
        update(label, CRAWL_START_PERCENT + (total <= 0 ? 0 : span * done / total));
    }

    private void update(String phase, int percent) {
        this.phase = phase;
        this.percent = Math.max(0, Math.min(100, percent));
    }

    public boolean isReady() {
        return ready;
    }

    public StartupStatusResDto status() {
        return StartupStatusResDto.builder()
                .ready(ready)
                .phase(phase)
                .percent(percent)
                .elapsedSeconds(elapsedSeconds())
                .degraded(degraded)
                .build();
    }

    private long elapsedSeconds() {
        return Duration.between(startedAt, Instant.now()).toSeconds();
    }
}
