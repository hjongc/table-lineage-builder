package com.tablelineagebuilder.client;

import java.io.IOException;

/**
 * GPT-4o, GPT-4o-mini 계열 모델 호출 클라이언트
 * 표준 Chat Completions API 사용
 */
public class GPT4Client extends LLMClient {

    @Override
    public String call(String systemPrompt, String userPrompt) throws IOException {
        String endpoint = serverUrl + "/v1/chat/completions";
        String requestBody = buildRequestJson(systemPrompt, userPrompt);
        String responseJson = postJson(endpoint, requestBody);
        return extractAssistantContent(responseJson);
    }

    private String buildRequestJson(String systemPrompt, String userPrompt) {
        String sys = jsonEscape(systemPrompt);
        String usr = jsonEscape(userPrompt);

        return "{\n" +
                "  \"model\": \"" + modelName + "\",\n" +
                "  \"messages\": [\n" +
                "    {\"role\":\"system\",\"content\":\"" + sys + "\"},\n" +
                "    {\"role\":\"user\",\"content\":\"" + usr + "\"}\n" +
                "  ],\n" +
                "  \"temperature\": " + temperature + ",\n" +
                "  \"max_tokens\": " + maxTokens + ",\n" +
                "  \"stream\": false\n" +
                "}";
    }
}
