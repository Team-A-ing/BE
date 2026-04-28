package com.readb.adapter.llm;

import com.readb.common.exception.BusinessException;
import com.readb.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ClaudeAdapter implements LlmAdapter {

    private final WebClient webClient;

    @Value("${claude.api-key}")
    private String apiKey;

    @Value("${claude.url}")
    private String claudeUrl;

    @Value("${claude.model}")
    private String model;

    @Value("${claude.max-tokens}")
    private int maxTokens;

    @Override
    public String chat(String systemPrompt, String userPrompt) {
        Map<String, Object> requestBody = Map.of(
                "model", model,
                "max_tokens", maxTokens,
                "system", systemPrompt,
                "messages", List.of(
                        Map.of("role", "user", "content", userPrompt)
                )
        );

        try {
            Map<String, Object> response = webClient.post()
                    .uri(claudeUrl)
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", "2023-06-01")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            List<Map<String, Object>> content = (List<Map<String, Object>>) response.get("content");
            String result = (String) content.get(0).get("text");
            log.info("Claude 응답 수신. 길이: {}", result.length());
            return result;

        } catch (Exception e) {
            log.error("Claude API 호출 실패", e);
            throw new BusinessException(ErrorCode.ANALYSIS_FAILED);
        }
    }
}
