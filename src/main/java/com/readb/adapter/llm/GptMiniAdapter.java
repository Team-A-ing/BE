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
public class GptMiniAdapter implements LlmAdapter {

    private final WebClient webClient;

    @Value("${openai.api-key}")
    private String apiKey;

    @Value("${openai.gpt.url}")
    private String gptUrl;

    @Value("${openai.gpt.model}")
    private String model;

    @Override
    public String chat(String systemPrompt, String userPrompt) {
        Map<String, Object> requestBody = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)
                ),
                "response_format", Map.of("type", "json_object")
        );

        try {
            Map<String, Object> response = webClient.post()
                    .uri(gptUrl)
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
            Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
            String result = (String) message.get("content");
            log.info("GPT-mini 응답 수신. 길이: {}", result.length());
            return result;

        } catch (Exception e) {
            log.error("GPT-mini API 호출 실패", e);
            throw new BusinessException(ErrorCode.ANALYSIS_FAILED);
        }
    }
}
