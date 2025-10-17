package com.tablelineagebuilder.agent;

import com.tablelineagebuilder.model.QueryInfo;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 파일에서 SQL 쿼리를 추출하는 에이전트
 * - 주석 제거
 * - SQL 문장만 추출 (쉘 명령어, 프로그램 코드 제외)
 */
public class QueryExtractorAgent {

    /**
     * 파일에서 SQL 쿼리들을 추출
     */
    public List<QueryInfo> extractQueries(String filePath) throws IOException {
        List<QueryInfo> queries = new ArrayList<>();

        // 1. 파일 읽기 및 주석 제거
        String contentWithoutComments = removeComments(filePath);

        // 2. SQL 문장 추출
        List<String> sqlStatements = extractSqlStatements(contentWithoutComments);

        // 3. QueryInfo 객체로 변환
        for (String sql : sqlStatements) {
            queries.add(new QueryInfo(filePath, sql));
        }

        return queries;
    }

    /**
     * 파일에서 주석 제거 (블록 주석 포함)
     */
    private String removeComments(String filePath) throws IOException {
        StringBuilder result = new StringBuilder();
        boolean inBlockComment = false;

        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                // '#'으로 시작하는 줄 제거
                if (line.trim().startsWith("#")) {
                    continue;
                }

                // 블록 주석 및 라인 주석 제거
                line = removeCommentsFromLine(line, inBlockComment);

                // 블록 주석 상태 업데이트
                inBlockComment = updateBlockCommentState(line, inBlockComment);

                if (!line.trim().isEmpty()) {
                    result.append(line).append(System.lineSeparator());
                }
            }
        }

        return result.toString();
    }

    /**
     * 한 줄에서 주석 제거 (블록 주석 포함)
     */
    private String removeCommentsFromLine(String line, boolean inBlockComment) {
        StringBuilder cleaned = new StringBuilder();
        int i = 0;
        boolean currentlyInBlock = inBlockComment;

        while (i < line.length()) {
            // 블록 주석 종료 확인
            if (currentlyInBlock) {
                int endIndex = line.indexOf("*/", i);
                if (endIndex != -1) {
                    i = endIndex + 2;
                    currentlyInBlock = false;
                } else {
                    // 이 줄 전체가 블록 주석 안
                    return "";
                }
                continue;
            }

            // 블록 주석 시작 확인
            if (i < line.length() - 1 && line.charAt(i) == '/' && line.charAt(i + 1) == '*') {
                currentlyInBlock = true;
                i += 2;
                continue;
            }

            // 라인 주석 확인
            if (i < line.length() - 1 && line.charAt(i) == '-' && line.charAt(i + 1) == '-') {
                // 나머지 라인은 주석
                break;
            }

            // 일반 문자
            cleaned.append(line.charAt(i));
            i++;
        }

        return cleaned.toString();
    }

    /**
     * 블록 주석 상태 업데이트 (다음 줄을 위해)
     */
    private boolean updateBlockCommentState(String line, boolean currentState) {
        boolean inBlock = currentState;
        int i = 0;

        while (i < line.length()) {
            if (!inBlock && i < line.length() - 1 && line.charAt(i) == '/' && line.charAt(i + 1) == '*') {
                inBlock = true;
                i += 2;
                continue;
            }
            if (inBlock && i < line.length() - 1 && line.charAt(i) == '*' && line.charAt(i + 1) == '/') {
                inBlock = false;
                i += 2;
                continue;
            }
            i++;
        }

        return inBlock;
    }

    /**
     * 텍스트에서 SQL 문장만 추출
     */
    private List<String> extractSqlStatements(String content) {
        List<String> sqlStatements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inStatement = false;

        for (String line : content.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            // SQL 시작 키워드 감지 (대소문자 무시)
            if (!inStatement && isSqlStartKeyword(trimmed)) {
                inStatement = true;
            }

            if (inStatement) {
                current.append(line).append(System.lineSeparator());

                // 세미콜론으로 문장 종료
                if (trimmed.endsWith(";")) {
                    sqlStatements.add(current.toString().trim());
                    current.setLength(0);
                    inStatement = false;
                }
            }
        }

        // 마지막 문장이 세미콜론 없이 끝난 경우
        if (current.length() > 0) {
            sqlStatements.add(current.toString().trim());
        }

        return sqlStatements;
    }

    /**
     * SQL 시작 키워드 체크
     */
    private boolean isSqlStartKeyword(String line) {
        return line.matches("(?i)^(SELECT|INSERT|UPDATE|DELETE|MERGE|CREATE|ALTER|DROP|TRUNCATE|WITH).*");
    }
}
