package com.example.cinepick.repository;

import com.example.cinepick.domain.PreferenceSurvey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PreferenceSurveyRepository extends JpaRepository<PreferenceSurvey, Long> {
    boolean existsByMemberId(Long memberId);
    Optional<PreferenceSurvey> findByMemberId(Long memberId);
}