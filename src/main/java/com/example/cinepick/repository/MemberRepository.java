package com.example.cinepick.repository;

import com.example.cinepick.domain.Member;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {
    // 이메일 중복 가입 확인
    Optional<Member> findByEmail(String email);
    boolean existsByNicknameAndIdNot(String nickname, Long id);
}
