package com.example.cinepick.controller;

import com.example.cinepick.domain.Member;
import com.example.cinepick.domain.PreferenceSurvey;
import com.example.cinepick.dto.response.CurationResDto;
import com.example.cinepick.repository.MemberRepository;
import com.example.cinepick.repository.PreferenceSurveyRepository;
import com.example.cinepick.service.PreferenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class RecommendController {

    private final MemberRepository memberRepository;
    private final PreferenceSurveyRepository preferenceSurveyRepository;
    private final PreferenceService preferenceService;

    @GetMapping("/recommendations")
    public String recommendations(Authentication authentication, Model model) {
        Member member = memberRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new IllegalStateException("회원 정보를 찾을 수 없습니다."));

        // 설문 완료 여부 분기와 취향 수정 폼의 초기 선택값에 필요
        PreferenceSurvey survey = preferenceSurveyRepository.findByMemberId(member.getId()).orElse(null);

        // 점수 산출과 화면 값 구성은 전부 PreferenceService 담당
        CurationResDto curation = preferenceService.buildCuration(
                member, SurveyController.GENRE_OPTIONS, SurveyController.MOOD_OPTIONS);

        model.addAttribute("member", member);
        model.addAttribute("survey", survey);
        model.addAttribute("curation", curation);
        model.addAttribute("isLoggedIn", true);
        model.addAttribute("genreOptions", SurveyController.GENRE_OPTIONS);
        model.addAttribute("moodOptions", SurveyController.MOOD_OPTIONS);

        return "recommendations";
    }
}
