package com.lineage.llm;
import java.io.BufferedReader;
import java.io.OutputStreamWriter;
import java.io.InputStreamReader;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
/**
 * openAIClientVer3
 * - SQL 리스트를 받아 LLM 서버에 하나씩 보내고, 결과(JSON 문자열)를 그대로 수집해서 반환
 * - 최대한 간단/직관적으로 작성
 */
public class OpenAIClientVer3 {
    // ---- 설정값(필요 시 수정) ----
//    private static final String SERVER_URL = "http://150.6.15.80:9393/v1/chat/completions";
//    private static final String MODEL_NAME = "gpt-4o-mini";
//    private static final int TIMEOUT_MS   = 6000000;   // 60초
//    private static final double TEMPERATURE = 0.2;    // 출력 안정성 위해 낮춤
//    private static final int MAX_TOKENS     = 16384;
    
 private static final String SERVER_URL = "http://150.6.15.80:9393/v1/chat/completions-o3mini";
 private static final String MODEL_NAME = "o3-mini";
 private static final int TIMEOUT_MS = 6000000;
 private static final double TEMPERATURE = 0.1;
 private static final int MAX_TOKENS = 100000;
    // LLM에 줄 시스템 프롬프트(간단 버전)
    private static final String SYSTEM_PROMPT =
        "당신은 SQL 컬럼 계보 분석 전문가입니다. " +
        "입력된 SQL 문장을 분석하여 타겟 컬럼과 소스 컬럼 간의 데이터 흐름을 JSON으로만 반환하세요.";
    // 사용자 프롬프트 템플릿(간단 버전)
 private static String buildUserPrompt(String sql) {
  StringBuilder sb = new StringBuilder(512 + sql.length());
  sb.append("다음 SQL 문장을 분석하여 컬럼 계보 정보를 JSON으로만 반환하세요.\n").append("\n").append("응답 예시 형식(정확히 이 키만 사용):\n").append(
    "{\"lineages\":[{\"targetTable\":\"...\",\"targetColumn\":\"...\",\"sourceTable\":\"...\",\"sourceColumn\":\"...\"}]}\n")
    .append("\n").append("규칙:\n")
    .append("1) INSERT ... SELECT: INSERT 타겟 컬럼 ↔ SELECT 소스 표현식의 원본 컬럼으로 매핑을 생성\n")
    .append("2) UPDATE ... SET: SET 절의 타겟 컬럼 ↔ 우변(표현식)의 원본 컬럼으로 매핑을 생성\n")
    .append("3) MERGE INTO T USING S ON ...:\n").append("   - UPDATE THEN: SET 절의 타겟 컬럼들만 매핑을 생성\n")
    .append("   - NOT MATCHED INSERT: INSERT 절의 타겟 컬럼들만 매핑을 생성\n")
    .append("   - ON 조건만으로는 매핑을 생성하지 않음(단, 그 컬럼이 SET/INSERT 대상이면 생성)\n")
    .append("4) 상수(숫자/리터럴/날짜 등)는 추출하지 말 것\n")
    .append("5) 스키마/카탈로그/변수 접두어(예: SCHEMA.TBL, $VAR.TBL)는 제거하고, 실제 테이블명만 사용\n")
    .append("6) sourceTable에 alias/CTE/서브쿼리 이름(A, B, SRC, SUB 등)을 절대 넣지 말 것.\n")
    .append("   - alias가 가리키는 실제(리프) 테이블명을 찾아서 적을 것\n")
    .append("   - 중간 뷰/파생 서브쿼리는 건너뛰고 해당 컬럼의 근원 테이블을 적을 것\n")
    .append("7) sourceTable이 DUAL인 경우 해당 매핑을 생성하지 말 것\n")
    .append("8) MERGE의 USING이 없거나 DUAL만 참조한다면 전체 매핑을 생성하지 말 것\n")
    .append("9) 함수/표현식(집계, CASE, NVL 등)이 있어도 표현식 안에서 실제로 참조한 원본 컬럼만 추출\n")
    .append("10) 타겟 테이블을 자기 자신이 참조하여 업데이트하는 경우, sourceTable은 null이 아니라 타겟 테이블명으로 명시\n")
    .append("11) 따옴표/백틱 등 식별자 인용부호는 제거하고 원본 SQL의 컬럼명 표기(대/소문자)는 가능한 한 유지\n")
    .append("12) 중복 매핑 제거(완전히 동일한 {targetTable, targetColumn, sourceTable, sourceColumn}는 한 번만)\n")
    .append("13) 여러 소스 컬럼으로 계산되면 각 소스 컬럼별로 별도의 매핑 항목을 생성\n")
    .append("14) 결과는 유효한 JSON 한 줄로만 출력(코드펜스/주석/설명/빈 줄 금지)\n")
    .append("15) 매핑이 전혀 없으면 {\"lineages\":[]}를 출력\n").append("\n").append("출력 검증 체크리스트(모델 내부 점검용):\n")
    .append("- sourceTable에 A/B 같은 alias가 들어갔는가? → 들어갔다면 실제 테이블명으로 치환\n")
    .append("- sourceTable에 SCHEMA. 또는 $VAR. 접두어가 남았는가? → 제거\n").append("- 상수/DUAL 유래 컬럼이 포함되었는가? → 제외\n")
    .append("- 키는 lineages/targetTable/targetColumn/sourceTable/sourceColumn만 썼는가? → 예\n").append("\n")
    .append("[SQL 시작]\n").append(sql);
  return sb.toString();
 }
    /**
     * SQL 리스트를 받아 LLM 응답(assistant content)을 그대로 리스트로 반환
     */
    public static List<String> analyzeAll(List<String> sqlStatements) {
        List<String> results = new ArrayList<>();
        for (int i = 0; i < sqlStatements.size(); i++) {
            String sql = sqlStatements.get(i);
            try {
                String prompt = buildUserPrompt(sql);
                String requestBody = buildRequestJson(SYSTEM_PROMPT, prompt);
                String responseJson = postJson(SERVER_URL, requestBody, TIMEOUT_MS);
                String assistantContent = extractAssistantContent(responseJson);
                // LLM이 JSON만 주도록 요청했지만, 혹시 몰라 트림
                results.add(assistantContent != null ? assistantContent.trim() : responseJson);
            } catch (Exception e) {
                results.add("{\"error\":\"LLM 호출 실패: " + safe(e.getMessage()) + "\"}");
            }
        }
        return results;
    }
    
     
    // ---- 아래는 내부 유틸들(단순 구현) ----
    // OpenAI Chat Completions 형식 요청 JSON 생성
    private static String buildRequestJson(String systemPrompt, String userPrompt) {
        String sys = jsonEscape(systemPrompt);
        String usr = jsonEscape(userPrompt);
        return "{\n" +
                "  \"model\": \"" + MODEL_NAME + "\",\n" +
                "  \"messages\": [\n" +
                "    {\"role\":\"system\",\"content\":\"" + sys + "\"},\n" +
                "    {\"role\":\"user\",\"content\":\"" + usr + "\"}\n" +
                "  ],\n" +
                "  \"temperature\": " + TEMPERATURE + ",\n" +
                "  \"max_tokens\": " + MAX_TOKENS + ",\n" +
                "  \"stream\": false\n" +
                "}";
    }
    // HTTP POST(JSON) – 가장 단순한 버전
    private static String postJson(String urlStr, String body, int timeoutMs) throws IOException {
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
            while ((line = br.readLine()) != null) resp.append(line);
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
     * 응답 JSON에서 assistant의 content만 간단 추출(정식 JSON 파서 없이)
     * - 실제 운영에서는 Jackson/Gson 사용 권장
     */
    private static String extractAssistantContent(String responseJson) {
        if (responseJson == null) return null;
        // 매우 단순 추출: "content":" ... " 의 첫 번째 매치를 노림
        // (이스케이프 처리된 따옴표가 있을 수 있으므로 완벽하진 않음)
        String key = "\"content\":\"";
        int i = responseJson.indexOf(key);
        if (i < 0) return null;
        int start = i + key.length();
        StringBuilder out = new StringBuilder();
        boolean escape = false;
        for (int p = start; p < responseJson.length(); p++) {
            char c = responseJson.charAt(p);
            if (escape) {
                // 간단 이스케이프 해제
                switch (c) {
                    case 'n': out.append('\n'); break;
                    case 'r': out.append('\r'); break;
                    case 't': out.append('\t'); break;
                    case '"': out.append('"');  break;
                    case '\\': out.append('\\'); break;
                    default: out.append(c); break;
                }
                escape = false;
            } else {
                if (c == '\\') {
                    escape = true;
                } else if (c == '"') {
                    // content 끝
                    break;
                } else {
                    out.append(c);
                }
            }
        }
        return out.toString();
    }
    // JSON 안전 이스케이프(간단 버전)
    private static String jsonEscape(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length() + 64);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':  sb.append("\\\""); break;
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
    private static String safe(String s) {
        return s == null ? "" : s.replace("\"","'");
    }
}
