package com.example.cinepick.controller;

import com.example.cinepick.domain.Member;
import com.example.cinepick.dto.request.InquiryReqDto;
import com.example.cinepick.dto.response.NoticeResDto;
import com.example.cinepick.repository.MemberRepository;
import com.example.cinepick.service.CsService;
import com.example.cinepick.service.InquiryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/**
 * 고객지원 조회 화면. 공지사항 · FAQ · 1:1 상담이 탭으로 이어진 한 페이지다(탭 표시는 activeTab).
 * 글을 고치는 쪽은 {@link CsAdminController} 가 맡고 URL 도 /cs/admin/** 로 모여 있다.
 * 1:1 상담만 로그인이 필요하지만 탭 자체는 감추지 않는다.
 */
@Controller
@RequestMapping("/cs")
@RequiredArgsConstructor
public class CsController {

    private final CsService csService;
    private final InquiryService inquiryService;
    private final MemberRepository memberRepository;

    /** 상단 메뉴가 가리키는 입구. 고객지원의 첫 화면은 공지사항 */
    @GetMapping
    public String csHome() {
        return "redirect:/cs/notices";
    }

    // ---------------------------------------------------------------- 공지사항

    @GetMapping("/notices")
    public String notices(@RequestParam(defaultValue = "1") int page, Model model) {
        Page<NoticeResDto> notices = csService.getNotices(page);
        model.addAttribute("notices", notices);
        // Page 번호는 0부터라 화면에 그대로 쓰면 1페이지가 0으로 보인다. 여기서 한 번만 보정한다
        model.addAttribute("currentPage", notices.getNumber() + 1);
        model.addAttribute("activeTab", "notices");
        return "cs/notices";
    }

    @GetMapping("/notices/{id}")
    public String noticeDetail(@PathVariable Long id, Model model) {
        try {
            model.addAttribute("notice", csService.getNotice(id));
        } catch (IllegalArgumentException e) {
            // 지워졌거나 없는 번호. 오류 화면 대신 목록으로 되돌린다
            return "redirect:/cs/notices";
        }
        model.addAttribute("activeTab", "notices");
        return "cs/notice-detail";
    }

    // ---------------------------------------------------------------- FAQ

    @GetMapping("/faqs")
    public String faqs(Model model) {
        model.addAttribute("faqs", csService.getFaqs());
        model.addAttribute("categories", csService.getFaqCategories());
        model.addAttribute("activeTab", "faqs");
        return "cs/faqs";
    }

    // ---------------------------------------------------------------- 1:1 상담

    @GetMapping("/inquiry")
    public String inquiry(Authentication authentication, Model model) {
        fillInquiryModel(authentication, model);
        return "cs/inquiry";
    }

    /**
     * 문의 접수.
     * 실패하면 같은 화면을 다시 그린다 — redirect 로 돌리면 길게 쓴 글이 사라진다.
     */
    @PostMapping("/inquiry")
    public String createInquiry(InquiryReqDto reqDto, Authentication authentication,
                                Model model, RedirectAttributes redirect) {
        Member member = currentMember(authentication);
        if (member == null) return "redirect:/login";

        String error = inquiryService.create(member, reqDto);
        if (error != null) {
            fillInquiryModel(authentication, model);
            model.addAttribute("errorMessage", error);
            model.addAttribute("form", reqDto);
            return "cs/inquiry";
        }

        // 성공했을 때만 redirect. 새로고침으로 같은 문의가 두 번 접수되는 것을 막는다
        redirect.addFlashAttribute("successMessage", "문의가 접수되었어요. 순서대로 확인해 답변드릴게요.");
        return "redirect:/cs/inquiry";
    }

    private void fillInquiryModel(Authentication authentication, Model model) {
        Member member = currentMember(authentication);
        model.addAttribute("categories", InquiryService.CATEGORIES);
        model.addAttribute("inquiries", member == null ? List.of() : inquiryService.getMyInquiries(member));
        model.addAttribute("activeTab", "inquiry");
    }

    private Member currentMember(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) return null;
        return memberRepository.findByEmail(authentication.getName()).orElse(null);
    }
}
