package com.example.cinepick.repository;

import com.example.cinepick.domain.Faq;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FaqRepository extends JpaRepository<Faq, Long> {

    /** FAQ 는 건수가 적어 페이징 없이 한 번에 읽는다 (분류 탭 전환도 화면에서 처리) */
    List<Faq> findAllByOrderBySortOrderAscIdAsc();

    List<Faq> findByCategoryOrderBySortOrderAscIdAsc(String category);
}
