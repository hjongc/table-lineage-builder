package com.tablelineagebuilder.client;

import com.tablelineagebuilder.config.Config;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * LLM API 호출을 담당하는 추상 클래스
 * gpt-4o 계열과 o3 계열의 호출 방식이 다르므로 분리
 */
public abstract class LLMClient {

    protected final String serverUrl;
    protected final String modelName;
    protected final int timeoutMs;
    protected final double temperature;
    protected final int maxTokens;
    protected final int maxCompletionTokens;

    public LLMClient() {
        this.serverUrl = Config.getLlmServerUrl();
        this.modelName = Config.getLlmModelName();
        this.timeoutMs = Config.getLlmTimeout();
        this.temperature = Config.getLlmTemperature();
        this.maxTokens = Config.getLlmMaxTokens();
        this.maxCompletionTokens = Config.getLlmMaxCompletionTokens();
    }

    /**
     * LLM에 프롬프트를 보내고 응답을 받음
     */
    public abstract String call(String systemPrompt, String userPrompt) throws IOException;

    /**
     * 응답 JSON에서 assistant content 추출
     */
    protected String extractAssistantContent(String responseJson) {
        if (responseJson == null) return null;

        String key = "\"content\":\"";
        int i = responseJson.indexOf(key);
        if (i < 0) return null;

        int start = i + key.length();
        StringBuilder out = new StringBuilder();
        boolean escape = false;

        for (int p = start; p < responseJson.length(); p++) {
            char c = responseJson.charAt(p);
            if (escape) {
                switch (c) {
                    case 'n': out.append('\n'); break;
                    case 'r': out.append('\r'); break;
                    case 't': out.append('\t'); break;
                    case '"': out.append('"'); break;
                    case '\\': out.append('\\'); break;
                    default: out.append(c); break;
                }
                escape = false;
            } else {
                if (c == '\\') {
                    escape = true;
                } else if (c == '"') {
                    break;
                } else {
                    out.append(c);
                }
            }
        }
        return out.toString();
    }

    /**
     * HTTP POST 요청 실행
     */
    protected String postJson(String urlStr, String body) throws IOException {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        try {
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(timeoutMs);
            conn.setReadTimeout(timeoutMs);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setRequestProperty("Accept", "application/json");

            try (OutputStreamWriter w = new OutputStreamWriter(conn.getOutputStream(), StandardCharsets.UTF_8)) {
                w.write(body);
                w.flush();
            }

            int code = conn.getResponseCode();
            BufferedReader br = new BufferedReader(new InputStreamReader(
                code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream(),
                StandardCharsets.UTF_8
            ));

            StringBuilder resp = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                resp.append(line);
            }
            br.close();

            if (code < 200 || code >= 300) {
                throw new IOException("HTTP " + code + " - " + conn.getResponseMessage() + " | " + resp);
            }

            return resp.toString();
        } finally {
            conn.disconnect();
        }
    }

    /**
     * JSON 문자열 이스케이프
     */
    protected String jsonEscape(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length() + 64);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int)c));
                    else sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * 팩토리 메서드: 모델명에 따라 적절한 클라이언트 반환
     */
    public static LLMClient create() {
        String modelName = Config.getLlmModelName();
        if (modelName.startsWith("o3")) {
            return new O3Client();
        } else {
            return new GPT4Client();
        }
    }
}
