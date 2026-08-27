package com.example.cinepick.repository;

import com.example.cinepick.domain.CrawlSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

/** 크롤링 스냅샷 저장소. 행이 하나뿐이라 findById(SINGLETON_ID) 와 save 만 쓴다. */
public interface CrawlSnapshotRepository extends JpaRepository<CrawlSnapshot, Long> {
}
