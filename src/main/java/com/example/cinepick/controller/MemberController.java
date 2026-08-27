package com.example.cinepick.controller;

import com.example.cinepick.domain.Member;
import com.example.cinepick.dto.request.MemberUpdateReqDto;
import com.example.cinepick.repository.MemberRepository;
import com.example.cinepick.service.RatingService;
import com.example.cinepick.service.ReservationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/members")
@RequiredArgsConstructor
public class MemberController {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final ReservationService reservationService;
    private final RatingService ratingService;

    // 마이페이지 대문
    @GetMapping("/me")
    public String myPage(Authentication authentication, Model model) {
        model.addAttribute("member", getCurrentMember(authentication));
        return "members/mypage";
    }

    // 회원정보 조회/수정 화면
    @GetMapping("/info")
    public String memberInfoPage(Authentication authentication, Model model) {
        model.addAttribute("member", getCurrentMember(authentication));
        return "members/info";
    }

    // 회원정보 수정 처리
    @PostMapping("/info")
    public String updateMemberInfo(MemberUpdateReqDto reqDto,
                                   Authentication authentication,
                                   Model model) {
        Member member = getCurrentMember(authentication);

        // 닉네임 중복 검사 (본인 제외)
        if (!member.getNickname().equals(reqDto.getNickname())
                && memberRepository.existsByNicknameAndIdNot(reqDto.getNickname(), member.getId())) {
            model.addAttribute("member", member);
            model.addAttribute("errorMessage", "이미 사용 중인 닉네임입니다.");
            return "members/info";
        }

        // 비밀번호는 입력했을 때만 변경 (비우면 기존 값 유지)
        boolean changingPassword = reqDto.getPassword() != null && !reqDto.getPassword().isBlank();
        if (changingPassword) {
            if (!reqDto.getPassword().equals(reqDto.getPasswordConfirm())) {
                model.addAttribute("member", member);
                model.addAttribute("errorMessage", "비밀번호가 일치하지 않습니다.");
                return "members/info";
            }
            member.setPassword(passwordEncoder.encode(reqDto.getPassword()));
        }

        member.setNickname(reqDto.getNickname());
        memberRepository.save(member);

        return "redirect:/members/info?updated";
    }

    // 예매/취소 내역
    @GetMapping("/reservations")
    public String reservations(Authentication authentication, Model model) {
        Member member = getCurrentMember(authentication);
        model.addAttribute("reservations", reservationService.findHistory(member.getId()));
        model.addAttribute("activeMenu", "reservations");
        return "members/reservations";
    }

    // 나의 평점 및 리뷰
    @GetMapping("/ratings")
    public String ratings(Authentication authentication, Model model) {
        Member member = getCurrentMember(authentication);
        model.addAttribute("ratings", ratingService.findRatingList(member.getId()));
        model.addAttribute("activeMenu", "ratings");
        return "members/ratings";
    }

    private Member getCurrentMember(Authentication authentication) {
        return memberRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new IllegalStateException("회원 정보를 찾을 수 없습니다."));
    }
}