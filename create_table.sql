-- ====================================================================
-- Table Lineage 테이블 생성 스크립트 (MySQL 8.0+)
-- ====================================================================
-- 설명: 테이블 계보 정보를 저장하는 테이블
--       1개의 타겟 테이블에 여러 소스 테이블이 매핑될 수 있음 (다대다 관계)
-- ====================================================================

-- 기존 테이블이 있다면 삭제 (주의: 데이터 손실 발생)
-- DROP TABLE IF EXISTS midp_project.table_lineage;

-- 테이블 생성
CREATE TABLE IF NOT EXISTS midp_project.table_lineage (
    id INT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '고유 ID',
    source_table VARCHAR(255) NOT NULL COMMENT '원천 테이블명',
    target_table VARCHAR(255) NOT NULL COMMENT '타겟 테이블명',
    file_path VARCHAR(500) DEFAULT NULL COMMENT '분석한 파일 경로',
    query_text MEDIUMTEXT DEFAULT NULL COMMENT '실제 SQL 쿼리 (최대 16MB)',
    model_used VARCHAR(50) DEFAULT NULL COMMENT '사용한 LLM 모델명',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일시',
    PRIMARY KEY (id),
    INDEX idx_source_table (source_table),
    INDEX idx_target_table (target_table),
    INDEX idx_source_target (source_table, target_table),
    INDEX idx_created_at (created_at),
    INDEX idx_model_used (model_used),
    UNIQUE INDEX uk_source_target_file (source_table, target_table, file_path(255))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='테이블 계보 정보';

-- ====================================================================
-- 테이블 및 인덱스 확인 쿼리
-- ====================================================================

-- 테이블 구조 확인
-- DESCRIBE midp_project.table_lineage;

-- 인덱스 확인
-- SHOW INDEX FROM midp_project.table_lineage;

-- 테이블 상태 확인
-- SHOW TABLE STATUS LIKE 'table_lineage';
