package com.example.cinepick.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final StartupGateInterceptor startupGateInterceptor;

    /**
     * 시작 게이트를 크롤링 결과가 있어야 제대로 나오는 화면(메인 · 영화 상세)에만 건다.
     * <b>새 화면이 {@code MovieCrawlerScheduler.getCachedResult()} 에 의존하면 그 경로도 여기 더할 것</b>
     * — 빠뜨리면 막히는 게 아니라 빈 목록이나 틀린 상영 상태가 조용히 나온다.
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(startupGateInterceptor)
                .addPathPatterns("/", "/movies/detail/**");
    }
}
