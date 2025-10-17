package com.tablelineagebuilder.model;

/**
 * 추출된 SQL 쿼리 정보를 담는 모델
 */
public class QueryInfo {
    private String filePath;
    private String queryText;
    private boolean needsLineageAnalysis;

    public QueryInfo(String filePath, String queryText) {
        this.filePath = filePath;
        this.queryText = queryText;
        this.needsLineageAnalysis = determineIfNeedsAnalysis(queryText);
    }

    /**
     * 테이블 계보 분석이 필요한 쿼리인지 판단
     */
    private boolean determineIfNeedsAnalysis(String query) {
        if (query == null || query.trim().isEmpty()) {
            return false;
        }

        String normalized = query.trim().toUpperCase();

        // 계보 분석이 필요한 쿼리 타입
        // INSERT, UPDATE, MERGE, CREATE TABLE AS SELECT
        if (normalized.startsWith("INSERT") ||
            normalized.startsWith("UPDATE") ||
            normalized.startsWith("MERGE") ||
            (normalized.startsWith("CREATE") && normalized.contains("AS SELECT"))) {
            return true;
        }

        // 계보 분석이 불필요한 쿼리 타입
        // DELETE, SET, TRUNCATE, DROP, ALTER (테이블 구조 변경만), SELECT만 있는 경우
        return false;
    }

    public String getFilePath() {
        return filePath;
    }

    public String getQueryText() {
        return queryText;
    }

    public boolean needsLineageAnalysis() {
        return needsLineageAnalysis;
    }

    @Override
    public String toString() {
        return "QueryInfo{filePath='" + filePath + "', queryLength=" + queryText.length() +
               ", needsAnalysis=" + needsLineageAnalysis + "}";
    }
}
