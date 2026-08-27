package com.example.cinepick.security;

import com.example.cinepick.domain.Member;
import com.example.cinepick.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final MemberRepository memberRepository;

    // 시큐리티가 로그인 요청을 받으면 이 메서드를 호출한다
    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {

        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("가입되지 않은 이메일입니다: " + email));

        // 권한은 Member.role 컬럼을 그대로 쓴다.
        // roles(...) 가 아닌 authorities(...) 인 이유: roles 는 앞에 ROLE_ 을 자동으로 붙이는데
        // 컬럼에 이미 "ROLE_USER" 가 들어 있어 ROLE_ROLE_USER 가 된다
        return User.builder()
                .username(member.getEmail())
                .password(member.getPassword()) // DB에 저장된 '암호화된 비밀번호'를 그대로 넘겨줌 (시큐리티가 알아서 비교함)
                .authorities(member.getRole())
                .build();
    }
}