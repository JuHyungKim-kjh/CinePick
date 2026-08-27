package com.example.cinepick.service;

import com.example.cinepick.domain.Inquiry;
import com.example.cinepick.domain.Member;
import com.example.cinepick.dto.request.InquiryReqDto;
import com.example.cinepick.dto.response.InquiryResDto;
import com.example.cinepick.repository.InquiryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 1:1 상담 로직.
 * {@link CsService}(공지 · FAQ)와 따로 둔 이유는 데이터의 주인이 다르기 때문이다.
 * 문의는 회원 개인의 글이라 조회할 때마다 소유권을 확인해야 한다 — 한 서비스에 섞으면
 * "이 메서드는 회원 확인이 필요한가"를 매번 되짚어야 하고 언젠가 한 번은 빠뜨린다.
 */
@Service
@RequiredArgsConstructor
public class InquiryService {

    private final InquiryRepository inquiryRepository;

    /** 문의 유형 선택지. FAQ 분류와 같은 이름을 써서 두 화면의 어휘를 맞춘다 */
    public static final List<String> CATEGORIES = List.of("예매", "추천", "계정", "기타");

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy.MM.dd");

    /** 제목·내용 길이 상한. 엔티티 컬럼 길이(200)와 화면 안내 문구가 어긋나지 않게 여기서 정한다 */
    private static final int TITLE_MAX = 200;

    /** 내 문의 내역. 최신 글이 위 */
    public List<InquiryResDto> getMyInquiries(Member member) {
        return inquiryRepository.findByMemberIdOrderByCreatedAtDesc(member.getId()).stream()
                .map(this::toDto)
                .toList();
    }

    public long countMyInquiries(Member member) {
        return inquiryRepository.countByMemberId(member.getId());
    }

    /**
     * 문의 접수.
     * 검증에 걸리면 예외 대신 안내 문구를 돌려준다 — 사용자가 폼을 덜 채운 상황이지 오류가 아니다.
     *
     * @return null 이면 성공, 값이 있으면 화면에 띄울 실패 사유
     */
    @Transactional
    public String create(Member member, InquiryReqDto reqDto) {
        String title = trimToEmpty(reqDto.getTitle());
        String content = trimToEmpty(reqDto.getContent());

        if (title.isEmpty()) return "문의 제목을 입력해 주세요.";
        if (content.isEmpty()) return "문의 내용을 입력해 주세요.";
        if (title.length() > TITLE_MAX) return "문의 제목은 " + TITLE_MAX + "자까지 쓰실 수 있어요.";

        Inquiry inquiry = new Inquiry();
        inquiry.setMember(member);
        inquiry.setCategory(CATEGORIES.contains(reqDto.getCategory()) ? reqDto.getCategory() : "기타");
        inquiry.setTitle(title);
        inquiry.setContent(content);
        inquiryRepository.save(inquiry);
        return null;
    }

    private String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    /**
     * 화면에 내려보낼 모양으로 변환.
     * 회원 화면과 운영자 답변 화면이 같은 변환을 쓴다 — 같은 글을 다르게 보여주면
     * 답변을 쓴 사람과 받은 사람이 서로 다른 것을 보게 된다. 그래서 공개 메서드다.
     */
    public InquiryResDto toDto(Inquiry inquiry) {
        return InquiryResDto.builder()
                .id(inquiry.getId())
                .writer(inquiry.getMember().getNickname())
                .category(inquiry.getCategory())
                .title(inquiry.getTitle())
                .content(inquiry.getContent())
                .statusLabel(inquiry.getStatus().getLabel())
                .answered(inquiry.isAnswered())
                .answer(inquiry.getAnswer())
                .createdAt(inquiry.getCreatedAt().format(DATE))
                .answeredAt(inquiry.getAnsweredAt() == null ? null : inquiry.getAnsweredAt().format(DATE))
                .build();
    }
}
