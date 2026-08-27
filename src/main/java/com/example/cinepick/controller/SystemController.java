package com.example.cinepick.controller;

import com.example.cinepick.dto.response.StartupStatusResDto;
import com.example.cinepick.service.StartupWarmupService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * 서버 준비 중 보여줄 대기 화면과 그 화면이 물어볼 상태 조회.
 * 두 경로는 준비 전에도 열려 있어야 하므로 StartupGateInterceptor 와 SecurityConfig 양쪽에서 제외된다.
 */
@Controller
@RequiredArgsConstructor
public class SystemController {

    private final StartupWarmupService warmupService;

    @GetMapping("/loading")
    public String loading(@RequestParam(required = false) String next, Model model) {
        String target = safeNext(next);

        // 준비가 끝난 뒤 뒤로가기·즐겨찾기로 들어온 경우. 대기 화면을 보여줄 이유가 없다
        if (warmupService.isReady()) {
            return "redirect:" + target;
        }

        model.addAttribute("next", target);
        return "loading";
    }

    @GetMapping("/system/status")
    @ResponseBody
    public StartupStatusResDto status() {
        return warmupService.status();
    }

    /**
     * next 는 주소창에 노출돼 사용자가 바꿀 수 있다. 외부 주소를 그대로 보내면 열린 리다이렉트가
     * 되므로 내부 경로가 아닌 값은 전부 메인으로 돌린다.
     * ("//evil.com" 은 스킴 없는 외부 주소라 startsWith("/") 만으로는 못 거른다)
     */
    private String safeNext(String next) {
        if (next == null || next.isBlank()) return "/";
        if (!next.startsWith("/") || next.startsWith("//")) return "/";
        return next;
    }
}
