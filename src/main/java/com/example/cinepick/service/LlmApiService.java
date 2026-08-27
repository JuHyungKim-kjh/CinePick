package com.example.cinepick.service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.example.cinepick.domain.Movie;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Claude API 와 통신해 영화의 감정적 분위기를 판단한다.
 * API 키가 비어 있으면 항상 빈 결과를 돌려주고, 호출부(LlmMoodTagger)가 장르 기반 분석으로 대체한다.
 */
@Service
public class LlmApiService {

    /** Claude 가 채워서 돌려줄 분위기 점수(0~100). 구조화 출력으로 스키마가 강제된다 */
    public record MoodScores(int cheerful, int tense, int sad, int catharsis, int mystic) {

        /** 값이 0~100 범위를 벗어나지 않도록 다듬는다 */
        MoodScores clamped() {
            return new MoodScores(clamp(cheerful), clamp(tense), clamp(sad), clamp(catharsis), clamp(mystic));
        }

        private static int clamp(int v) {
            return Math.max(0, Math.min(100, v));
        }
    }

    private static final String SYSTEM_PROMPT = """
            너는 영화의 감정적 분위기를 분류하는 도구다.
            주어진 영화가 관객에게 주는 감정적 인상을 5개 축으로 0~100점씩 매겨라.

            - cheerful(유쾌): 밝고 즐거우며 웃음을 주는 정도
            - tense(긴장): 조마조마하고 몰입하게 만드는 긴장감의 정도
            - sad(슬픔): 먹먹하고 눈물이 나게 하는 정도
            - catharsis(카타르시스): 벅차오르고 후련하게 만드는 해소감의 정도
            - mystic(신비): 신비롭고 낯설며 상상력을 자극하는 정도

            각 축은 서로 독립적이다. 합이 100이 될 필요는 없다.
            가장 두드러지는 축은 80 이상, 거의 없는 축은 20 이하로 매겨라.
            """;

    private final String apiKey;
    private final String model;
    private final AnthropicClient client;

    public LlmApiService(@Value("${api.claude.key:}") String apiKey,
                         @Value("${api.claude.model:claude-opus-5}") String model) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model;
        this.client = this.apiKey.isEmpty() ? null : AnthropicOkHttpClient.builder()
                .apiKey(this.apiKey)
                .build();

        if (this.client == null) {
            System.out.println("[LlmApiService] Claude API 키가 없어 AI 분위기 분석을 사용하지 않습니다. "
                    + "장르 조합 기반 분석으로 대체됩니다.");
        }
    }

    /** 키가 설정되어 있어 실제 호출이 가능한 상태인지 */
    public boolean isEnabled() {
        return client != null;
    }

    /**
     * 영화 한 편의 분위기 분석. 호출이 불가능하거나 실패하면 빈 값을 돌려준다.
     * (예외로 던지지 않는 이유: 한 편이 실패해도 배치 전체가 멈추면 안 된다)
     */
    public Optional<MoodScores> analyzeMood(Movie movie) {
        if (client == null) return Optional.empty();

        try {
            StructuredMessageCreateParams<MoodScores> params = MessageCreateParams.builder()
                    .model(model)
                    // 최신 모델은 max_tokens 를 사고와 응답이 함께 쓴다.
                    // 부족하면 JSON 이 잘려 조용히 실패하므로 사고 몫까지 넉넉히 잡는다
                    .maxTokens(8192L)
                    .system(SYSTEM_PROMPT)
                    .outputConfig(MoodScores.class)
                    .addUserMessage(buildUserPrompt(movie))
                    .build();

            var response = client.messages().create(params);

            // 안전 분류기가 요청을 거절하면 content 가 비어 있으므로 먼저 확인한다
            if (response.stopReason().filter(r -> r.equals(StopReason.REFUSAL)).isPresent()) {
                System.out.println("[LlmApiService] 분석 거절됨: " + movie.getTitle());
                return Optional.empty();
            }
            // 토큰이 모자라 잘리면 JSON 이 깨져 조용히 실패한다. 원인을 알 수 있게 남긴다
            if (response.stopReason().filter(r -> r.equals(StopReason.MAX_TOKENS)).isPresent()) {
                System.out.println("[LlmApiService] 응답이 max_tokens에서 잘렸습니다: " + movie.getTitle());
                return Optional.empty();
            }

            return response.content().stream()
                    .flatMap(block -> block.text().stream())
                    .map(text -> text.text().clamped())
                    .findFirst();

        } catch (Exception e) {
            System.out.println("[LlmApiService] 분위기 분석 실패 (" + movie.getTitle() + "): "
                    + e.getClass().getSimpleName() + " - " + e.getMessage());
            return Optional.empty();
        }
    }

    private String buildUserPrompt(Movie movie) {
        StringBuilder sb = new StringBuilder();
        sb.append("제목: ").append(movie.getTitle()).append('\n');

        if (movie.getGenre() != null && !movie.getGenre().isBlank()) {
            sb.append("장르: ").append(movie.getGenre()).append('\n');
        }
        if (movie.getDirector() != null && !movie.getDirector().isBlank()) {
            sb.append("감독: ").append(movie.getDirector()).append('\n');
        }
        if (movie.getOverview() != null && !movie.getOverview().isBlank()) {
            sb.append("줄거리: ").append(movie.getOverview()).append('\n');
        }
        return sb.toString();
    }
}
