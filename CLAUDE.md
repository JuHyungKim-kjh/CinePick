# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

CinePick is a Spring Boot 4.1.0 / Java 17 / Gradle web application for browsing movies and getting genre/mood-based recommendations. It's a server-rendered MVC app (Thymeleaf templates), not a SPA — there is no separate frontend build.

## Commands

- Build: `.\gradlew.bat build`
- Run: `.\gradlew.bat bootRun`
- Run all tests: `.\gradlew.bat test`
- Run a single test class: `.\gradlew.bat test --tests "com.example.cinepick.CinePickApplicationTests"`
- No lint task is configured (no Checkstyle/Spotless/PMD plugin in `build.gradle`).

## Runtime requirements

- Requires a local MySQL instance reachable at `localhost:3306/CinePick`.
- **비밀 설정은 `application.yaml` 에 없다.** 그 파일에는 `${DB_PASSWORD}` 같은 플레이스홀더만 있고, 실제 값은 두 곳에서 온다 — 로컬은 `config/application-local.yaml`(git 제외, `.example` 파일이 템플릿), 배포는 환경변수(`DB_URL`·`DB_USERNAME`·`DB_PASSWORD`·`KOBIS_API_KEY`·`TMDB_API_KEY`·`CLAUDE_API_KEY`). `src/main/resources` 가 아니라 프로젝트 루트 `config/` 에 두는 이유는 **빌드할 때 jar 로 딸려 들어가지 않게** 하기 위해서다. 값이 비면 `RequiredSettingsCheck`(`META-INF/spring.factories` 로 등록)가 DB 접속 전에 무엇이 비었는지 알려주고 기동을 멈춘다 — 이 검사가 없으면 `Access denied for user '${DB_USERNAME}'` 같은 메시지와 200줄짜리 Hibernate 스택트레이스가 대신 나온다.
- `spring.jpa.hibernate.ddl-auto: update` — the schema is generated from JPA entities on startup; there are no migration scripts (no Flyway/Liquibase).
- The movie crawler uses Selenium + WebDriverManager, so a browser/driver must be available at runtime for `MovieCrawlerService`/`MovieCrawlerScheduler` to work.

## Architecture

Package layout under `src/main/java/com/example/cinepick/`:
- `controller/` — `@Controller` classes returning Thymeleaf view names. No REST API layer exists; `SurveyController` is the only exception, using `@ResponseBody` for a small JSON endpoint (`/survey/submit`, `/survey/dismiss`) consumed by `static/js/survey-prompt.js`.
- `security/` — Spring Security config (`SecurityConfig`) using session/form login, not the JWT classes (see stubs below).

**Auth**: Spring Security form login (`security/SecurityConfig.java`), `email` as the username parameter, BCrypt password hashing, CSRF disabled (noted in code as a temporary dev convenience). `/members/**`, `/cs/inquiry/**`, `/recommendations`, `/reservations/**`, and `/ratings/**` require authentication; everything else is open.

**Movie data flow**: `MovieService` merges two sources — TMDB API data (via `OpenApiService`, using `RestTemplate`) and multi-site scrape results from `MovieCrawlerService` (Selenium-based scraping of CGV, Lotte Cinema, Megabox for now-showing/upcoming titles and per-site detail links). `MovieCrawlerScheduler` caches results in memory (`volatile CrawlResult`). Titles from different sources are matched via `cleanTitle()` normalization before being upserted into `MovieRepository`.

**`cleanTitle()` 매칭은 반드시 실패한다는 전제로 쓴다.** 라이브뷰잉·재개봉처럼 예매사마다 표기가 다른 제목은 정규화해도 같은 작품으로 묶이지 않는다. 그런데 그 제목들을 TMDB에 물어보면 **같은 id가 돌아온다.** 제목으로 못 찾았다고 바로 `movieRepository.save()` 하면 `movie_cd` 유니크 제약(`UK52viw…`)에 걸려 예외가 컨트롤러까지 올라가고, **메인 화면 전체가 500**이 된다(실제로 발생). 그래서 TMDB에서 id를 받은 직후 `findByMovieCd()`로 한 번 더 확인하고 있으면 그 행을 재사용한다 — `seedIfEmpty()`가 원래 쓰던 방식과 같다. 같은 이유로 `getMoviesForMainPage()`는 화면 목록도 `movieCd` 기준으로 중복을 걷어내는데, **사이트별 링크 갱신(`updateSiteLinks`)은 그 중복 제거보다 먼저** 해야 한다. 표기가 다른 두 제목은 서로 다른 예매사에서 온 것이라, 뒤쪽을 그냥 건너뛰면 그 사이트의 예매 링크를 잃는다.

**시작 준비와 대기 화면**: 크롤링과 TMDB 초기 적재는 예전에 둘 다 `@PostConstruct`였다. 그러면 빈 생성 단계에서 30초 넘게 붙잡히는데 **Tomcat이 포트를 여는 건 그 다음**이라, 그동안 브라우저에는 "연결할 수 없음"만 떴다. 대기 화면을 띄우려 해도 띄울 서버가 없는 상태였다. 포트폴리오라 서버를 자주 껐다 켠다는 점에서 이 시간이 곧 첫인상이 된다.

그래서 준비 작업을 포트가 열린 **뒤로** 옮겼다. `StartupWarmupService`가 `ApplicationReadyEvent`에서 데몬 스레드를 띄워 ① `MovieService.seedIfEmpty()` ② `MovieCrawlerScheduler.refresh(listener)` 순으로 돌린다. 시동은 35초 → 11초로 줄고, 준비 중에도 서버가 응답한다.

**크롤링 결과 스냅샷**: 그런데 이 방식도 재시작할 때마다 30초를 다시 기다린다. 로컬에서는 서버를 켜 두면 그만이지만 **배포하면 배포·크래시·유휴 슬립마다 재시작이 일어나고**, 방문이 뜸한 포트폴리오에서는 그 대기가 예외가 아니라 기본 경험이 된다. 그래서 크롤링 결과를 `crawl_snapshot` 테이블에 통째로 남기고(`CrawlSnapshotStore`), 기동 직후 `restoreFromSnapshot()`으로 복원해 **크롤링을 기다리지 않고 문을 먼저 연다.** 새 크롤링은 그 뒤에 이어서 돌며 캐시와 스냅샷을 덮어쓴다. 실측으로 준비 시간이 **125초 → 1초**가 됐다.

- **행은 항상 하나다**(`CrawlSnapshot.SINGLETON_ID`). 컬럼을 나누지 않고 JSON 한 덩어리로 담는데, 이 데이터는 조건으로 조회하는 대상이 아니라 언제나 통째로 읽어 메모리에 올리는 스냅샷이라 정규화해도 얻는 것이 없다.
- **24시간이 지난 스냅샷은 복원하지 않는다**(`CrawlSnapshotStore.MAX_AGE`). 갱신 주기가 6시간이라 하루가 지났으면 상영표가 이미 바뀌었을 가능성이 높고, 그런 데이터로 문을 열면 **종영한 영화에 예매 버튼이 붙는다**. 그때는 기다리는 편이 낫다.
- **저장·복원 실패는 전부 삼킨다.** 크롤링 실패와 달리 스냅샷 실패는 서비스에 지장이 없다(다음 기동이 조금 느려질 뿐). 특히 구조가 바뀌어 역직렬화가 깨지면 그냥 새로 크롤링한다.
- `MovieCrawlerService.SiteLink`에 `@JsonCreator`가 붙은 이유가 이것이다 — 필드가 final 이라 Jackson 이 되돌릴 방법이 생성자뿐이다. **매퍼는 스프링 것을 주입받지 않고 직접 만든다**: Spring Boot 4 는 웹 계층에 Jackson 3(`tools.jackson`)를 써서 2.x `ObjectMapper` 빈이 아예 없고, 응답 규칙이 바뀔 때 저장 포맷까지 조용히 바뀌면 예전 스냅샷을 못 읽게 되기 때문이다.

준비 전 요청은 `StartupGateInterceptor`가 가로챈다. GET 화면 요청은 `/loading?next=<원래 주소>`로 302, 그 외(POST·fetch)는 503이다. `loading.js`가 `GET /system/status`를 1초마다 물어보다 `ready`가 되면 `next`로 이동한다.

**게이트는 두 경로에만 걸린다 — `/` 와 `/movies/detail/**`**(`WebConfig.addInterceptors`). 예전에는 `/**`를 전부 막고 로딩 화면·정적 자원만 뺐는데, 그러면 마이페이지나 고객지원처럼 **크롤링과 아무 상관 없는 화면까지** 30초를 기다렸다. 무엇을 기다리는지 알 수 없는 대기였다. 막을 곳만 적으면 기본값이 "통과"가 되고 제외 목록도 통째로 사라진다(`/system/**`를 빼먹어 무한 대기에 빠지는 위험 자체가 없어진다).

두 경로인 이유는 **크롤링 캐시가 없으면 화면이 조용히 틀리기 때문**이다. 메인은 `getMoviesForMainPage()`가 크롤링 제목을 순회하므로 목록이 통째로 비고, 영화 상세는 `determineMovieStatus()`가 아무것도 일치시키지 못해 **상영 중인 영화를 종영으로 표시하고 예매 버튼을 지운다**. 반면 `/movies` 목록은 DB만 읽고 고객지원·마이페이지·추천은 크롤링을 아예 쓰지 않는다.

> 새 화면이 `MovieCrawlerScheduler.getCachedResult()`에 의존하기 시작하면 **그 경로를 `WebConfig`에 더해야 한다.** 빠뜨려도 막히지 않고 빈 목록이나 틀린 상영 상태가 그대로 보이므로 알아채기 어렵다. `MovieService`의 두 메서드에 같은 경고를 주석으로 달아 뒀다.

주의할 점 둘:

- **`@Scheduled(initialDelay = REFRESH_INTERVAL, ...)`** — initialDelay를 주기와 같게 두지 않으면 스케줄러가 시작 직후에도 돌아 크롬이 두 개 뜨고 같은 일을 두 번 한다. 최초 1회는 워밍업이 전담한다. 참고로 이 주기 갱신은 `ready`를 건드리지 않는다 — 게이트가 다시 걸리는 것은 **서버가 재시작될 때뿐**이다(devtools 자동 재시작 포함).
- **`next`는 사용자가 바꿀 수 있는 값** — `SystemController.safeNext()`가 `/`로 시작하지 않거나 `//`로 시작하는 값을 걸러 열린 리다이렉트를 막는다.

크롤링이 실패해도 `ready`는 true로 올린다(사용자를 대기 화면에 가둘 수 없다). 대신 `degraded=true`로 "일부 정보를 불러오지 못했다"고 알린다. `loading.html`은 `layout.html`을 쓰지 않는다 — 공통 레이아웃이 참조하는 데이터가 아직 없을 때 뜨는 화면이라 얹으면 스스로 깨진다.

**Recommendation/survey flow**: `PreferenceSurvey` stores a member's per-axis preference values (`axisValues`, 0–100 for each of 8 genres + 5 moods) plus `surveyWeight` (how much those values count against actual viewing history). `RecommendController` reads the survey and computes percentage stats per genre/mood option for display on `recommendations.html`. See 취향 점수 모델 below.

**나의 취향 프로필 카드**(메인 오른쪽, `home.html` + `curation.css`): 선호 장르 TOP 3 막대 · 최근 관람&평점 3줄 · 자주 선택하는 무드 4칸으로 이뤄진 요약 카드다. 로그인 회원에게 큐레이션이 있을 때만 나온다. **퍼센트는 분석 페이지와 같은 `PreferenceService.buildCuration()`에서 가져온다** — 두 화면이 각자 계산하면 같은 회원에게 다른 숫자를 보여주게 되고, 사용자는 둘 중 무엇을 믿어야 할지 알 수 없게 된다. 요약이라 `HomeController.topRows()`가 0%인 축을 걸러내는 점만 다르다(좁은 카드에 빈 막대를 늘어놓지 않기 위해). 넣는 줄 수는 `PROFILE_*_SIZE` 상수가 정하며, 옆 큐레이션 배너와 높이를 맞추려고 잡은 값이다.

**메인 AI 큐레이션의 두 탭**(`home.html` + `curation-slider.html` + `home.js`): 「지금 상영 중」(기본)과 「전체 작품」이다. 전자는 크롤링 상영작 안에서만 고른다 — 전체에서만 추천하면 **극장에서 볼 수 없는 작품이 절반쯤 섞여** "지금 뭐 볼까"로 들어온 사용자에게 쓸모없는 목록이 된다.

- **채점은 한 번만 한다.** `RecommendService.curateForMain()`이 전체를 채점한 뒤 `MovieService.getNowShowingMovieCds()`가 준 집합으로 걸러 `MainCuration(all, nowShowing)` 두 벌을 함께 돌려준다. 목록별로 따로 부르면 전체 영화 순회와 `MoodTagger.tag()`가 통째로 두 번 돈다.
- **`matchPercent`는 두 목록 모두 전체 최고점으로 나눈다.** 상영작만 따로 정규화하면 후보가 적을 때 **평범한 영화가 100%로 표시된다.** 같은 기준을 써야 두 탭의 숫자가 비교 가능하고, 상영작 탭 최고값이 100%에 못 미치는 편이 사실에 가깝다.
- **`getNowShowingMovieCds()`는 읽기 전용**이다. `getMoviesForMainPage()`와 달리 TMDB를 부르지도 저장하지도 않는다 — 이미 채점한 영화를 거르는 용도라 DB에 없는 영화는 어차피 점수가 없다. 다만 **크롤링 캐시를 읽는 소비자가 하나 더 늘었다**(`/`는 이미 `WebConfig` 게이트 대상이라 추가 작업은 없다).
- **탭 전환은 화면에서만 한다.** 두 목록을 이미 다 내려보냈다. 다만 **숨은 채로 만들어진 Swiper는 폭을 0으로 재므로** 탭을 열 때 `swiper.update()`를 부른다(`home.js`). 그리고 두 슬라이더 모두 `curationSwiper` 클래스를 유지하고 `id`로만 구분한다 — 그 클래스에 **화살표가 잘리지 않게 하는 패딩 보정**이 들어 있다.
- 상영작 목록은 비어 있을 수 있다(크롤링 준비 전이거나 취향과 겹치지 않을 때). 이때는 안내 문구와 전체 탭으로 가는 버튼만 보여준다. `HomeController`가 **빈 목록이라도 속성을 반드시 넣는다** — `#lists.isEmpty(null)`은 예외를 낸다.

**취향 점수 모델** (`PreferenceService.merge()`): 선호 점수는 **설문과 관람 이력을 각각 정규화한 뒤 사용자가 정한 지분으로 섞은 값**이다. 단순 합산이 아니다.

```
최종 = SCORE_SCALE(100) × ( w × (설문값/설문최고) + (1−w) × (이력점수/이력최고절댓값) )
w = PreferenceSurvey.surveyWeight / 100        (화면의 '판단 비중' 슬라이더)
```

| 출처 | 값 |
|---|---|
| 설문 | 축(장르 8 + 분위기 5)마다 사용자가 슬라이더로 정한 **0~100** (`PreferenceSurvey.axisValues`) |
| 관람 이력 | 영화 1편당 장르 예산 2.0 + 분위기 예산 2.0에 평점 배율 적용. 편수만큼 누적 |

출처는 이 둘뿐이다. **검색 이력은 쓰지 않기로 했다** — 아래 `PreferenceHistory` 항목 참조.

**왜 정규화인가**: 예전에는 설문이 고정 점수(순위 9/6/3, 한 차원 18점)라 이력이 쌓일수록 저절로 밀렸다. 30편쯤 보면 설문 지분이 20%까지 떨어지는데 **되돌릴 방법이 없었다.** 각 출처를 자기 최고점으로 나눠 0~1로 맞춘 뒤 w로 섞으면 편수가 아무리 늘어도 지분은 w 그대로다. 이력 정규화만 **절댓값** 기준인데, 기피(음수)가 큰 축 하나 때문에 전체 부호가 뒤집히지 않게 하기 위해서다.

**왜 축 슬라이더인가**: 순위 3개만 받던 때는 8개 장르 중 5개에 대해 사용자가 아무 의견도 낼 수 없었다. "예매로 쌓인 액션 점수를 낮추고 싶다"를 표현할 수단이 없었다(고르지 않음 = 0점일 뿐 "싫다"가 아니다). 이제 13개 축을 전부 조절한다.

주의할 점 셋:
- **`SCORE_SCALE`을 빼면 안 된다** — 정규화 결과는 −1~1이라 `genreScores()`가 정수로 반올림할 때 전부 0이나 ±1로 뭉개진다. 100을 곱해 −100~100으로 펴둔다.
- **순위 컬럼(`genreRank1~3`)은 이제 입력이 아니라 파생값이다** — `applyAxisValues()`가 축 값 상위 3개에서 만들어낸다. 배너의 선호 장르 TOP, 순위 보기 토글, AI 인사이트 문구가 이 값을 쓴다.
- **`axisValues`가 비어 있으면 순위에서 값을 만들어낸다**(`PreferenceSurvey.axisValue()`) — 슬라이더 도입 전에 설문을 마친 회원 데이터를 마이그레이션 없이 그대로 쓰기 위한 장치다. 100/70/40으로 환산된다.

설문 팝업은 여전히 **3개 순서대로 고르기**다(가입 직후에 슬라이더 13개를 들이밀 수 없다). 고른 3개가 100/70/40으로 자리를 잡고 나머지는 0에서 시작한다. `/survey/submit` 한 엔드포인트가 두 모양을 모두 받는다 — 팝업은 `genres`/`moods`, 수정 화면은 `axisNames`/`axisValues` 평행 배열(파라미터 이름에 한글이 들어가지 않도록).

**판단 비중 막대**(`buildWeights()`)는 설문 지분에 w를 그대로 쓰고, 남은 (100−w)를 예매·평점이 각자의 영향력 크기로 나눈다. 다만 한쪽이 아예 없으면 사실이 우선이라 이력이 없는 회원에게는 설문 100%로 표시한다.

**평점 배율**: 1점 −0.5 · 2점 0 · 3점 +1.0 · 4점 +1.5 · 5점 +2.0 · **미평가 +1.0**. 1.0이 "예매만 한 상태"의 기준선이라 3점과 미평가가 같고, 1점은 예매 기여까지 상쇄해 **취향을 깎아내린다.**

주의할 설계 결정 세 가지:
- **정액 배분** — 영화 한 편의 예산을 축 개수로 나눈다. 축마다 만점을 주면 장르·분위기가 많이 붙은 영화(예: 7축)가 한 편만으로 설문 총점을 넘어선다.
- **하한 없음(기피 상태)** — `merge()`는 음수를 그대로 둔다. 0으로 자르면 1점과 2점을 준 장르가 구분되지 않고 "얼마나 싫어했는지"가 사라진다. 대신 음수를 다루는 책임이 소비자 쪽으로 넘어간다 — 아래 표 참조.
- **내부는 실수, 외부는 정수** — 예산을 축으로 나누므로 계산은 `double`이고, `genreScores()`/`moodScores()`가 마지막에 한 번만 반올림해 넘긴다.

**음수 점수를 쓰는 두 소비자** (점수는 컬럼이 아니라 `reservation`·`movie_rating` 행에서 매 요청 재계산되므로, 누적은 원본 이력에 남고 하한 없이 이어진다):

| 소비자 | 처리 |
|---|---|
| 분석 화면 (`toRows`) | 막대는 길이가 곧 값이라 음수를 그릴 수 없다. `toPercent()`가 0으로 자르고, 대신 `PrefRow.avoided`(음수 여부)와 `points`(실제 점수)를 따로 내려보내 **'기피' 배지**로 구분한다. `is-empty`(이력 없음)와 `is-avoided`(싫어함)는 CSS 색으로 갈린다 |
| 추천 (`RecommendService.score`) | 음수 선호도를 **감점**으로 쓴다. 다만 `matchedGenres`/`matchedMoods`에는 넣지 않는다 — 이 집합은 대표 장르(`leadGenre`) 선정용이라 기피 장르가 대표가 되면 안 된다. 합계가 음수로 내려간 영화는 `total > 0` 필터에서 빠진다 |

회복은 상쇄식이다. 1점이 축당 −0.5, 5점이 +2.0이라 **떨어지는 것보다 4배 빠르게 올라온다.** 다만 0을 넘기 전까지는 화면이 계속 '기피'로 보인다.

설문만 상한이 있으므로 **약 6편을 보면 관람 이력이 설문과 대등해진다.** 이 비중은 `buildWeights()`가 **절댓값(영향력) 기준**으로 환산해 분석 페이지 막대로 보여주고, 순감소로 작용하는 출처에는 ▼를 붙인다. 장르는 `canonicalGenre()`로 묶은 뒤 중복 제거하고, 분위기는 `MoodTagger.PRESENCE_THRESHOLD`(50) 이상인 축만 쓴다.

**예매·평점 흐름**: CinePick은 예매를 처리하지 못하고 각 예매사로 링크만 중계한다. 그래서 상세페이지에서 예매 링크를 누르면 `ReservationService.recordClick()`이 `PENDING` 이력만 남기고, 실제 예매 여부는 팝업으로 사용자에게 직접 받아 `CONFIRMED`/`CANCELED`로 확정한다.

팝업이 뜨는 경로는 두 가지다. ① 화면을 새로 그릴 때 — `PendingReservationAdvice`(`@ControllerAdvice`)가 모든 화면 모델에 미확인 건을 넣고 `layout.html`이 렌더하므로 페이지별 컨트롤러는 관여하지 않는다. ② 예매 사이트에 다녀와 탭으로 돌아왔을 때 — 예매 사이트는 새 탭에서 열려 CinePick 화면이 그대로 살아 있으므로, `reservation-popup.js`가 `visibilitychange`/`focus`를 감지해 `GET /reservations/pending`으로 확인한다. 이 경로를 위해 **미확인 건이 없어도 로그인 사용자에게는 팝업 껍데기를 숨긴 채 렌더**한다(`reservationPopupEnabled`). 복귀 경로에만 `ReservationService.RETURN_GRACE`(1분) 유예가 걸려 있어 예매 중인 사용자를 붙잡지 않는다.

**평점**은 확정된 예매가 있는 영화에만 남길 수 있고, 횟수 제한 없이 언제든 고칠 수 있다. 다만 같은 영화를 여러 번 예매해도 평점은 **회원+영화당 한 행**만 유지된다 — `MovieRating`의 unique 제약과 `RatingService.findRatingList()`가 확정 예매를 영화 단위로 접는 로직이 그 역할을 한다.

**고객지원(`/cs`)**: 공지사항 · FAQ · 1:1 상담 세 갈래가 **탭 하나로 묶인 한 페이지**다. 상단 메뉴의 고객지원은 `/cs`로 들어와 `/cs/notices`로 넘어간다. 세 화면은 `cs/tabs.html` 프래그먼트를 공유하고, 컨트롤러가 `activeTab`으로 어느 탭인지 알려준다 — 화면마다 제목을 따로 쓰면 여백과 문구가 조금씩 어긋나 다른 사이트처럼 보인다.

공개 범위가 갈린다. 공지사항·FAQ는 누구나 보고, **1:1 상담만 로그인이 필요**하다(`SecurityConfig`의 `/cs/inquiry/**`). 그래도 탭 자체는 감추지 않는다 — 상담 창구가 있다는 사실은 로그인 전에도 알 수 있어야 한다.

- **공지·FAQ는 `CsService`, 1:1 상담은 `InquiryService`로 나눠 뒀다.** 앞의 둘은 누구에게나 같은 내용이고 문의는 회원 개인의 글이라, 한 서비스에 섞으면 "이 메서드는 회원 확인이 필요한가"를 매번 되짚어야 한다. `InquiryRepository`에는 아예 전체 조회 메서드를 두지 않고 조회 조건에 회원을 박아 뒀다(`findByMemberIdOrderByCreatedAtDesc`).
- **초기 글은 `CsService.seedIfEmpty()`가 넣고 `StartupWarmupService`가 호출한다.** 공지 작성 화면이 없어 이것이 유일한 입력 경로다. 담긴 내용은 예시 문구가 아니라 실제 동작 설명이다 — 특히 "예매는 예매사에서 한다"는 이 서비스의 가장 큰 오해 지점이라 결제 후에 알게 되면 늦는다. 두 게시판을 따로 검사하므로 한쪽만 지우고 재시작해도 그쪽만 복구된다.
- **FAQ 작성은 운영자 전용**(`/cs/faqs/new`, `POST /cs/faqs` = `hasRole("ADMIN")`). 이 때문에 `CustomUserDetailsService`가 바뀌었다 — 예전에는 `.roles("USER")`를 못박아 `Member.role` 컬럼이 무시됐고 운영자 계정을 만들 방법이 없었다. 지금은 `.authorities(member.getRole())`을 쓴다. `roles(...)`가 아닌 이유는 그쪽이 `ROLE_` 접두사를 자동으로 붙여 `ROLE_ROLE_USER`가 되기 때문이다.
- **FAQ 분류 거르개는 서버를 다시 다녀오지 않는다**(`cs-faq.js`). 전부 내려보내고 화면에서 감춘다 — 분류마다 요청하면 열어 둔 답변이 닫히고 스크롤이 튀는데, 몇십 건 규모라 그럴 이유가 없다. 아코디언은 하나를 열 때 나머지를 닫지 않는다(여러 답을 나란히 읽는 경우가 많다). 같은 스크립트를 1:1 상담의 내 문의 내역이 그대로 재사용한다.
- **문의 답변은 `/cs/admin/inquiries` 한 화면에서 처리한다.** 목록·문의 내용·답변 칸이 한 곳에 있고 아코디언으로 펼친다 — 답변은 대개 여러 건을 연달아 처리하는 일이라, 건마다 상세로 들어갔다 목록으로 돌아오면 같은 왕복을 반복하게 된다. 이미 답변한 건은 기존 답변이 폼에 채워져 그대로 고칠 수 있고, **고칠 때 `answeredAt`도 갱신한다** — 회원이 보는 것은 "이 답이 언제 쓰였는가"이지 "처음 답이 언제 달렸는가"가 아니다. 답변을 지우는 기능은 두지 않았다(받은 답이 말없이 사라지면 회원은 무슨 일이 있었는지 알 길이 없다).
- **`InquiryRepository.findAllForAdmin()`은 이름으로 스스로를 방어한다.** 이 저장소는 원래 전체 조회 메서드를 두지 않았다 — 남의 문의가 섞여 나오는 사고를 만들 수 없게 하려고 조회 조건에 회원을 박아 뒀다. 답변 화면 때문에 전체 조회가 한 번은 필요해졌지만, 파생 쿼리로 슬쩍 여는 대신 `ForAdmin`을 이름에 박았다(회원 화면을 만들다 자동완성에서 잘못 골라도 알아챌 수 있어야 한다). 정렬은 **`status`가 아니라 `answeredAt is null`** 기준이다 — enum이 문자열로 저장돼 사전순이 `ANSWERED < WAITING`이라, status로 정렬하면 답변한 건이 위로 올라온다. `join fetch i.member`는 목록에 작성자 닉네임을 함께 보여주기 때문이다.
- **접수 실패는 예외가 아니라 안내 문구다.** `InquiryService.create()`는 실패 사유를 문자열로 돌려주고 컨트롤러가 같은 화면에 `form`을 그대로 얹어 다시 그린다. redirect로 돌리면 길게 쓴 글이 사라진다. 성공했을 때만 redirect 한다(새로고침 중복 접수 방지).
- **관리 URL은 `/cs/admin/**` 한 갈래로 모았다.** 처음에는 `/cs/faqs/new`처럼 조회 URL 사이에 끼워 두고 `SecurityConfig`에서 경로마다 막았는데, 공지 작성·수정·삭제와 FAQ 수정·삭제까지 늘면 규칙이 열 줄 가까이 되고 **그중 하나만 빠뜨리면 그대로 구멍**이 된다. 지금은 `.requestMatchers("/cs/admin/**").hasRole("ADMIN")` 한 줄이고, 관리 기능을 더해도 이 줄을 고칠 일이 없다. 컨트롤러도 `CsController`(조회)와 `CsAdminController`(편집)로 갈라 둔다.
- **서비스도 `CsService`(읽기)와 `CsAdminService`(쓰기)로 나눴다.** 한 서비스에 섞으면 "이 메서드는 관리자만 부를 수 있는가"를 메서드마다 되짚어야 하는데, 그런 판단은 언젠가 한 번 틀린다. `CsAdminService`에 있는 것은 전부 관리자 전용이라 되짚을 일이 없다. 다만 **수정 폼을 채우는 조회는 `CsService` 쪽**이다(`getNoticeForEdit`·`getFaq`) — 읽기이기 때문이고, `getNoticeForEdit`은 `getNotice`와 달리 **조회수를 올리지 않는다**(운영자가 고치러 들어간 것을 독자가 읽은 것으로 세면 숫자가 사실과 멀어진다).
- **작성 폼과 수정 폼은 한 템플릿이다**(`notice-form.html`, `faq-form.html`). 받는 값이 완전히 같아 나눌 이유가 없고, 나누면 필드를 하나 더할 때 두 곳을 고쳐야 한다. 어느 쪽인지는 `editing` 플래그가 정하고 값은 **`form`(저장 실패로 되돌아온 입력값) → 도메인 객체(DB의 현재 값) 순**으로 본다. 실패했는데 방금 쓴 긴 글이 사라지면 안 되기 때문에 저장 실패는 redirect 가 아니라 같은 화면 재렌더다(성공했을 때만 redirect — 새로고침 중복 등록 방지).
- **삭제는 전부 POST 폼이다.** GET 링크로 두면 주소만 알아도 지워지고, 브라우저나 크롤러가 링크를 미리 열어보는 것만으로 글이 사라진다. 다만 **CSRF가 꺼져 있어 POST라고 안전한 것은 아니다** — CSRF를 켤 때 이 폼들도 함께 봐야 한다. 되돌릴 수 없는 동작이라 `cs-admin.js`가 제출 직전에 한 번 되묻는다(버튼이 아니라 폼의 submit 을 가로챈다 — 엔터로 제출하는 경로가 남기 때문).
- **운영자 계정은 `Member.role` 컬럼으로만 만든다.** 가입 화면은 항상 `ROLE_USER`로 넣으므로, 관리자로 쓰려면 가입 후 그 행의 role 을 `ROLE_ADMIN`으로 바꾼다. 권한 부여 화면을 두지 않은 이유는 그 화면 자체가 권한 상승 통로가 되기 때문이다.
- 메인 화면 하단 고객지원 구역과 footer 링크도 이 게시판을 가리킨다. 메인에 적힌 제목을 눌렀는데 다른 글이 열리면 그때부터 그 구역을 아무도 믿지 않게 되므로, 자리표시자 문구를 두지 않고 `HomeController`가 `getRecentNotices()`/`getFaqPreview()`로 실제 데이터를 넣는다.

**Unimplemented stubs**: a number of classes/files already exist in the tree but only as empty placeholders (package declaration + empty class body) and should not be assumed to work. Package root stays `com.example.cinepick` — do not rename it. See "향후 구현 계획" below for what each stub is meant to become.

## 향후 구현 계획 (스텁 클래스 목적)

아래 클래스들은 현재 빈 껍데기(package 선언 + 빈 클래스 본문)로만 존재하며, 최종적으로 담당할 역할은 다음과 같다. 새 기능을 구현할 때 이 계획에 맞춰 작성한다. 패키지 루트는 `com.example.cinepick`을 그대로 사용한다(변경하지 않음).

**controller**
- `RecommendController` — 현재는 설문 기반 통계만 계산. 최종적으로 AI 맞춤 추천 결과와 AI 큐레이션(취향 분석 결과) 조회까지 담당

**domain / repository** (아래 도메인은 엔티티·레포지토리 모두 스텁)
- `PreferenceHistory` — 검색·상세조회 이력. **단, 취향 점수에는 쓰지 않는다.** 영화 검색은 `MovieService.getCombinedMovieList()` → `findByTitleContaining()`, 즉 **제목 검색**이라 검색어에서 장르를 알아낼 방법이 없다. 결과에 잡힌 영화의 장르를 대신 쓰면 엉뚱한 작품이 검색 범위에 걸릴 때 그 노이즈가 그대로 취향에 실린다. 근거가 약한 신호를 넣느니 빼는 편이 낫다고 판단해 점수 파이프라인에서 검색 출처를 아예 제거했다(2026-08-13). 이 도메인을 살린다면 "최근 본 영화" 같은 **점수와 무관한 표시 용도**로 쓴다
- 참고: `Reservation`, `MovieRating`, `Notice`, `Faq`, `Inquiry`는 구현 완료(스텁 아님)

**dto** — 아래 DTO는 파일만 생성되어 있고 필드/빌더 구현이 비어 있다:
- request: `LoginReqDto`(이메일·비밀번호), `SearchReqDto`(검색 키워드)
- response: `BoxOfficeResDto`(메인 화면 박스오피스 순위), `InitialStatusResDto`(첫 방문/신규 가입 시 팝업 노출 여부), `MemberInfoResDto`(마이페이지 내 정보), `RelayLinkResDto`(외부 예매 사이트 중계 URL)
- 참고: `FaqReqDto`, `NoticeReqDto`, `InquiryReqDto`, `FaqResDto`, `NoticeResDto`, `InquiryResDto`, `SignUpReqDto`, `SurveyReqDto`, `MemberUpdateReqDto`, `RatingReqDto`, `KobisMovieDto`, `MovieListResDto`, `MovieDetailResDto`, `AiRecommendResDto`, `CurationResDto`, `RatingResDto`, `ReservationResDto`는 이미 구현되어 있으므로 스텁이 아니다

**security**
- `JwtTokenProvider` — 로그인 성공 시 JWT 토큰 발급
- `JwtAuthenticationFilter` — API 요청마다 JWT 토큰 유효성 검증
- 주의: 현재 실제로 동작하는 인증 방식은 `SecurityConfig`의 세션/폼 로그인이며, JWT 전환은 계획 단계다. 두 방식을 혼동하지 말 것

**service**
- `LlmApiService` — ChatGPT/Gemini 등 외부 AI 모델과 통신
- `MemberService` — 마이페이지 회원정보 수정. 당초 예매/평점 내역 관리까지 맡길 계획이었으나, 파일 분할 규칙에 맞춰 도메인별로 `ReservationService`·`RatingService`(구현 완료)에 두었다
- `CsService`·`CsAdminService`·`InquiryService` — 구현 완료(스텁 아님). 고객지원 항목 참조
- `PreferenceService` — 구현 완료(스텁 아님). 설문 저장/수정 + 취향 점수 합산을 담당하며, 출처는 설문·예매·평점 셋으로 확정됐다
- `RecommendService` — 수집된 데이터를 바탕으로 AI 추천을 지시하고 결과를 화면에 전달하는 조정자 역할
- `UserPersonaAnalyzer` — 명시적(설문) + 묵시적(활동) 취향 데이터를 분석해 AI에 보낼 프롬프트 텍스트로 가공

## 작업 규칙

- 작업 결과와 설명은 한글로 출력한다.
- CSS와 JavaScript는 필요할 경우 HTML 파일에 직접 작성하지 않고, 별도의 `.css`, `.js` 파일로 분리하여 작성한다.
- 하나의 파일에 과도하게 많은 내용을 담지 않고, 유지보수가 쉽도록 적절한 단위로 파일을 분할하여 작성한다.
