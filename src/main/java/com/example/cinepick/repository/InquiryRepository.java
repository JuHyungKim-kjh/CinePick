package com.example.cinepick.repository;

import com.example.cinepick.domain.Inquiry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

/**
 * 1:1 상담 저장소.
 *
 * 회원 화면용 조회 메서드에는 회원 조건이 <b>이름에 박혀</b> 있다 — 남의 문의가 섞여 나오는
 * 사고를 아예 만들 수 없게 하기 위해서다(물려받은 findAll 은 쓰지 않는다).
 * 전체 조회가 필요한 운영자 화면용은 파생 쿼리로 슬쩍 열지 않고 {@link #findAllForAdmin()}
 * 이라는 이름을 붙였다 — 자동완성에서 잘못 골라도 이름만 보고 알아챌 수 있어야 한다.
 */
public interface InquiryRepository extends JpaRepository<Inquiry, Long> {

    List<Inquiry> findByMemberIdOrderByCreatedAtDesc(Long memberId);

    long countByMemberId(Long memberId);

    /**
     * 운영자 답변 화면이 쓰는 전체 목록. <b>회원 화면에서는 부르지 않는다.</b>
     *
     * 정렬은 미답변 우선, 그다음 최신순. status 로 정렬하지 않은 이유는 문자열로 저장되는
     * enum 이라 사전순(ANSWERED &lt; WAITING)이 되어 답변한 건이 위로 올라오기 때문이다.
     * join fetch 는 목록에 작성자 닉네임을 함께 보여주기 때문(member 가 LAZY).
     */
    @Query("select i from Inquiry i join fetch i.member "
            + "order by case when i.answeredAt is null then 0 else 1 end, i.createdAt desc")
    List<Inquiry> findAllForAdmin();

    /** 관리 화면 상단에 보여줄 미답변 건수 */
    long countByAnsweredAtIsNull();
}
