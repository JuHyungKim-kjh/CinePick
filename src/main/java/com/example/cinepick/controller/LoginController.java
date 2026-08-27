package com.example.cinepick.controller;

import com.example.cinepick.dto.request.SignUpReqDto;
import com.example.cinepick.service.LoginService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
@RequiredArgsConstructor
public class LoginController {

    private final LoginService loginService;

    // 1. 로그인 화면
    @GetMapping("/login")
    public String loginPage() {
        return "login"; // login.html 렌더링
    }

    // 2. 회원가입 화면
    @GetMapping("/signup")
    public String signupPage() {
        return "signup"; // signup.html 렌더링
    }

    // 3. 회원가입 처리
    @PostMapping("/signup")
    public String processSignup(SignUpReqDto signUpReqDto) {
        try {
            loginService.registerMember(signUpReqDto);

            return "redirect:/login";
        } catch (IllegalArgumentException e) {
            // TODO 실패 사유를 화면에 표시
            return "redirect:/signup?error";
        }
    }
}