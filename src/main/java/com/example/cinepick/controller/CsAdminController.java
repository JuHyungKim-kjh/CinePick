package com.example.cinepick.controller;

import com.example.cinepick.dto.request.FaqReqDto;
import com.example.cinepick.dto.request.NoticeReqDto;
import com.example.cinepick.dto.response.FaqResDto;
import com.example.cinepick.dto.response.NoticeResDto;
import com.example.cinepick.service.CsAdminService;
import com.example.cinepick.service.CsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 고객지원 게시판 관리 화면. 운영자(ROLE_ADMIN) 전용.
 * URL 을 /cs/admin 한 갈래로 모아 SecurityConfig 규칙이 한 줄로 끝난다.
 * 삭제는 전부 POST 폼이지만 <b>CSRF 가 꺼져 있어 안전한 것은 아니다</b> — 켤 때 이 폼들도 함께 볼 것.
 */
@Controller
@RequestMapping("/cs/admin")
@RequiredArgsConstructor
public class CsAdminController {

    private final CsService csService;
    private final CsAdminService csAdminService;

    // ---------------------------------------------------------------- 공지사항

    @GetMapping("/notices/new")
    public String noticeForm(Model model) {
        model.addAttribute("categories", CsService.NOTICE_CATEGORIES);
        model.addAttribute("activeTab", "notices");
        model.addAttribute("editing", false);
        return "cs/notice-form";
    }

    @PostMapping("/notices")
    public String createNotice(NoticeReqDto reqDto, Model model, RedirectAttributes redirect) {
        String error = csAdminService.create(reqDto);
        if (error != null) return backToNoticeForm(error, reqDto, null, model);

        redirect.addFlashAttribute("successMessage", "공지사항을 등록했어요.");
        return "redirect:/cs/notices";
    }

    @GetMapping("/notices/{id}/edit")
    public String noticeEditForm(@PathVariable Long id, Model model) {
        NoticeResDto notice = csService.getNoticeForEdit(id);
        if (notice == null) return "redirect:/cs/notices";

        model.addAttribute("categories", CsService.NOTICE_CATEGORIES);
        model.addAttribute("activeTab", "notices");
        model.addAttribute("editing", true);
        model.addAttribute("noticeId", id);
        model.addAttribute("notice", notice);
        return "cs/notice-form";
    }

    @PostMapping("/notices/{id}")
    public String updateNotice(@PathVariable Long id, NoticeReqDto reqDto,
                               Model model, RedirectAttributes redirect) {
        String error = csAdminService.update(id, reqDto);
        if (error != null) return backToNoticeForm(error, reqDto, id, model);

        redirect.addFlashAttribute("successMessage", "공지사항을 수정했어요.");
        return "redirect:/cs/notices/" + id;
    }

    @PostMapping("/notices/{id}/delete")
    public String deleteNotice(@PathVariable Long id, RedirectAttributes redirect) {
        csAdminService.deleteNotice(id);
        redirect.addFlashAttribute("successMessage", "공지사항을 삭제했어요.");
        return "redirect:/cs/notices";
    }

    /**
     * 저장에 실패했을 때 쓰던 화면으로 되돌린다.
     * redirect 로 보내지 않는 이유는 방금 쓴 본문이 사라지기 때문 — 입력값(form)을 얹어 다시 그린다.
     */
    private String backToNoticeForm(String error, NoticeReqDto reqDto, Long id, Model model) {
        model.addAttribute("categories", CsService.NOTICE_CATEGORIES);
        model.addAttribute("activeTab", "notices");
        model.addAttribute("editing", id != null);
        model.addAttribute("noticeId", id);
        model.addAttribute("errorMessage", error);
        model.addAttribute("form", reqDto);
        return "cs/notice-form";
    }

    // ---------------------------------------------------------------- FAQ

    @GetMapping("/faqs/new")
    public String faqForm(Model model) {
        model.addAttribute("categories", CsService.FAQ_CATEGORY_ORDER);
        model.addAttribute("activeTab", "faqs");
        model.addAttribute("editing", false);
        return "cs/faq-form";
    }

    @PostMapping("/faqs")
    public String createFaq(FaqReqDto reqDto, Model model, RedirectAttributes redirect) {
        String error = csAdminService.create(reqDto);
        if (error != null) return backToFaqForm(error, reqDto, null, model);

        redirect.addFlashAttribute("successMessage", "질문을 등록했어요.");
        return "redirect:/cs/faqs";
    }

    @GetMapping("/faqs/{id}/edit")
    public String faqEditForm(@PathVariable Long id, Model model) {
        FaqResDto faq = csService.getFaq(id);
        if (faq == null) return "redirect:/cs/faqs";

        model.addAttribute("categories", CsService.FAQ_CATEGORY_ORDER);
        model.addAttribute("activeTab", "faqs");
        model.addAttribute("editing", true);
        model.addAttribute("faqId", id);
        model.addAttribute("faq", faq);
        return "cs/faq-form";
    }

    @PostMapping("/faqs/{id}")
    public String updateFaq(@PathVariable Long id, FaqReqDto reqDto,
                            Model model, RedirectAttributes redirect) {
        String error = csAdminService.update(id, reqDto);
        if (error != null) return backToFaqForm(error, reqDto, id, model);

        redirect.addFlashAttribute("successMessage", "질문을 수정했어요.");
        return "redirect:/cs/faqs";
    }

    @PostMapping("/faqs/{id}/delete")
    public String deleteFaq(@PathVariable Long id, RedirectAttributes redirect) {
        csAdminService.deleteFaq(id);
        redirect.addFlashAttribute("successMessage", "질문을 삭제했어요.");
        return "redirect:/cs/faqs";
    }

    // ---------------------------------------------------------------- 1:1 상담

    /**
     * 답변 화면. 문의를 한 목록에 모아 보여주고 각 건을 펼치면 답변 칸이 나온다.
     * 건마다 상세로 들어가지 않는 이유: 답변은 대개 여러 건을 연달아 처리하는 일이라
     * 목록으로 돌아왔다 다시 들어가면 같은 왕복을 반복하게 된다.
     */
    @GetMapping("/inquiries")
    public String inquiries(Model model) {
        fillInquiryAdminModel(model);
        return "cs/inquiry-admin";
    }

    @PostMapping("/inquiries/{id}/answer")
    public String answerInquiry(@PathVariable Long id, @RequestParam String answer,
                                Model model, RedirectAttributes redirect) {
        String error = csAdminService.answerInquiry(id, answer);
        if (error != null) {
            // 실패해도 목록은 그대로 보여줘야 어느 건에서 막혔는지 알 수 있다
            fillInquiryAdminModel(model);
            model.addAttribute("errorMessage", error);
            model.addAttribute("errorInquiryId", id);
            return "cs/inquiry-admin";
        }

        redirect.addFlashAttribute("successMessage", "답변을 등록했어요.");
        return "redirect:/cs/admin/inquiries";
    }

    private void fillInquiryAdminModel(Model model) {
        model.addAttribute("inquiries", csAdminService.getInquiries());
        model.addAttribute("waitingCount", csAdminService.countWaitingInquiries());
        model.addAttribute("activeTab", "inquiry-admin");
    }

    private String backToFaqForm(String error, FaqReqDto reqDto, Long id, Model model) {
        model.addAttribute("categories", CsService.FAQ_CATEGORY_ORDER);
        model.addAttribute("activeTab", "faqs");
        model.addAttribute("editing", id != null);
        model.addAttribute("faqId", id);
        model.addAttribute("errorMessage", error);
        model.addAttribute("form", reqDto);
        return "cs/faq-form";
    }
}
