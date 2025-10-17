package com.tablelineagebuilder.repository;

import com.tablelineagebuilder.config.Config;
import com.tablelineagebuilder.model.TableLineage;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

/**
 * 테이블 계보 정보를 DB에 저장하는 Repository
 */
public class TableLineageRepository {

    private Connection connection;

    public TableLineageRepository() throws SQLException {
        try {
            Class.forName(Config.getMysqlDriver());
            this.connection = DriverManager.getConnection(
                Config.getMysqlUrl(),
                Config.getMysqlUsername(),
                Config.getMysqlPassword()
            );
        } catch (ClassNotFoundException e) {
            throw new SQLException("MySQL 드라이버를 로드할 수 없습니다: " + e.getMessage());
        }
    }

    /**
     * 테이블 계보 정보 저장
     * UNIQUE 제약으로 중복 시 업데이트
     */
    public void save(TableLineage lineage) throws SQLException {
        String sql = "INSERT INTO midp_project.table_lineage " +
                     "(source_table, target_table, file_path, query_text, model_used, created_at, updated_at) " +
                     "VALUES (?, ?, ?, ?, ?, NOW(), NOW()) " +
                     "ON DUPLICATE KEY UPDATE " +
                     "query_text = VALUES(query_text), " +
                     "model_used = VALUES(model_used), " +
                     "updated_at = NOW()";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, lineage.getSourceTable());
            pstmt.setString(2, lineage.getTargetTable());
            pstmt.setString(3, lineage.getFilePath());

            // query_text가 너무 길면 잘라냄 (TEXT 타입 최대 65535자)
            String queryText = lineage.getQueryText();
            if (queryText != null && queryText.length() > 60000) {
                queryText = queryText.substring(0, 60000);
            }
            pstmt.setString(4, queryText);
            pstmt.setString(5, lineage.getModelUsed());

            pstmt.executeUpdate();
        }
    }

    /**
     * 여러 개의 계보 정보를 배치로 저장 (성능 최적화)
     * 개수 또는 시간 기준으로 배치 실행
     */
    public void saveAll(List<TableLineage> lineages) throws SQLException {
        if (lineages == null || lineages.isEmpty()) {
            return;
        }

        String sql = "INSERT INTO midp_project.table_lineage " +
                     "(source_table, target_table, file_path, query_text, model_used, created_at, updated_at) " +
                     "VALUES (?, ?, ?, ?, ?, NOW(), NOW()) " +
                     "ON DUPLICATE KEY UPDATE " +
                     "query_text = VALUES(query_text), " +
                     "model_used = VALUES(model_used), " +
                     "updated_at = NOW()";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            int batchCount = 0;
            long batchStartTime = System.currentTimeMillis();

            final int BATCH_SIZE = 100;  // 배치 크기 (100개로 축소)
            final long BATCH_TIME_MS = 5000;  // 배치 시간 제한 (5초)

            for (TableLineage lineage : lineages) {
                pstmt.setString(1, lineage.getSourceTable());
                pstmt.setString(2, lineage.getTargetTable());
                pstmt.setString(3, lineage.getFilePath());

                // query_text가 너무 길면 잘라냄 (MEDIUMTEXT 최대 16MB)
                String queryText = lineage.getQueryText();
                if (queryText != null && queryText.length() > 60000) {
                    queryText = queryText.substring(0, 60000);
                }
                pstmt.setString(4, queryText);
                pstmt.setString(5, lineage.getModelUsed());

                pstmt.addBatch();
                batchCount++;

                long elapsedTime = System.currentTimeMillis() - batchStartTime;

                // 배치 실행 조건: 개수(100개) 또는 시간(5초)
                if (batchCount >= BATCH_SIZE || elapsedTime >= BATCH_TIME_MS) {
                    pstmt.executeBatch();
                    pstmt.clearBatch();

                    // 배치 카운터 및 시간 리셋
                    batchCount = 0;
                    batchStartTime = System.currentTimeMillis();
                }
            }

            // 남은 배치 실행
            if (batchCount > 0) {
                pstmt.executeBatch();
            }
        }
    }

    /**
     * 연결 종료
     */
    public void close() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                System.err.println("DB 연결 종료 실패: " + e.getMessage());
            }
        }
    }
}
