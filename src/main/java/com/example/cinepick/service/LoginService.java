package com.example.cinepick.service;

import com.example.cinepick.domain.Member;
import com.example.cinepick.dto.request.SignUpReqDto;
import com.example.cinepick.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LoginService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder; // 비밀번호 암호화 도구

    @Transactional
    public void registerMember(SignUpReqDto reqDto) {
        // 1. 비밀번호 확인 일치 여부
        if (!reqDto.getPassword().equals(reqDto.getPasswordConfirm())) {
            throw new IllegalArgumentException("비밀번호가 일치하지 않습니다.");
        }

        // 2. 이메일 중복 체크
        if (memberRepository.findByEmail(reqDto.getEmail()).isPresent()) {
            throw new IllegalArgumentException("이미 가입된 이메일입니다.");
        }

        // 3. 비밀번호 암호화 및 Member 생성
        Member newMember = new Member();
        newMember.setEmail(reqDto.getEmail());
        newMember.setNickname(reqDto.getNickname());
        // 암호화된 비밀번호만 저장
        newMember.setPassword(passwordEncoder.encode(reqDto.getPassword()));

        // 4. DB에 저장
        memberRepository.save(newMember);
    }
}