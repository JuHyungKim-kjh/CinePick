package com.example.cinepick.service;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.PageLoadStrategy;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class MovieCrawlerService {

    public static class SiteLink {
        public final String siteName;
        public final String detailUrl;

        // 필드가 final 이라 Jackson 이 되돌릴 방법이 이 생성자뿐이다.
        // CrawlSnapshotStore 가 이 구조를 JSON 으로 저장했다 복원한다
        @JsonCreator
        public SiteLink(@JsonProperty("siteName") String siteName,
                        @JsonProperty("detailUrl") String detailUrl) {
            this.siteName = siteName;
            this.detailUrl = detailUrl;
        }
    }

    public static class CrawlResult {
        public List<String> nowShowing = new ArrayList<>();
        public List<String> upcoming = new ArrayList<>();
        public Map<String, List<SiteLink>> nowShowingLinks = new HashMap<>();
        // cleanTitle(제목) -> 그 영화를 상영예정으로 표시한 "모든" 사이트의 원문 텍스트
        public Map<String, List<String>> upcomingReleaseInfo = new HashMap<>(); // String -> List<String>으로 변경

        // 사이트명 -> 그 사이트의 무비차트/박스오피스 순위 순서대로의 제목 목록.
        // 현재상영작 목록은 3사를 합치며 중복을 제거해 사이트별 순위가 사라지므로 따로 담는다
        public Map<String, List<String>> rankedByBrand = new LinkedHashMap<>();
    }

    // WebElement 하나에서 문자열(상세링크·날짜 등)을 뽑는 공용 인터페이스
    @FunctionalInterface
    private interface ElementTextExtractor {
        String extract(WebElement titleElement);
    }

    /**
     * 크롤링이 어디까지 왔는지 알려주는 통로.
     * 서버를 켠 직후에는 이 작업이 끝나야 화면을 그릴 수 있어 사용자가 로딩 화면에서 기다린다.
     * 아무것도 보여주지 않으면 멈춘 것처럼 보이므로 단계마다 알린다.
     */
    @FunctionalInterface
    public interface ProgressListener {
        void onStep(String label, int done, int total);
    }

    private static class SiteConfig {
        final String siteName;
        final String url;
        final String nowTabText;
        final String upcomingTabText;
        final String upcomingUrl;
        final String titleSelector;
        final String closeButtonSelector;
        final String loadMoreButtonSelector;
        final ElementTextExtractor detailUrlExtractor;   // 현재상영작 상세링크 추출
        final ElementTextExtractor upcomingInfoExtractor; // 상영예정작 개봉일 정보 추출 (없으면 null)

        SiteConfig(String siteName, String url, String nowTabText, String upcomingTabText,
                   String upcomingUrl, String titleSelector, String closeButtonSelector,
                   String loadMoreButtonSelector, ElementTextExtractor detailUrlExtractor,
                   ElementTextExtractor upcomingInfoExtractor) {
            this.siteName = siteName;
            this.url = url;
            this.nowTabText = nowTabText;
            this.upcomingTabText = upcomingTabText;
            this.upcomingUrl = upcomingUrl;
            this.titleSelector = titleSelector;
            this.closeButtonSelector = closeButtonSelector;
            this.loadMoreButtonSelector = loadMoreButtonSelector;
            this.detailUrlExtractor = detailUrlExtractor;
            this.upcomingInfoExtractor = upcomingInfoExtractor;
        }
    }

    private static final ElementTextExtractor CGV_EXTRACTOR = titleEl -> {
        try {
            WebElement li = titleEl.findElement(By.xpath("ancestor::li[1]"));
            WebElement img = li.findElement(By.tagName("img"));
            String src = img.getAttribute("src");
            Matcher m = Pattern.compile("/Poster/\\d+/(\\d+)/").matcher(src);
            if (m.find()) {
                return "https://cgv.co.kr/cnm/cgvChart/movieChart/" + m.group(1);
            }
        } catch (Exception e) {
            System.out.println("  [CGV 링크추출 실패] " + e.getMessage());
        }
        return null;
    };

    // CGV 상영예정작 목록의 "2026.08.12 재개봉" / "2026.08.12 개봉" 텍스트 추출
    private static final ElementTextExtractor CGV_UPCOMING_INFO_EXTRACTOR = titleEl -> {
        try {
            WebElement li = titleEl.findElement(By.xpath("ancestor::li[1]"));
            WebElement infoSpan = li.findElement(By.cssSelector(".bestChartList_info__SZwYg span"));
            String text = infoSpan.getText().trim();
            return text.isEmpty() ? null : text;
        } catch (Exception e) {
            return null;
        }
    };

    private static final ElementTextExtractor MEGABOX_EXTRACTOR = titleEl -> {
        try {
            WebElement li = titleEl.findElement(By.xpath("ancestor::li[1]"));
            WebElement btn = li.findElement(By.cssSelector("a.movieBtn"));
            String no = btn.getAttribute("data-no");
            if (no != null && !no.isEmpty()) {
                return "https://www.megabox.co.kr/movie-detail?rpstMovieNo=" + no;
            }
        } catch (Exception e) {
            System.out.println("  [MEGABOX 링크추출 실패] " + e.getMessage());
        }
        return null;
    };
    private static final ElementTextExtractor MEGABOX_UPCOMING_INFO_EXTRACTOR = titleEl -> {
        try {
            WebElement li = titleEl.findElement(By.xpath("ancestor::li[1]"));
            WebElement dateSpan = li.findElement(By.cssSelector(".rate-date span.date"));
            String text = dateSpan.getText().trim();
            return text.isEmpty() ? null : text;
        } catch (Exception e) {
            return null;
        }
    };

    private static final ElementTextExtractor LOTTE_EXTRACTOR = titleEl -> {
        try {
            WebElement li = titleEl.findElement(By.xpath("ancestor::li[1]"));
            WebElement detailLink = li.findElement(By.cssSelector("a.btn_col3.ty3"));
            String href = detailLink.getAttribute("href");
            return (href != null && !href.isEmpty()) ? href : null;
        } catch (Exception e) {
            System.out.println("  [LotteCinema 링크추출 실패] " + e.getMessage());
        }
        return null;
    };

    public CrawlResult getCrawledMovieTitles() {
        return getCrawledMovieTitles(null);
    }

    /** 진행 상황을 받아보며 크롤링한다. listener 가 null 이면 조용히 돈다 */
    public CrawlResult getCrawledMovieTitles(ProgressListener listener) {
        CrawlResult result = new CrawlResult();
        Set<String> normalizedNow = new HashSet<>();
        Set<String> normalizedUpcoming = new HashSet<>();

        List<SiteConfig> sites = List.of(
                new SiteConfig("CGV", "https://cgv.co.kr/cnm/cgvChart/movieChart",
                        "현재상영작", "상영예정",
                        null,
                        "span[class*='bestChartList_name']",
                        null,
                        null,
                        CGV_EXTRACTOR,
                        CGV_UPCOMING_INFO_EXTRACTOR), // CGV만 개봉일 정보 지원
                new SiteConfig("LotteCinema", "https://www.lottecinema.co.kr/NLCHS/Movie/List",
                        "현재 상영작", "개봉 예정작",
                        null,
                        "strong.tit_info",
                        ".appbannermain_wrap .btn_close",
                        null,
                        LOTTE_EXTRACTOR,
                        null), // 롯데는 아직 미확인 -> null
                new SiteConfig("Megabox", "https://www.megabox.co.kr/movie",
                        "박스오피스", null,
                        "https://www.megabox.co.kr/movie/comingsoon",
                        ".tit-area p.tit",
                        null,
                        "#btnAddMovie",
                        MEGABOX_EXTRACTOR,
                        MEGABOX_UPCOMING_INFO_EXTRACTOR) // null
        );

        // 사이트마다 현재상영작·상영예정작 두 번씩 훑고, 그 앞에 브라우저를 띄우는 단계가 하나 더 있다
        int totalSteps = sites.size() * 2 + 1;
        int done = 0;
        report(listener, "예매 사이트를 둘러볼 준비를 하고 있어요", done, totalSteps);

        WebDriverManager.chromedriver().setup();

        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--remote-allow-origins=*");
        options.addArguments("user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
        options.addArguments("--disable-gpu");
        options.addArguments("--disable-extensions");
        options.addArguments("--disable-blink-features=AutomationControlled");
        options.setExperimentalOption("excludeSwitches", List.of("enable-automation"));
        options.setExperimentalOption("useAutomationExtension", false);

        Map<String, Object> prefs = new HashMap<>();
        prefs.put("profile.managed_default_content_settings.images", 2);
        prefs.put("profile.default_content_setting_values.notifications", 2);
        options.setExperimentalOption("prefs", prefs);
        options.setPageLoadStrategy(PageLoadStrategy.EAGER);

        WebDriver driver = null;

        try {
            driver = new ChromeDriver(options);
            driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(15));
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(5));
            report(listener, "예매 사이트에 접속하고 있어요", ++done, totalSteps);

            for (SiteConfig site : sites) {
                // 현재상영작 탭은 무비차트/박스오피스 화면이라 DOM 순서가 곧 순위다.
                // 합쳐진 목록과 별개로 사이트별 순서를 보존한다
                List<String> ranked = new ArrayList<>();
                result.rankedByBrand.put(site.siteName, ranked);

                scrapeTab(driver, wait, site, site.url, site.nowTabText, "현재상영작",
                        normalizedNow, result.nowShowing, result.nowShowingLinks, null, ranked);
                report(listener, site.siteName + " 현재상영작을 확인했어요", ++done, totalSteps);

                if (site.upcomingUrl != null) {
                    scrapeTab(driver, wait, site, site.upcomingUrl, null, "상영예정작",
                            normalizedUpcoming, result.upcoming, null, result.upcomingReleaseInfo, null);
                } else {
                    scrapeTab(driver, wait, site, site.url, site.upcomingTabText, "상영예정작",
                            normalizedUpcoming, result.upcoming, null, result.upcomingReleaseInfo, null);
                }
                report(listener, site.siteName + " 상영예정작을 확인했어요", ++done, totalSteps);
            }

        } catch (Exception e) {
            System.out.println("Selenium 전체 실행 중 오류: " + e.getMessage());
        } finally {
            if (driver != null) {
                driver.quit();
            }
        }

        return result;
    }

    /** 진행 상황 알림. 로딩 화면 때문에 크롤링을 실패시킬 수는 없으므로 리스너의 예외는 삼킨다 */
    private void report(ProgressListener listener, String label, int done, int total) {
        if (listener == null) return;
        try {
            listener.onStep(label, done, total);
        } catch (Exception e) {
            System.out.println("[크롤링 진행 알림 실패] " + e.getMessage());
        }
    }

    private void scrapeTab(WebDriver driver, WebDriverWait wait, SiteConfig site,
                           String targetUrl, String tabText, String logLabel,
                           Set<String> normalizedTitles, List<String> resultList,
                           Map<String, List<SiteLink>> linksMap,
                           Map<String, List<String>> upcomingInfoMap,
                           List<String> rankedOut) {
        try {
            boolean loaded = safeGet(driver, targetUrl, 3);
            if (!loaded) {
                System.out.println("[" + site.siteName + " - " + logLabel + "] 접속 최종 실패 (3회 재시도 후 포기)");
                return;
            }

            JavascriptExecutor js = (JavascriptExecutor) driver;

            if (site.closeButtonSelector != null) {
                try {
                    WebElement closeBtn = new WebDriverWait(driver, Duration.ofSeconds(3))
                            .until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(site.closeButtonSelector)));
                    js.executeScript("arguments[0].click();", closeBtn);
                } catch (Exception ignore) { }
            }

            if (tabText != null) {
                String xpath = String.format("//*[contains(text(), '%s')]", tabText);
                WebElement tabButton = wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath(xpath)));
                js.executeScript("arguments[0].click();", tabButton);
            }

            wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(site.titleSelector)));

            if (site.loadMoreButtonSelector != null) {
                clickLoadMoreRepeatedly(driver, js, site.loadMoreButtonSelector, site.titleSelector);
            }

            List<WebElement> elements = driver.findElements(By.cssSelector(site.titleSelector));
            int count = 0;

            for (WebElement element : elements) {
                String title = ((String) js.executeScript("return arguments[0].textContent;", element)).trim();
                if (title.isEmpty() || title.length() == 1) continue;

                String cleaned = cleanTitle(title);

                // 이 사이트에서의 노출 순서(=순위)를 그대로 기록한다. 사이트 안에서만 중복을 거른다
                if (rankedOut != null && !rankedOut.contains(title)) {
                    rankedOut.add(title);
                }

                if (linksMap != null && site.detailUrlExtractor != null) {
                    String detailUrl = site.detailUrlExtractor.extract(element);
                    if (detailUrl != null) {
                        linksMap.computeIfAbsent(cleaned, k -> new ArrayList<>())
                                .add(new SiteLink(site.siteName, detailUrl));
                    }
                }

                if (upcomingInfoMap != null && site.upcomingInfoExtractor != null) {
                    String info = site.upcomingInfoExtractor.extract(element);
                    if (info != null) {
                        upcomingInfoMap.computeIfAbsent(cleaned, k -> new ArrayList<>()).add(info);
                    }
                }

                if (!normalizedTitles.contains(cleaned)) {
                    normalizedTitles.add(cleaned);
                    resultList.add(title);
                    count++;
                }
            }
            System.out.println("[" + site.siteName + " - " + logLabel + "] 크롤링 성공. 신규 추가된 영화: " + count + "개 (총 " + elements.size() + "개 요소 확인)");

        } catch (Exception e) {
            System.out.println("[" + site.siteName + " - " + logLabel + "] 크롤링 실패 (타임아웃 또는 구조 변경): " + e.getMessage());
        }
    }

    private boolean safeGet(WebDriver driver, String url, int maxRetries) {
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                driver.get(url);
                return true;
            } catch (Exception e) {
                System.out.println("  [접속 재시도 " + attempt + "/" + maxRetries + "] "
                        + url + " -> " + e.getClass().getSimpleName());
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ignored) { }
            }
        }
        return false;
    }

    private void clickLoadMoreRepeatedly(WebDriver driver, JavascriptExecutor js,
                                         String buttonSelector, String titleSelector) {
        int maxClicks = 20;

        for (int i = 0; i < maxClicks; i++) {
            List<WebElement> moreButtons = driver.findElements(By.cssSelector(buttonSelector));
            if (moreButtons.isEmpty()) break;

            WebElement moreBtn = moreButtons.get(0);
            if (!moreBtn.isDisplayed()) break;

            int beforeCount = driver.findElements(By.cssSelector(titleSelector)).size();

            try {
                js.executeScript("arguments[0].click();", moreBtn);
            } catch (Exception e) {
                break;
            }

            boolean increased = false;
            for (int t = 0; t < 10; t++) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ignored) { }
                int afterCount = driver.findElements(By.cssSelector(titleSelector)).size();
                if (afterCount > beforeCount) {
                    increased = true;
                    break;
                }
            }
            if (!increased) break;
        }
    }

    public String cleanTitle(String title) {
        if (title == null) return "";
        return title.replaceAll("[^a-zA-Z0-9가-힣]", "").toLowerCase();
    }
}