package com.tablelineagebuilder.model;

/**
 * 테이블 간의 계보 정보를 담는 모델
 */
public class TableLineage {
    private String sourceTable;
    private String targetTable;
    private String filePath;
    private String queryText;
    private String modelUsed;

    public TableLineage(String sourceTable, String targetTable, String filePath, String queryText, String modelUsed) {
        this.sourceTable = sourceTable;
        this.targetTable = targetTable;
        this.filePath = filePath;
        this.queryText = queryText;
        this.modelUsed = modelUsed;
    }

    public String getSourceTable() {
        return sourceTable;
    }

    public String getTargetTable() {
        return targetTable;
    }

    public String getFilePath() {
        return filePath;
    }

    public String getQueryText() {
        return queryText;
    }

    public String getModelUsed() {
        return modelUsed;
    }

    @Override
    public String toString() {
        return "TableLineage{" +
                "source='" + sourceTable + '\'' +
                ", target='" + targetTable + '\'' +
                ", file='" + filePath + '\'' +
                '}';
    }
}
