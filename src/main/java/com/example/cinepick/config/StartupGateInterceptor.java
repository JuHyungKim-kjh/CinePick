package com.example.cinepick.config;

import com.example.cinepick.service.StartupWarmupService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 준비가 끝나기 전에 들어온 요청을 로딩 화면으로 돌린다.
 * 준비가 끝난 뒤에는 volatile boolean 하나만 읽고 통과한다.
 *
 * <b>모든 요청에 걸리지 않는다.</b> 어디에 거는지는 {@link WebConfig#addInterceptors} 가 정하며,
 * 크롤링 결과가 있어야 제대로 나오는 화면(메인 · 영화 상세)만 대상이다.
 */
@Component
@RequiredArgsConstructor
public class StartupGateInterceptor implements HandlerInterceptor {

    private final StartupWarmupService warmupService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {

        if (warmupService.isReady()) return true;

        // 화면 이동이 아닌 요청(fetch, 폼 전송)에 HTML 을 돌려주면 호출한 쪽이 해석하지 못한다.
        // 상태 코드로 답한다
        if (!"GET".equalsIgnoreCase(request.getMethod()) || wantsJson(request)) {
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            return false;
        }

        // 준비가 끝나면 원래 가려던 곳으로 돌아갈 수 있도록 주소를 들려 보낸다
        String target = request.getRequestURI();
        String query = request.getQueryString();
        if (query != null && !query.isEmpty()) {
            target = target + "?" + query;
        }

        response.sendRedirect("/loading?next=" + URLEncoder.encode(target, StandardCharsets.UTF_8));
        return false;
    }

    /** 브라우저 주소창 이동이 아니라 스크립트가 부른 요청인지 */
    private boolean wantsJson(HttpServletRequest request) {
        if ("XMLHttpRequest".equals(request.getHeader("X-Requested-With"))) return true;

        String accept = request.getHeader("Accept");
        // 브라우저의 화면 요청은 Accept 에 text/html 을 먼저 싣는다
        return accept != null && accept.contains("application/json") && !accept.contains("text/html");
    }
}
