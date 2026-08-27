package com.example.cinepick.controller;

import com.example.cinepick.domain.Member;
import com.example.cinepick.dto.request.SurveyReqDto;
import com.example.cinepick.repository.MemberRepository;
import com.example.cinepick.service.PreferenceService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Controller
@RequiredArgsConstructor
public class SurveyController {

    private final MemberRepository memberRepository;
    private final PreferenceService preferenceService;

    // HomeController 도 같은 목록을 재사용한다 (팝업/단독 페이지 목록 불일치 방지)
    public static final List<String> GENRE_OPTIONS = List.of(
            "액션", "드라마", "코미디", "스릴러", "호러", "로맨스", "SF", "판타지"
    );
    public static final List<String> MOOD_OPTIONS = List.of(
            "유쾌", "긴장", "슬픔", "카타르시스", "신비"
    );

    // 설문 팝업과 취향 수정 화면이 모두 이 엔드포인트로 보낸다.
    // 수정 화면만 슬라이더 값을 추가로 싣고, 없으면 기존 설정이 유지된다
    @PostMapping("/survey/submit")
    @ResponseBody
    public ResponseEntity<String> submitSurveyAjax(SurveyReqDto surveyReqDto,
                                                   Authentication authentication,
                                                   HttpServletResponse response) {
        try {
            boolean saved = saveSurvey(surveyReqDto, authentication, response);
            if (!saved) {
                return ResponseEntity.badRequest().body("장르/분위기를 각각 3개씩 선택해주세요.");
            }
            return ResponseEntity.ok("저장되었습니다.");
        } catch (Exception e) {
            // 화면에는 짧은 메시지만, 원인은 서버 콘솔에 남긴다
            System.out.println("[/survey/submit] 저장 중 예외 발생: " + e.getClass().getSimpleName());
            e.printStackTrace();
            return ResponseEntity.status(500).body("서버 오류: " + e.getClass().getSimpleName() + " - " + e.getMessage());
        }
    }

    @PostMapping("/survey/dismiss")
    @ResponseBody
    public void dismissSurveyPrompt(Authentication authentication) {
        if (!isLoggedIn(authentication)) return;

        memberRepository.findByEmail(authentication.getName()).ifPresent(member -> {
            member.setSurveyPromptDismissed(true);
            memberRepository.save(member);
        });
    }

    // 로그인 회원은 DB, 비로그인 방문자는 쿠키에 저장
    private boolean saveSurvey(SurveyReqDto surveyReqDto, Authentication authentication, HttpServletResponse response) {
        if (!isLoggedIn(authentication)) {
            return saveToCookie(surveyReqDto, response);
        }

        Member member = memberRepository.findByEmail(authentication.getName()).orElse(null);
        if (member == null) return false; // 회원을 못 찾으면 성공 응답이 나가지 않도록 명확히 실패 처리

        boolean saved = preferenceService.saveSurvey(surveyReqDto, member);
        if (!saved) return false;

        member.setSurveyPromptDismissed(true);
        memberRepository.save(member);
        return true;
    }

    private boolean saveToCookie(SurveyReqDto surveyReqDto, HttpServletResponse response) {
        List<String> genres = surveyReqDto.getGenres();
        List<String> moods = surveyReqDto.getMoods();

        if (genres == null || genres.size() != 3 || moods == null || moods.size() != 3) {
            return false;
        }

        setCookie(response, "surveyGenres", encode(String.join(",", genres)), 365);
        setCookie(response, "surveyMoods", encode(String.join(",", moods)), 365);
        setCookie(response, "surveyPromptDismissed", "1", 365);
        return true;
    }

    private boolean isLoggedIn(Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }

    private void setCookie(HttpServletResponse response, String name, String value, int days) {
        Cookie cookie = new Cookie(name, value);
        cookie.setPath("/");
        cookie.setMaxAge(days * 24 * 60 * 60);
        response.addCookie(cookie);
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
