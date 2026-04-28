package com.readb.adapter.llm;

public interface LlmAdapter {

    /**
     * 프롬프트를 LLM에 전달하고 JSON 문자열 응답을 받는다.
     * 구현체 교체 시 이 인터페이스만 유지하면 상위 로직 변경 없음.
     *
     * @param systemPrompt  LLM에게 줄 시스템 역할 지시
     * @param userPrompt    실제 분석 요청 (전사 텍스트 포함)
     * @return JSON 문자열 (파싱은 호출 측에서 수행)
     */
    String chat(String systemPrompt, String userPrompt);
}
