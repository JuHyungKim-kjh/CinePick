package com.example.cinepick.repository;

import com.example.cinepick.domain.Member;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {
    // 이메일 중복 가입 확인
    Optional<Member> findByEmail(String email);

    // 가입 시 닉네임 중복 확인. 수정 화면은 본인을 제외해야 하므로 아래 것을 쓴다
    boolean existsByNickname(String nickname);

    boolean existsByNicknameAndIdNot(String nickname, Long id);
}
