package com.tablelineagebuilder.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tablelineagebuilder.client.LLMClient;
import com.tablelineagebuilder.config.Config;
import com.tablelineagebuilder.model.QueryInfo;
import com.tablelineagebuilder.model.TableLineage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * SQL 쿼리를 분석하여 소스-타겟 테이블 관계를 추출하는 에이전트
 */
public class TableAnalyzerAgent {

    private final LLMClient llmClient;
    private final ObjectMapper objectMapper;

    public TableAnalyzerAgent() {
        this.llmClient = LLMClient.create();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 쿼리를 분석하여 테이블 계보 정보 추출
     */
    public List<TableLineage> analyze(QueryInfo queryInfo) {
        List<TableLineage> lineages = new ArrayList<>();

        try {
            // 쿼리 정보 로깅
            int queryLength = queryInfo.getQueryText().length();
            int estimatedTokens = queryLength / 4;  // 1 토큰 ≈ 4자 (근사치)

            System.out.println("📊 [" + Config.getLlmModelName() + "] 쿼리 분석 시작:");
            System.out.println("   - 파일: " + queryInfo.getFilePath());
            System.out.println("   - 쿼리 길이: " + queryLength + "자 (약 " + estimatedTokens + " 토큰)");

            // 컨텍스트 윈도우 초과 경고
            if (Config.getLlmModelName().startsWith("o3")) {
                // o3-mini: 200K 토큰 컨텍스트
                if (estimatedTokens > 195000) {
                    System.err.println("⚠️  경고: 쿼리가 매우 깁니다 (200K 토큰 근접)");
                    System.err.println("   - 테이블 추출 정확도가 떨어질 수 있습니다.");
                }
            } else {
                // gpt-4o-mini: 128K 토큰 컨텍스트
                if (estimatedTokens > 120000) {
                    System.err.println("⚠️  경고: 쿼리가 너무 깁니다 (128K 토큰 초과 가능)");
                    System.err.println("   - 테이블 추출 정확도가 떨어질 수 있습니다.");
                }
            }

            String systemPrompt = buildSystemPrompt();
            String userPrompt = buildUserPrompt(queryInfo.getQueryText());

            String response = llmClient.call(systemPrompt, userPrompt);

            // JSON 파싱
            lineages = parseResponse(response, queryInfo);

            System.out.println("   ✅ 추출된 계보: " + lineages.size() + "개");

        } catch (Exception e) {
            System.err.println("❌ 테이블 분석 실패: " + queryInfo.getFilePath());
            System.err.println("   Error: " + e.getMessage());
        }

        return lineages;
    }

    /**
     * 시스템 프롬프트 생성
     */
    private String buildSystemPrompt() {
        return "You are an expert SQL table lineage analyzer. " +
               "Analyze the given SQL statement and extract source tables (FROM) and target tables (INSERT/UPDATE/MERGE destination) accurately, then return as JSON. " +
               "MUST extract only exact table names explicitly written in the SQL - NO guessing, NO abbreviation. " +
               "Aliases, CTE names, and subqueries are NOT table names.";
    }

    /**
     * 사용자 프롬프트 생성
     */
    private String buildUserPrompt(String sql) {
        StringBuilder sb = new StringBuilder();
        sb.append("Analyze the following SQL and return table lineage as JSON.\n\n");
        sb.append("Response format:\n");
        sb.append("{\"lineages\":[\n");
        sb.append("  {\"sourceTable\":\"SOURCE_TABLE_NAME\", \"targetTable\":\"TARGET_TABLE_NAME\"},\n");
        sb.append("  ...\n");
        sb.append("]}\n\n");
        sb.append("CRITICAL RULES:\n");
        sb.append("1) Identify target tables accurately: INSERT/UPDATE/MERGE/CREATE TABLE AS SELECT\n");
        sb.append("2) Extract ONLY actual table names from FROM/JOIN clauses:\n");
        sb.append("   - Aliases are NOT table names (e.g., FROM table_name t ← exclude 't', extract 'table_name')\n");
        sb.append("   - CTEs (WITH clause) are NOT table names\n");
        sb.append("   - Subqueries are NOT table names\n");
        sb.append("3) Remove schema names, use only table names (e.g., SCHEMA.TABLE → TABLE)\n");
        sb.append("4) Exclude DUAL table\n");
        sb.append("5) **Extract EXACT table names ONLY**: Do NOT create non-existent table names\n");
        sb.append("   - Example: If MMAP_COMM_CD_DTL_C exists → MMAP_COMM_CD_DTL_C (exact)\n");
        sb.append("   - Example: If MMAP_COMM_CD_D does NOT exist in SQL → Do NOT extract it\n");
        sb.append("   - Do NOT abbreviate or guess table names\n");
        sb.append("6) Create separate entries for multiple source tables\n");
        sb.append("7) Remove duplicates\n");
        sb.append("8) If only SELECT without INSERT/UPDATE, return empty array: {\"lineages\":[]}\n");
        sb.append("9) Output ONLY JSON, no code fences (```) or explanations\n\n");
        sb.append("VERIFICATION:\n");
        sb.append("- Double-check that ALL returned table names actually exist in the SQL below\n");
        sb.append("- Verify exact spelling of table names\n");
        sb.append("- Do NOT invent table names based on assumptions or guesses\n\n");
        sb.append("[SQL]\n");
        sb.append(sql);

        return sb.toString();
    }

    /**
     * LLM 응답을 파싱하여 TableLineage 리스트로 변환
     */
    private List<TableLineage> parseResponse(String response, QueryInfo queryInfo) {
        List<TableLineage> lineages = new ArrayList<>();
        Set<String> dedup = new HashSet<>();

        try {
            if (response == null || response.trim().isEmpty()) {
                System.err.println("⚠️  LLM 응답이 비어있습니다.");
                return lineages;
            }

            // 코드펜스 제거 (다양한 형식 지원)
            String cleaned = response
                .replace("```json", "")
                .replace("```JSON", "")
                .replace("```", "")
                .trim();

            // JSON 시작 위치 찾기 (설명 텍스트가 앞에 있을 수 있음)
            int jsonStart = cleaned.indexOf("{");
            if (jsonStart > 0) {
                cleaned = cleaned.substring(jsonStart);
            }

            // JSON 끝 위치 찾기 (설명 텍스트가 뒤에 있을 수 있음)
            int jsonEnd = cleaned.lastIndexOf("}");
            if (jsonEnd > 0 && jsonEnd < cleaned.length() - 1) {
                cleaned = cleaned.substring(0, jsonEnd + 1);
            }

            System.out.println("   🔍 파싱할 JSON: " + cleaned.substring(0, Math.min(200, cleaned.length())) + "...");

            JsonNode rootNode = objectMapper.readTree(cleaned);
            JsonNode lineagesNode = rootNode.path("lineages");

            if (!lineagesNode.isArray()) {
                System.err.println("⚠️  'lineages' 필드가 배열이 아닙니다.");
                System.err.println("   Response: " + cleaned);
                return lineages;
            }

            System.out.println("   📋 LLM이 추출한 테이블: " + lineagesNode.size() + "개");

            for (JsonNode node : lineagesNode) {
                String sourceTable = node.path("sourceTable").asText("").trim().toUpperCase();
                String targetTable = node.path("targetTable").asText("").trim().toUpperCase();

                // 빈 값 체크
                if (sourceTable.isEmpty() || targetTable.isEmpty()) {
                    System.out.println("   ⏭️  빈 테이블명 건너뜀");
                    continue;
                }

                System.out.println("   🔎 검증 중: " + sourceTable + " → " + targetTable);

                // *** 추가 검증: SQL에 실제로 존재하는 테이블명인지 확인 ***
                if (!isTableInQuery(sourceTable, queryInfo.getQueryText())) {
                    System.err.println("   ❌ 검증 실패: 소스 테이블 '" + sourceTable + "'가 SQL에 없습니다. 건너뜁니다.");
                    continue;
                }

                if (!isTableInQuery(targetTable, queryInfo.getQueryText())) {
                    System.err.println("   ❌ 검증 실패: 타겟 테이블 '" + targetTable + "'가 SQL에 없습니다. 건너뜁니다.");
                    continue;
                }

                // 중복 체크
                String key = sourceTable + "|" + targetTable;
                if (dedup.contains(key)) {
                    System.out.println("   ⏭️  중복 건너뜀: " + sourceTable + " → " + targetTable);
                    continue;
                }
                dedup.add(key);

                System.out.println("   ✅ 검증 성공: " + sourceTable + " → " + targetTable);

                // TableLineage 생성
                lineages.add(new TableLineage(
                    sourceTable,
                    targetTable,
                    queryInfo.getFilePath(),
                    queryInfo.getQueryText(),
                    Config.getLlmModelName()
                ));
            }

        } catch (Exception e) {
            System.err.println("❌ JSON 파싱 실패: " + e.getMessage());
            System.err.println("   Exception: " + e.getClass().getName());
            System.err.println("   Full Response:");
            System.err.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            System.err.println(response);
            System.err.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        }

        return lineages;
    }

    /**
     * 테이블명이 SQL 쿼리에 실제로 존재하는지 검증
     * 정규식을 사용하여 단어 경계(\b)를 기준으로 완전 일치만 허용
     * 예: "MMAP_COMM_CD_D"가 "MMAP_COMM_CD_DTL"과 매칭되지 않도록
     */
    private boolean isTableInQuery(String tableName, String query) {
        if (tableName == null || query == null) {
            return false;
        }

        String upperQuery = query.toUpperCase();
        String upperTable = tableName.toUpperCase();

        // 정규식으로 단어 경계 체크
        // \b는 단어 경계를 의미 (공백, 콤마, 괄호, 개행, 탭 등)
        // Pattern.quote()로 특수문자 이스케이프
        String regex = "\\b" + Pattern.quote(upperTable) + "\\b";
        Pattern pattern = Pattern.compile(regex);

        if (pattern.matcher(upperQuery).find()) {
            return true;
        }

        // 스키마.테이블 형식 체크 (예: SCHEMA.TABLE_NAME)
        // 점(.) 뒤에 테이블명이 오는 경우
        regex = "\\." + Pattern.quote(upperTable) + "\\b";
        pattern = Pattern.compile(regex);

        if (pattern.matcher(upperQuery).find()) {
            return true;
        }

        return false;
    }
}
