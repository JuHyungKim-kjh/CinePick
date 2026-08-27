package com.example.cinepick.service;

import com.example.cinepick.domain.Faq;
import com.example.cinepick.domain.Inquiry;
import com.example.cinepick.domain.InquiryStatus;
import com.example.cinepick.domain.Notice;
import com.example.cinepick.dto.request.FaqReqDto;
import com.example.cinepick.dto.request.NoticeReqDto;
import com.example.cinepick.dto.response.InquiryResDto;
import com.example.cinepick.repository.FaqRepository;
import com.example.cinepick.repository.InquiryRepository;
import com.example.cinepick.repository.NoticeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 고객지원(공지 · FAQ · 1:1 상담)을 <b>고치는</b> 쪽. 운영자(ROLE_ADMIN)만 도달한다.
 * 읽기({@link CsService})와 나눈 이유는 권한이다 — 섞으면 메서드마다 "관리자 전용인가"를
 * 되짚어야 하고 그 판단은 언젠가 틀린다. 검증 실패는 예외가 아니라 안내 문구로 돌려준다.
 */
@Service
@RequiredArgsConstructor
public class CsAdminService {

    private final NoticeRepository noticeRepository;
    private final FaqRepository faqRepository;
    private final InquiryRepository inquiryRepository;
    private final InquiryService inquiryService;

    /** 제목·질문 칸의 길이 상한. 엔티티 컬럼 길이(200)와 화면 안내가 어긋나지 않게 여기서 정한다 */
    private static final int TITLE_MAX = 200;

    // ---------------------------------------------------------------- 공지사항

    /**
     * 공지 등록.
     *
     * @return null 이면 성공, 값이 있으면 화면에 띄울 실패 사유
     */
    @Transactional
    public String create(NoticeReqDto reqDto) {
        String error = validate(reqDto.getTitle(), reqDto.getContent(), "공지 제목", "공지 내용");
        if (error != null) return error;

        Notice notice = new Notice();
        apply(notice, reqDto);
        noticeRepository.save(notice);
        return null;
    }

    /** 공지 수정. 등록 시각과 조회수는 건드리지 않는다(고친 것이지 다시 올린 것이 아니다) */
    @Transactional
    public String update(Long id, NoticeReqDto reqDto) {
        String error = validate(reqDto.getTitle(), reqDto.getContent(), "공지 제목", "공지 내용");
        if (error != null) return error;

        Notice notice = noticeRepository.findById(id).orElse(null);
        if (notice == null) return "이미 삭제된 공지사항이에요.";

        apply(notice, reqDto);   // 더티 체킹으로 저장됩니다
        return null;
    }

    @Transactional
    public void deleteNotice(Long id) {
        noticeRepository.deleteById(id);
    }

    private void apply(Notice notice, NoticeReqDto reqDto) {
        notice.setCategory(blankTo(reqDto.getCategory(), "안내"));
        notice.setTitle(reqDto.getTitle().trim());
        notice.setContent(reqDto.getContent().trim());
        notice.setPinned(reqDto.isPinned());
    }

    // ---------------------------------------------------------------- FAQ

    /**
     * FAQ 등록.
     *
     * @return null 이면 성공, 값이 있으면 화면에 띄울 실패 사유
     */
    @Transactional
    public String create(FaqReqDto reqDto) {
        String error = validate(reqDto.getQuestion(), reqDto.getAnswer(), "질문", "답변");
        if (error != null) return error;

        Faq faq = new Faq();
        apply(faq, reqDto);
        faqRepository.save(faq);
        return null;
    }

    @Transactional
    public String update(Long id, FaqReqDto reqDto) {
        String error = validate(reqDto.getQuestion(), reqDto.getAnswer(), "질문", "답변");
        if (error != null) return error;

        Faq faq = faqRepository.findById(id).orElse(null);
        if (faq == null) return "이미 삭제된 질문이에요.";

        apply(faq, reqDto);
        return null;
    }

    @Transactional
    public void deleteFaq(Long id) {
        faqRepository.deleteById(id);
    }

    private void apply(Faq faq, FaqReqDto reqDto) {
        faq.setCategory(blankTo(reqDto.getCategory(), "기타"));
        faq.setQuestion(reqDto.getQuestion().trim());
        faq.setAnswer(reqDto.getAnswer().trim());
        faq.setSortOrder(Math.max(0, reqDto.getSortOrder()));
    }

    // ---------------------------------------------------------------- 1:1 상담

    /** 답변 화면의 전체 목록. 미답변이 위, 그다음 최신순 */
    public List<InquiryResDto> getInquiries() {
        return inquiryRepository.findAllForAdmin().stream()
                .map(inquiryService::toDto)
                .toList();
    }

    /** 아직 답변하지 않은 건수. 화면 상단에 "N건 대기"로 보여준다 */
    public long countWaitingInquiries() {
        return inquiryRepository.countByAnsweredAtIsNull();
    }

    /**
     * 문의에 답변을 단다. 이미 답변한 건에 다시 부르면 내용을 고치고 <b>답변 시각도 갱신</b>한다
     * (회원이 보는 것은 "이 답이 언제 쓰였는가"다). 답변을 지우는 기능은 두지 않았다.
     *
     * @return null 이면 성공, 값이 있으면 화면에 띄울 실패 사유
     */
    @Transactional
    public String answerInquiry(Long id, String answer) {
        String trimmed = trimToEmpty(answer);
        if (trimmed.isEmpty()) return "답변 내용을 입력해 주세요.";

        Inquiry inquiry = inquiryRepository.findById(id).orElse(null);
        if (inquiry == null) return "이미 삭제된 문의예요.";

        inquiry.setAnswer(trimmed);
        inquiry.setStatus(InquiryStatus.ANSWERED);
        inquiry.setAnsweredAt(LocalDateTime.now());
        return null;   // 더티 체킹으로 저장됩니다
    }

    // ---------------------------------------------------------------- 공통

    /**
     * 두 게시판의 검증 규칙이 같다 — 제목 한 줄과 본문이 있어야 하고 제목은 200자 이하.
     * 칸 이름만 화면마다 달라 인자로 받는다.
     */
    private String validate(String title, String content, String titleLabel, String contentLabel) {
        String trimmedTitle = trimToEmpty(title);
        String trimmedContent = trimToEmpty(content);

        if (trimmedTitle.isEmpty()) return objectJosa(titleLabel) + " 입력해 주세요.";
        if (trimmedContent.isEmpty()) return objectJosa(contentLabel) + " 입력해 주세요.";
        if (trimmedTitle.length() > TITLE_MAX) {
            return topicJosa(titleLabel) + " " + TITLE_MAX + "자까지 쓸 수 있어요.";
        }
        return null;
    }

    /**
     * 받침 유무로 조사를 골라 붙인다. "제목을(를)" 같은 문구를 만들지 않기 위한 것.
     * 한글이 아닌 글자로 끝나면 판단할 수 없으므로 받침이 있는 쪽으로 둔다.
     */
    private String withJosa(String word, String withBatchim, String withoutBatchim) {
        if (word == null || word.isEmpty()) return word + withBatchim;
        char last = word.charAt(word.length() - 1);
        if (last < 0xAC00 || last > 0xD7A3) return word + withBatchim;
        return word + (((last - 0xAC00) % 28 == 0) ? withoutBatchim : withBatchim);
    }

    /** 목적격 조사 (~을 / ~를) */
    private String objectJosa(String word) {
        return withJosa(word, "을", "를");
    }

    /** 주제격 조사 (~은 / ~는) */
    private String topicJosa(String word) {
        return withJosa(word, "은", "는");
    }

    private String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private String blankTo(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value;
    }
}
