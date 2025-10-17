package com.tablelineagebuilder.client;

import java.io.IOException;

/**
 * o3, o3-mini 계열 모델 호출 클라이언트
 * 별도 엔드포인트 사용 (기존 코드 참고)
 */
public class O3Client extends LLMClient {

    @Override
    public String call(String systemPrompt, String userPrompt) throws IOException {
        // o3-mini는 별도 엔드포인트 사용
        String endpoint = serverUrl + "/v1/chat/completions-o3mini";
        String requestBody = buildRequestJson(systemPrompt, userPrompt);
        String responseJson = postJson(endpoint, requestBody);
        return extractAssistantContent(responseJson);
    }

    private String buildRequestJson(String systemPrompt, String userPrompt) {
        String sys = jsonEscape(systemPrompt);
        String usr = jsonEscape(userPrompt);

        // o3 계열은 temperature 미지원, max_completion_tokens 사용
        return "{\n" +
                "  \"model\": \"" + modelName + "\",\n" +
                "  \"messages\": [\n" +
                "    {\"role\":\"system\",\"content\":\"" + sys + "\"},\n" +
                "    {\"role\":\"user\",\"content\":\"" + usr + "\"}\n" +
                "  ],\n" +
                "  \"max_completion_tokens\": " + maxCompletionTokens + ",\n" +
                "  \"stream\": false\n" +
                "}";
    }
}
