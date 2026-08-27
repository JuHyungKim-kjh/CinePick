package com.example.cinepick.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // 접근 권한
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/css/**", "/images/**", "/js/**").permitAll()
                        // 대기 화면. 준비 전에는 로그인 화면조차 이쪽으로 돌려보내지므로 항상 열려 있어야 한다
                        .requestMatchers("/loading", "/system/**").permitAll()
                        // 고객지원 게시판 관리는 운영자 전용.
                        // 관리 URL 을 /cs/admin 아래로 모아 둔 덕분에 규칙이 이 한 줄로 끝난다 —
                        // 경로마다 막던 때는 하나만 빠뜨려도 그대로 구멍이 됐다
                        .requestMatchers("/cs/admin/**").hasRole("ADMIN")
                        // 회원 전용. /reservations/**, /ratings/** 는 예매 이력·평점을 다루므로
                        // 반드시 로그인 상태여야 한다 (각 서비스에서 소유권도 다시 확인한다)
                        .requestMatchers("/members/**", "/cs/inquiry/**", "/recommendations",
                                "/reservations/**", "/ratings/**").authenticated()

                // 그 외는 게스트 포함 접근 가능
                        .anyRequest().permitAll()
                )

                // 폼 로그인
                .formLogin(login -> login
                        .loginPage("/login")             // 로그인 페이지
                        .usernameParameter("email")        // 로그인 ID로 'email' 파라미터를 사용하도록 설정
                        .passwordParameter("password")
                        .loginProcessingUrl("/login")      // HTML 폼에서 이 URL로 POST 요청을 보내면 시큐리티가 가로채서 로그인 처리
                        .defaultSuccessUrl("/")            // 로그인 성공 시 메인 페이지로 이동
                        .permitAll()
                )

                // 로그아웃
                .logout(logout -> logout
                        .logoutUrl("/logout")              // 로그아웃을 처리할 URL
                        .logoutSuccessUrl("/")             // 로그아웃 성공 시 메인 페이지로 이동
                        .invalidateHttpSession(true)       // 로그아웃 시 세션 초기화
                        .permitAll()
                )

                // CSRF 비활성화 (개발 편의). HTML 폼 전송 시 403 을 피하려고 임시로 꺼둔 상태
                .csrf(csrf -> csrf.disable());

        return http.build();
    }
}