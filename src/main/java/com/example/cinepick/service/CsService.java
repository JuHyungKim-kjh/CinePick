package com.example.cinepick.service;

import com.example.cinepick.domain.Faq;
import com.example.cinepick.domain.Notice;
import com.example.cinepick.dto.response.FaqResDto;
import com.example.cinepick.dto.response.NoticeResDto;
import com.example.cinepick.repository.FaqRepository;
import com.example.cinepick.repository.NoticeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 고객지원(공지사항 · FAQ) 조회 로직.
 * 두 게시판을 한 서비스에 둔 이유는 화면에서 늘 같이 다니기 때문이다.
 * 회원 개인의 글인 1:1 문의는 {@link InquiryService}, 쓰기는 {@link CsAdminService} 가 맡는다.
 */
@Service
@RequiredArgsConstructor
public class CsService {

    private final NoticeRepository noticeRepository;
    private final FaqRepository faqRepository;

    /** 목록 한 페이지에 보여줄 공지 수 */
    public static final int NOTICE_PAGE_SIZE = 10;

    /** 이 기간 안에 올라온 공지에는 NEW 배지가 붙는다 */
    private static final int FRESH_DAYS = 7;

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy.MM.dd");

    /** 분류 탭의 고정 순서. 여기 없는 분류는 뒤에 등장 순서대로 붙는다 */
    public static final List<String> FAQ_CATEGORY_ORDER = List.of("예매", "추천", "계정", "기타");

    /** 공지 말머리 선택지. 화면 배지 색이 붙어 있는 이름들이라 임의로 늘리면 회색으로 나온다 */
    public static final List<String> NOTICE_CATEGORIES = List.of("안내", "이벤트", "점검");

    // ---------------------------------------------------------------- 공지사항

    /** 공지 목록 한 페이지. page 는 1부터 센다(화면 표기와 맞추려고) */
    public Page<NoticeResDto> getNotices(int page) {
        int safePage = Math.max(1, page) - 1;
        return noticeRepository
                .findAllByOrderByPinnedDescCreatedAtDesc(PageRequest.of(safePage, NOTICE_PAGE_SIZE))
                .map(this::toDto);
    }

    /** 메인 화면 미리보기용 상위 몇 건 */
    public List<NoticeResDto> getRecentNotices(int size) {
        return noticeRepository
                .findAllByOrderByPinnedDescCreatedAtDesc(PageRequest.of(0, size))
                .map(this::toDto)
                .getContent();
    }

    /**
     * 공지 상세. 열어볼 때마다 조회수를 올린다.
     * 조회수 증가 때문에 쓰기 트랜잭션이며 더티 체킹만으로 저장된다(save 호출 없음).
     * 새로고침으로 계속 오르지만, 중복을 막으려면 세션이나 IP 를 들고 있어야 해서 그대로 둔다.
     */
    @Transactional
    public NoticeResDto getNotice(Long id) {
        Notice notice = noticeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("공지사항을 찾을 수 없습니다."));
        notice.setViewCount(notice.getViewCount() + 1);
        return toDto(notice);
    }

    /**
     * 수정 화면이 폼을 채울 때 쓰는 조회. {@link #getNotice(Long)} 과 달리 <b>조회수를 올리지 않는다.</b>
     * 운영자가 고치러 들어간 것을 독자가 읽은 것으로 세면 숫자가 사실과 멀어진다.
     */
    public NoticeResDto getNoticeForEdit(Long id) {
        return noticeRepository.findById(id).map(this::toDto).orElse(null);
    }

    private NoticeResDto toDto(Notice notice) {
        return NoticeResDto.builder()
                .id(notice.getId())
                .category(notice.getCategory())
                .title(notice.getTitle())
                .content(notice.getContent())
                .pinned(notice.isPinned())
                .viewCount(notice.getViewCount())
                .createdAt(notice.getCreatedAt().format(DATE))
                .fresh(notice.getCreatedAt().isAfter(LocalDateTime.now().minusDays(FRESH_DAYS)))
                .build();
    }

    // ---------------------------------------------------------------- FAQ

    /**
     * FAQ 전체 목록.
     * 분류로 먼저 묶고 그 안에서 sortOrder 를 따른다. 레포지토리 정렬을 그대로 쓰면
     * "예매1 · 추천1 · 계정1 · 기타1 · 예매2 …" 처럼 분류가 번갈아 나온다 —
     * sortOrder 는 <b>분류 안에서의</b> 순서라 분류를 가로질러 비교하면 뜻을 잃는다.
     */
    public List<FaqResDto> getFaqs() {
        return faqRepository.findAllByOrderBySortOrderAscIdAsc().stream()
                .sorted(Comparator.comparingInt(faq -> categoryRank(faq.getCategory())))
                .map(this::toDto)
                .toList();
    }

    /** 수정 화면이 폼을 채울 때 쓰는 FAQ 단건 조회 */
    public FaqResDto getFaq(Long id) {
        return faqRepository.findById(id).map(this::toDto).orElse(null);
    }

    private FaqResDto toDto(Faq faq) {
        return FaqResDto.builder()
                .id(faq.getId())
                .category(faq.getCategory())
                .question(faq.getQuestion())
                .answer(faq.getAnswer())
                .sortOrder(faq.getSortOrder())
                .build();
    }

    /** 메인 화면 미리보기용 상위 몇 건. 순서는 운영자가 정한 sortOrder 를 따른다 */
    public List<FaqResDto> getFaqPreview(int size) {
        List<FaqResDto> all = getFaqs();
        return all.size() <= size ? all : all.subList(0, size);
    }

    /**
     * 화면 상단 분류 탭에 쓸 이름들.
     * 글이 실제로 있는 분류만 내려보낸다 — 상수 목록을 그대로 쓰면 눌러도 아무것도 나오지 않는
     * 빈 탭이 생긴다. 순서는 FAQ_CATEGORY_ORDER 를 따르고 새 분류는 뒤에 붙는다.
     */
    public List<String> getFaqCategories() {
        Set<String> found = new LinkedHashSet<>();
        for (Faq faq : faqRepository.findAllByOrderBySortOrderAscIdAsc()) {
            found.add(faq.getCategory());
        }

        List<String> ordered = new ArrayList<>();
        for (String known : FAQ_CATEGORY_ORDER) {
            if (found.remove(known)) ordered.add(known);
        }
        ordered.addAll(found);
        return ordered;
    }

    /** 분류의 고정 순서 위치. 목록에 없는 새 분류는 뒤로 보낸다 */
    private int categoryRank(String category) {
        int index = FAQ_CATEGORY_ORDER.indexOf(category);
        return index < 0 ? FAQ_CATEGORY_ORDER.size() : index;
    }

    // ---------------------------------------------------------------- 초기 데이터

    /**
     * 게시판이 비어 있을 때만 기본 글을 넣는다.
     *
     * 담긴 내용은 예시 문구가 아니라 실제 동작 설명이다 — 특히 예매를 예매사에서 한다는 점은
     * 이 서비스의 가장 큰 오해 지점이라 결제 후에 알게 되면 늦는다.
     * 두 게시판을 따로 검사하는 이유: 한쪽만 지우고 재시작해도 그쪽이 복구되게 하기 위해서다.
     */
    @Transactional
    public void seedIfEmpty() {
        if (noticeRepository.count() == 0) seedNotices();
        if (faqRepository.count() == 0) seedFaqs();
    }

    private void seedNotices() {
        LocalDateTime now = LocalDateTime.now();
        List<Notice> notices = List.of(
                notice("안내", "CinePick은 예매를 대신 처리하지 않아요", true, now.minusDays(1), """
                        CinePick은 현재 상영작 정보를 모아 보여주고, 예매는 각 예매사(CGV · 롯데시네마 · 메가박스)에서
                        진행하도록 연결해 드리는 서비스예요.

                        영화 상세 화면에서 예매 버튼을 누르면 해당 예매사 페이지가 새 탭으로 열립니다.
                        결제와 취소, 좌석 선택은 모두 그 사이트에서 이루어지고 CinePick은 관여하지 않아요.

                        그래서 결제가 끝났는지를 저희가 알 수 없습니다. 예매사에 다녀오신 뒤 화면으로 돌아오시면
                        예매하셨는지 여쭤보는 팝업이 뜨는데, 여기서 알려주신 답으로 예매 내역이 확정돼요."""),

                notice("안내", "CinePick 오픈 안내", true, now.minusDays(2), """
                        영화를 더 특별하게, 취향을 더 완벽하게. CinePick이 문을 열었습니다.

                        - 현재 상영작 · 상영 예정작을 예매사별로 모아서 보기
                        - 간단한 설문으로 시작하는 취향 분석
                        - 관람 이력과 평점을 반영한 맞춤 추천

                        아직 다듬는 중인 화면이 있어요. 이상한 점을 발견하시면 고객센터로 알려주세요."""),

                notice("이벤트", "취향 설문에 참여하고 맞춤 추천 받아보세요", false, now.minusDays(3), """
                        가입 후 처음 접속하면 뜨는 설문에서 좋아하는 장르 3개와 분위기 3개를 골라주세요.
                        고른 항목이 바로 취향 점수로 반영되어, 메인 화면 상단이 회원님만을 위한 추천으로 채워집니다.

                        설문은 언제든 취향 및 장르 분석 화면에서 다시 조정할 수 있어요."""),

                notice("안내", "취향 분석 기준이 달라졌어요", false, now.minusDays(9), """
                        예전에는 관람 편수가 늘어날수록 설문에서 고르신 취향이 점점 묻혔습니다.
                        30편쯤 보시면 설문 반영 비중이 20% 아래로 떨어졌는데, 이를 되돌릴 방법이 없었어요.

                        이제는 설문과 관람 이력을 각각 따로 계산한 뒤, 회원님이 정한 판단 비중으로 섞습니다.
                        편수가 아무리 늘어도 비중은 정하신 그대로 유지돼요. 기본값은 50 대 50입니다.

                        비중 조절은 취향 및 장르 분석 화면에서 하실 수 있어요."""),

                notice("안내", "관람한 영화에 평점을 남겨보세요", false, now.minusDays(14), """
                        예매를 확정하신 영화에는 평점과 한 줄 리뷰를 남기실 수 있어요.
                        마이페이지 > 나의 평점 및 리뷰에서 확인하실 수 있습니다.

                        평점은 추천에도 함께 쓰입니다. 높은 점수를 주신 장르는 더 자주,
                        낮은 점수를 주신 장르는 덜 나오게 돼요. 몇 번이든 다시 고치실 수 있습니다."""),

                notice("점검", "정기 서버 점검 안내", false, now.minusDays(21), """
                        서비스 안정화를 위한 정기 점검을 진행합니다.

                        - 일시: 매월 첫째 주 화요일 02:00 ~ 04:00
                        - 영향: 점검 시간 동안 CinePick 접속이 원활하지 않을 수 있어요.

                        예매사 사이트는 점검 대상이 아니므로 예매 자체는 정상적으로 하실 수 있습니다.""")
        );
        noticeRepository.saveAll(notices);
    }

    private Notice notice(String category, String title, boolean pinned,
                          LocalDateTime createdAt, String content) {
        Notice notice = new Notice();
        notice.setCategory(category);
        notice.setTitle(title);
        notice.setPinned(pinned);
        notice.setContent(content);
        notice.setCreatedAt(createdAt);
        return notice;
    }

    private void seedFaqs() {
        List<Faq> faqs = List.of(
                faq("예매", 1, "CinePick에서 바로 예매할 수 있나요?", """
                        아니요. CinePick은 예매사 페이지로 연결해 드리는 곳까지만 담당해요.
                        영화 상세 화면의 예매 버튼을 누르면 CGV · 롯데시네마 · 메가박스 중 고르신 곳이 새 탭으로 열리고,
                        좌석 선택과 결제는 그 사이트에서 진행됩니다."""),

                faq("예매", 2, "예매했는지 왜 저에게 다시 물어보나요?", """
                        결제가 예매사에서 이루어지기 때문에, 실제로 예매를 마치셨는지 CinePick이 알 수 있는 방법이 없어요.
                        그래서 예매사에 다녀오신 뒤 돌아오시면 팝업으로 여쭤봅니다.

                        예매하셨다고 알려주시면 그 영화에 평점을 남기실 수 있게 되고, 취향 분석에도 반영돼요.
                        답을 미루셔도 괜찮습니다. 마이페이지 > 예매/취소 내역에서 나중에 정리하실 수 있어요."""),

                faq("예매", 3, "예매를 취소하고 싶어요.", """
                        결제하신 예매사에서 취소하셔야 해요. CinePick에서는 취소가 되지 않습니다.

                        내역 화면의 안 함 버튼은 예매를 취소하는 기능이 아니라,
                        이 건은 결국 예매하지 않았다고 기록만 정리하는 버튼이에요."""),

                faq("추천", 1, "추천은 어떤 기준으로 만들어지나요?", """
                        두 가지를 섞습니다. 회원님이 설문에서 직접 고르신 취향, 그리고 확정된 예매와 평점 이력이에요.

                        둘을 어떤 비율로 볼지는 취향 및 장르 분석 화면의 판단 비중 슬라이더로 직접 정하실 수 있어요.
                        검색 기록은 사용하지 않습니다. 제목으로 검색하는 구조라 검색어만으로는 취향을 알 수 없거든요."""),

                faq("추천", 2, "설문 내용을 다시 바꾸고 싶어요.", """
                        취향 및 장르 분석 화면에서 언제든 바꾸실 수 있어요.
                        처음 설문은 3개만 고르는 간단한 형태지만, 이 화면에서는 장르 8개와 분위기 5개를 모두 조절하실 수 있습니다."""),

                faq("추천", 3, "장르에 기피 배지가 붙었어요.", """
                        그 장르의 영화에 낮은 평점을 주신 기록이 쌓여 점수가 0 아래로 내려간 상태예요.
                        추천에서 그 장르는 감점 요소로 쓰입니다.

                        영구적인 것은 아니에요. 같은 장르에 좋은 평점을 남기시면 다시 올라옵니다.
                        올라오는 속도가 내려가는 속도보다 빠르게 잡혀 있어요."""),

                faq("계정", 1, "비밀번호나 닉네임을 바꾸고 싶어요.", """
                        마이페이지 > 회원정보 조회에서 변경하실 수 있어요.
                        비밀번호 칸을 비워두고 저장하시면 기존 비밀번호가 그대로 유지됩니다."""),

                faq("계정", 2, "평점은 몇 번까지 남길 수 있나요?", """
                        횟수 제한은 없어요. 예매를 확정하신 영화라면 언제든 다시 고치실 수 있습니다.
                        다만 같은 영화를 여러 번 예매하셔도 평점은 영화 한 편당 하나만 남습니다."""),

                faq("기타", 1, "상영작 정보는 얼마나 자주 갱신되나요?", """
                        서버가 켜질 때 한 번, 그 뒤로는 주기적으로 예매사 사이트를 확인해 갱신합니다.

                        갱신 중에는 잠시 대기 화면이 보일 수 있어요. 예매사 사이트 사정으로 일부 정보를 가져오지 못하면
                        일부 정보를 불러오지 못했다고 안내해 드립니다."""),

                faq("기타", 2, "찾는 답이 없어요.", """
                        고객센터(02-1234-5678)로 연락 주시면 도와드릴게요.
                        평일 09:00 ~ 18:00 (점심 12:00 ~ 13:00)에 운영합니다.""")
        );
        faqRepository.saveAll(faqs);
    }

    private Faq faq(String category, int sortOrder, String question, String answer) {
        Faq faq = new Faq();
        faq.setCategory(category);
        faq.setSortOrder(sortOrder);
        faq.setQuestion(question);
        faq.setAnswer(answer);
        return faq;
    }
}
