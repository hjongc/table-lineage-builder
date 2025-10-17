package com.tablelineagebuilder;

import com.tablelineagebuilder.agent.QueryExtractorAgent;
import com.tablelineagebuilder.agent.TableAnalyzerAgent;
import com.tablelineagebuilder.model.QueryInfo;
import com.tablelineagebuilder.model.TableLineage;
import com.tablelineagebuilder.repository.TableLineageRepository;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 테이블 계보 구축 메인 클래스
 *
 * 실행 흐름:
 * 1. 파일 경로 입력받기 (단일 파일 또는 파일 목록)
 * 2. QueryExtractorAgent로 SQL 쿼리 추출
 * 3. TableAnalyzerAgent로 테이블 계보 분석 (LLM 사용)
 * 4. TableLineageRepository로 DB에 저장
 *
 * 사용법:
 * - 단일 파일: java -jar table-lineage-builder.jar script.sql
 * - 파일 목록: java -jar table-lineage-builder.jar --list file_list.txt
 */
public class TableLineageBuilderMain {

    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("사용법:");
            System.out.println("  단일 파일: java -jar table-lineage-builder.jar <파일경로>");
            System.out.println("  파일 목록: java -jar table-lineage-builder.jar --list <목록파일>");
            System.out.println();
            System.out.println("예제:");
            System.out.println("  java -jar table-lineage-builder.jar /path/to/sql/file.sql");
            System.out.println("  java -jar table-lineage-builder.jar --list file_list.txt");
            return;
        }

        List<String> filePaths = new ArrayList<>();

        // --list 옵션: 파일 목록 읽기
        if (args.length >= 2 && "--list".equals(args[0])) {
            String listFilePath = args[1];
            filePaths = readFileList(listFilePath);
            if (filePaths.isEmpty()) {
                System.err.println("파일 목록이 비어있거나 읽을 수 없습니다: " + listFilePath);
                return;
            }
        } else {
            // 단일 파일 모드
            filePaths.add(args[0]);
        }

        System.out.println("=== 테이블 계보 추출 시작 ===");
        System.out.println("처리할 파일 개수: " + filePaths.size());
        System.out.println();

        TableLineageRepository repository = null;
        PrintWriter reportWriter = null;

        try {
            // 결과 리포트 파일 생성
            String reportFileName = "result_report_" +
                new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".txt";
            reportWriter = new PrintWriter(new BufferedWriter(new FileWriter(reportFileName)));

            reportWriter.println("================================================================================");
            reportWriter.println("                     테이블 계보 추출 작업 결과 보고서");
            reportWriter.println("================================================================================");
            reportWriter.println("작업 시작 시간: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
            reportWriter.println("총 대상 파일: " + filePaths.size() + "개");
            reportWriter.println("================================================================================");
            reportWriter.println();

            repository = new TableLineageRepository();
            QueryExtractorAgent extractor = new QueryExtractorAgent();
            TableAnalyzerAgent analyzer = new TableAnalyzerAgent();

            int totalFilesProcessed = 0;
            int totalFilesSkipped = 0;
            int totalFilesError = 0;
            int totalQueriesProcessed = 0;
            int totalLineagesSaved = 0;

            List<String> skippedFiles = new ArrayList<>();
            List<String> errorFiles = new ArrayList<>();

            // 각 파일 순차 처리
            for (int fileIndex = 0; fileIndex < filePaths.size(); fileIndex++) {
                String filePath = filePaths.get(fileIndex);
                File file = new File(filePath);

                System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                System.out.println("파일 [" + (fileIndex + 1) + "/" + filePaths.size() + "]: " + filePath);
                System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

                reportWriter.println("--------------------------------------------------------------------------------");
                reportWriter.println("[" + (fileIndex + 1) + "/" + filePaths.size() + "] " + filePath);
                reportWriter.println("--------------------------------------------------------------------------------");

                if (!file.exists()) {
                    System.err.println("⚠ 파일을 찾을 수 없습니다. 건너뜁니다.");
                    System.out.println();

                    reportWriter.println("상태: SKIP (파일 없음)");
                    reportWriter.println();

                    totalFilesSkipped++;
                    skippedFiles.add(filePath);
                    continue;
                }

                try {
                    // 1. 쿼리 추출
                    System.out.println("[1단계] 쿼리 추출 중...");
                    List<QueryInfo> queries = extractor.extractQueries(filePath);
                    System.out.println("추출된 쿼리 개수: " + queries.size());

                    // 계보 분석이 필요한 쿼리만 필터링
                    List<QueryInfo> analyzeQueries = new ArrayList<>();
                    int skippedQueryCount = 0;
                    for (QueryInfo query : queries) {
                        if (query.needsLineageAnalysis()) {
                            analyzeQueries.add(query);
                        } else {
                            skippedQueryCount++;
                        }
                    }

                    System.out.println("분석 대상 쿼리: " + analyzeQueries.size() + "개 (건너뜀: " + skippedQueryCount + "개)");
                    System.out.println();

                    reportWriter.println("추출된 쿼리 개수: " + queries.size());
                    reportWriter.println("분석 대상 쿼리: " + analyzeQueries.size() + "개");
                    reportWriter.println("건너뛴 쿼리 (DELETE/SET 등): " + skippedQueryCount + "개");

                    if (analyzeQueries.isEmpty()) {
                        System.out.println("⚠ 분석할 쿼리가 없습니다. 다음 파일로 이동합니다.");
                        System.out.println();

                        reportWriter.println("상태: SKIP (분석 대상 없음)");
                        reportWriter.println();

                        totalFilesSkipped++;
                        skippedFiles.add(filePath);
                        continue;
                    }

                    // 2. 테이블 분석 (필터링된 쿼리만)
                    System.out.println("[2단계] 테이블 계보 분석 중...");
                    List<TableLineage> allLineages = new ArrayList<>();

                    for (int i = 0; i < analyzeQueries.size(); i++) {
                        QueryInfo query = analyzeQueries.get(i);
                        System.out.println("  (" + (i + 1) + "/" + analyzeQueries.size() + ") 분석 중...");

                        List<TableLineage> lineages = analyzer.analyze(query);
                        allLineages.addAll(lineages);

                        // 분석 결과 출력
                        for (TableLineage lineage : lineages) {
                            String relation = lineage.getSourceTable() + " → " + lineage.getTargetTable();
                            System.out.println("    -> " + relation);
                            reportWriter.println("  계보: " + relation);
                        }
                    }

                    System.out.println("이 파일의 추출된 계보: " + allLineages.size() + "개");
                    System.out.println();

                    // 3. DB 저장
                    System.out.println("[3단계] 데이터베이스 저장 중...");
                    repository.saveAll(allLineages);
                    int savedCount = allLineages.size();

                    System.out.println("✓ 저장 완료: " + savedCount + "개");
                    System.out.println();

                    reportWriter.println("저장된 계보: " + savedCount + "개");
                    reportWriter.println("상태: SUCCESS");
                    reportWriter.println();

                    totalFilesProcessed++;
                    totalQueriesProcessed += analyzeQueries.size();
                    totalLineagesSaved += savedCount;

                } catch (Exception e) {
                    System.err.println("✗ 파일 처리 중 오류 발생: " + e.getMessage());
                    e.printStackTrace();
                    System.out.println();

                    reportWriter.println("상태: ERROR");
                    reportWriter.println("오류 메시지: " + e.getMessage());
                    reportWriter.println();

                    totalFilesError++;
                    errorFiles.add(filePath + " (" + e.getMessage() + ")");
                }
            }

            // 최종 요약 (콘솔)
            System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            System.out.println("=== 전체 처리 완료 ===");
            System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            System.out.println("처리 성공: " + totalFilesProcessed + "개");
            System.out.println("건너뜀: " + totalFilesSkipped + "개");
            System.out.println("오류: " + totalFilesError + "개");
            System.out.println("총 파일: " + filePaths.size() + "개");
            System.out.println("처리된 쿼리: " + totalQueriesProcessed + "개");
            System.out.println("저장된 계보: " + totalLineagesSaved + "개");
            System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

            // 최종 요약 (리포트 파일)
            reportWriter.println("================================================================================");
            reportWriter.println("                           전체 작업 요약");
            reportWriter.println("================================================================================");
            reportWriter.println("작업 종료 시간: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
            reportWriter.println();
            reportWriter.println("총 대상 파일: " + filePaths.size() + "개");
            reportWriter.println("  - 처리 성공: " + totalFilesProcessed + "개");
            reportWriter.println("  - 건너뜀: " + totalFilesSkipped + "개");
            reportWriter.println("  - 오류: " + totalFilesError + "개");
            reportWriter.println();
            reportWriter.println("처리된 쿼리 수: " + totalQueriesProcessed + "개");
            reportWriter.println("저장된 계보 수: " + totalLineagesSaved + "개");
            reportWriter.println("================================================================================");

            // 건너뛴 파일 상세 정보
            if (!skippedFiles.isEmpty()) {
                reportWriter.println();
                reportWriter.println("[ 건너뛴 파일 목록 ]");
                reportWriter.println("--------------------------------------------------------------------------------");
                for (String skipped : skippedFiles) {
                    reportWriter.println("  - " + skipped);
                }
            }

            // 오류 발생 파일 상세 정보
            if (!errorFiles.isEmpty()) {
                reportWriter.println();
                reportWriter.println("[ 오류 발생 파일 목록 ]");
                reportWriter.println("--------------------------------------------------------------------------------");
                for (String error : errorFiles) {
                    reportWriter.println("  - " + error);
                }
            }

            reportWriter.println();
            reportWriter.println("================================================================================");
            reportWriter.println("리포트 종료");
            reportWriter.println("================================================================================");

            System.out.println();
            System.out.println("📄 작업 결과 리포트 파일 생성: " + reportFileName);

        } catch (Exception e) {
            System.err.println("오류 발생: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (repository != null) {
                repository.close();
            }
            if (reportWriter != null) {
                reportWriter.close();
            }
        }
    }

    /**
     * 파일 목록을 읽어서 경로 리스트로 반환
     */
    private static List<String> readFileList(String listFilePath) {
        List<String> paths = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(listFilePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                // 빈 줄이나 주석(#으로 시작) 무시
                if (!line.isEmpty() && !line.startsWith("#")) {
                    paths.add(line);
                }
            }
        } catch (Exception e) {
            System.err.println("파일 목록 읽기 실패: " + e.getMessage());
        }

        return paths;
    }
}
