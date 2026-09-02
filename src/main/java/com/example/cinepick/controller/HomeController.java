package com.example.cinepick.controller;

import com.example.cinepick.domain.Member;
import com.example.cinepick.controller.SurveyController;
import com.example.cinepick.dto.response.AiRecommendResDto;
import com.example.cinepick.dto.response.CurationResDto;
import com.example.cinepick.dto.response.MovieListResDto;
import com.example.cinepick.dto.response.RatingResDto;
import com.example.cinepick.repository.MemberRepository;
import com.example.cinepick.repository.PreferenceSurveyRepository;
import com.example.cinepick.service.BoxOfficeService;
import com.example.cinepick.service.CsService;
import com.example.cinepick.service.MovieService;
import com.example.cinepick.service.PreferenceService;
import com.example.cinepick.service.RatingService;
import com.example.cinepick.service.RecommendService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Comparator;
import java.util.List;

@Controller
@RequiredArgsConstructor
public class HomeController {

    private final MovieService movieService;
    private final MemberRepository memberRepository;
    private final PreferenceSurveyRepository preferenceSurveyRepository;
    private final PreferenceService preferenceService;
    private final RecommendService recommendService;
    private final BoxOfficeService boxOfficeService;
    private final CsService csService;
    private final RatingService ratingService;

    // 게스트에게 이번 세션에 설문 팝업을 이미 띄웠는지 표시하는 세션 키
    private static final String SURVEY_PROMPT_SHOWN = "surveyPromptShown";

    // 메인페이지 큐레이션 / 박스오피스에 보여줄 작품 수 (슬라이드라 넉넉히 담는다)
    private static final int CURATION_SIZE = 12;
    private static final int BOX_OFFICE_SIZE = 12;

    // 취향 프로필 카드에 올릴 줄 수. 옆의 큐레이션 배너와 높이를 맞추려고 잡은 값이라
    // 늘리면 카드가 배너보다 길어져 두 칸이 어긋난다
    private static final int PROFILE_GENRE_SIZE = 3;
    private static final int PROFILE_MOOD_SIZE = 4;
    private static final int PROFILE_WATCHED_SIZE = 3;

    // 메인 하단 고객지원 구역의 미리보기 줄 수. 공지와 FAQ 두 칸의 높이를 맞추려고 같은 값을 쓴다
    private static final int CS_PREVIEW_SIZE = 3;

    @GetMapping("/")
    public String home(@RequestParam(defaultValue = "now") String tab,
                       @CookieValue(name = "surveyPromptDismissed", required = false) String dismissedCookie,
                       Authentication authentication,
                       HttpServletRequest request,
                       Model model) {

        List<MovieListResDto> movies = movieService.getMoviesForMainPage(tab);
        model.addAttribute("movies", movies);
        model.addAttribute("currentTab", tab);

        boolean isLoggedIn = authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
        model.addAttribute("isLoggedIn", isLoggedIn);

        boolean showSurveyPrompt = shouldShowSurveyPrompt(isLoggedIn, dismissedCookie, authentication, request);

        // 예매 확인 팝업과 설문 팝업이 한 화면에서 겹치지 않게 한다.
        // 예매 확인은 건너뛸 수 없는 응답이라 그쪽을 먼저 처리하고 설문은 다음 방문으로 미룬다
        if (model.asMap().containsKey("pendingReservation")) {
            showSurveyPrompt = false;
        }

        // 게스트에게 한 번 띄웠으면 이번 세션 동안은 다시 띄우지 않는다 (첫 방문에만 노출)
        if (showSurveyPrompt && !isLoggedIn) {
            request.getSession().setAttribute(SURVEY_PROMPT_SHOWN, Boolean.TRUE);
        }

        model.addAttribute("showSurveyPrompt", showSurveyPrompt);
        model.addAttribute("genreOptions", SurveyController.GENRE_OPTIONS);
        model.addAttribute("moodOptions", SurveyController.MOOD_OPTIONS);

        addCuration(isLoggedIn, authentication, model);

        // 화면 맨 아래 고객지원 구역. 실제 게시판에서 가져온다 —
        // 메인에 적힌 제목을 눌렀는데 다른 글이 나오면 그때부터 이 구역을 아무도 믿지 않는다
        model.addAttribute("noticePreview", csService.getRecentNotices(CS_PREVIEW_SIZE));
        model.addAttribute("faqPreview", csService.getFaqPreview(CS_PREVIEW_SIZE));

        return "home";
    }

    /**
     * 메인페이지 오른쪽 큰 영역.
     * 취향 정보가 있으면 AI 큐레이션과 취향 프로필을, 없으면 박스오피스 인기 순위를 보여준다.
     * 설문 유도는 팝업이 담당하므로 화면에 별도 버튼을 두지 않는다.
     */
    private void addCuration(boolean isLoggedIn, Authentication authentication, Model model) {
        List<AiRecommendResDto> curationList = List.of();

        if (isLoggedIn) {
            Member member = memberRepository.findByEmail(authentication.getName()).orElse(null);
            if (member != null) {
                RecommendService.MainCuration curation = recommendService.curateForMain(
                        member, CURATION_SIZE, movieService.getNowShowingMovieCds());
                curationList = curation.all();

                if (!curationList.isEmpty()) {
                    model.addAttribute("curationList", curationList);
                    // 비어 있을 수 있다 (크롤링 준비 전이거나 상영작이 취향과 겹치지 않을 때).
                    // 그래도 속성은 넣는다 — 템플릿의 #lists.isEmpty(null) 은 예외를 낸다
                    model.addAttribute("nowShowingCurationList", curation.nowShowing());
                    addTasteProfile(member, model);
                }
            }
        }

        // 추천할 취향 정보가 없는 방문자에게는 박스오피스를 대신 보여준다
        if (curationList.isEmpty()) {
            model.addAttribute("boxOfficeList", boxOfficeService.getBoxOffice(BOX_OFFICE_SIZE));
        }
    }

    /**
     * 큐레이션 오른쪽에 붙는 '나의 취향 프로필' 카드.
     * 계산은 취향 분석 페이지와 <b>같은 {@code buildCuration()}</b> 을 쓴다 — 두 화면이 각자
     * 계산하면 같은 회원에게 다른 퍼센트를 보여주게 되고, 사용자는 무엇을 믿어야 할지 알 수 없다.
     */
    private void addTasteProfile(Member member, Model model) {
        CurationResDto curation = preferenceService.buildCuration(
                member, SurveyController.GENRE_OPTIONS, SurveyController.MOOD_OPTIONS);

        // 설문 전이면 buildCuration 이 null 이다. 이때도 속성을 반드시 넣어야 한다 —
        // 템플릿의 #lists.isEmpty(null) 은 값이 없다고 판단하는 대신 예외를 낸다
        model.addAttribute("profileGenres",
                curation == null ? List.of() : topRows(curation.getGenreRows(), PROFILE_GENRE_SIZE));
        model.addAttribute("profileMoods",
                curation == null ? List.of() : topRows(curation.getMoodRows(), PROFILE_MOOD_SIZE));

        // 확정한 예매가 있는 영화를 최근 순으로 (평점을 아직 안 남긴 영화도 포함)
        List<RatingResDto> watched = ratingService.findRatingList(member.getId());
        model.addAttribute("profileWatched", watched.stream().limit(PROFILE_WATCHED_SIZE).toList());
    }

    /**
     * 프로필 카드에 올릴 상위 몇 줄. 0% 인 줄은 뺀다(좁은 카드에 빈 막대를 늘어놓지 않으려고).
     * 분위기 행은 레이더 축 순서를 지키느라 정렬돼 있지 않아 여기서 한 번 더 정렬한다.
     */
    private List<CurationResDto.PrefRow> topRows(List<CurationResDto.PrefRow> rows, int size) {
        if (rows == null) return List.of();

        return rows.stream()
                .filter(row -> row.getPercent() > 0)
                .sorted(Comparator.comparingInt(CurationResDto.PrefRow::getPercent).reversed())
                .limit(size)
                .toList();
    }

    /**
     * 설문 팝업 노출 조건
     * - 로그인 사용자: 설문 이력과 건너뛰기 이력이 양쪽 다 없을 때
     * - 게스트: 설문을 마친 적이 없고(쿠키), 이번 세션에서 아직 띄운 적이 없을 때
     */
    private boolean shouldShowSurveyPrompt(boolean isLoggedIn, String dismissedCookie,
                                           Authentication authentication, HttpServletRequest request) {
        if (isLoggedIn) {
            Member member = memberRepository.findByEmail(authentication.getName()).orElse(null);
            if (member == null) return false;

            boolean hasSurvey = preferenceSurveyRepository.existsByMemberId(member.getId());
            return !hasSurvey && !member.isSurveyPromptDismissed();
        }

        // 게스트가 설문을 마치면 SurveyController 가 이 쿠키를 남긴다. 그 뒤로는 묻지 않는다
        if (dismissedCookie != null) return false;

        // 세션은 여기서 처음 필요해진다. 쿠키로 이미 걸러진 방문자에게는 만들지 않도록 조회만 한다
        HttpSession session = request.getSession(false);
        return session == null || session.getAttribute(SURVEY_PROMPT_SHOWN) == null;
    }
}