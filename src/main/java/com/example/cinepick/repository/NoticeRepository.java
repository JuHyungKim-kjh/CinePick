package com.example.cinepick.repository;

import com.example.cinepick.domain.Notice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NoticeRepository extends JpaRepository<Notice, Long> {

    /**
     * 목록 화면과 메인 미리보기가 함께 쓴다. 고정 공지 먼저, 그다음 최신순.
     *
     * 정렬을 Pageable 에 맡기지 않고 이름에 박아 둔 이유: "고정 우선"은 화면이 고를 수 있는
     * 옵션이 아니라 이 도메인의 규칙이다. 호출부가 정렬을 넘기면 언젠가 고정 공지가 3페이지에서 나온다.
     */
    Page<Notice> findAllByOrderByPinnedDescCreatedAtDesc(Pageable pageable);
}
