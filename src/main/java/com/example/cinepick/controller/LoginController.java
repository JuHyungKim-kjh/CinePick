package com.example.cinepick.controller;

import com.example.cinepick.dto.request.SignUpReqDto;
import com.example.cinepick.service.LoginService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
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

    /**
     * 3. 회원가입 처리.
     *
     * 실패하면 redirect 가 아니라 같은 화면을 다시 그린다. redirect 로 돌리면 무엇이 잘못됐는지도,
     * 방금 입력한 이메일·닉네임도 함께 사라진다. 비밀번호만 비워서 다시 받는다.
     */
    @PostMapping("/signup")
    public String processSignup(SignUpReqDto signUpReqDto, Model model) {
        String error = loginService.registerMember(signUpReqDto);

        if (error != null) {
            model.addAttribute("errorMessage", error);
            model.addAttribute("form", signUpReqDto);
            return "signup";
        }
        // 성공했을 때만 redirect 한다 (새로고침으로 같은 가입이 두 번 시도되는 것을 막는다)
        return "redirect:/login?joined";
    }
}