package com.lineage.utils;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.TimeZone;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lineage.llm.OpenAIClientVer3;
import lombok.extern.slf4j.Slf4j;
@Slf4j
public class RemoveComments {
 private static final String MYSQL_URL = "jdbc:mysql://midp-emrmysql-prd.c1ooq0gkygqz.ap-northeast-2.rds.amazonaws.com:3306/midp_project?useSSL=true&serverTimezone=Asia/Seoul&connectTimeout=30000&autoCommit=true&allowPublicKeyRetrieval=true&verifyServerCertificate=false";
 private static final String MYSQL_USERNAME = "midp_sysops_dev";
 private static final String MYSQL_PASSWORD = "Devadmin1!";
 private static final String MYSQL_DRIVER = "com.mysql.cj.jdbc.Driver";
    public static void main(String[] args) {
     
  System.setProperty("user.timezone", "Asia/Seoul");
  TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"));
  
  log.info("start extract lineage ");
//  Options options = new Options();
//  options.addOption("p", "filePath", true, "filePath");
//  CommandLineParser parser = new DefaultParser();
//  CommandLine cmd;
//  
//  String filePath ="";
//  try {
//   cmd = parser.parse(options, args);
//   filePath = cmd.getOptionValue("p");
//  } catch (ParseException e1) {
//   // TODO Auto-generated catch block
//   log.error("parsing filepath is failed :{}",e1.getMessage());
//   e1.printStackTrace();
//  }
//  
   
  String filePath ="C:\\dev\\workspace_aws\\sid-ap\\src";
  
  
  List<Path> sqlFiles = new ArrayList<>();
  if (filePath != null && !"".equals(filePath)) {
      sqlFiles = findSQLFiles(filePath);
      for (Iterator<Path> it = sqlFiles.iterator(); it.hasNext();) {
          Path p = it.next();
          if (isFileExists(p.toString())) {
              it.remove();
          }
      }
      log.info("target file count : {} ",sqlFiles.size());
  }
  
//     for(int i=0; i<sqlFiles.size(); i++) {
//         // Path 객체를 생성하여 파일명 추출
//         Path path = Paths.get(sqlFiles.get(i).toString());
//         String fixedfileName = path.getFileName().toString();
//      log.info("file : {}",fixedfileName);
//     }
//  
  for (Path sqlFile : sqlFiles) {
   StringBuilder resultBuilder = new StringBuilder();
   try (BufferedReader reader = new BufferedReader(new FileReader(sqlFile.toFile()));) {
    String line;
    while ((line = reader.readLine()) != null) {
     // '#'으로 시작하는 줄을 제거합니다
     if (line.trim().startsWith("#")) {
      continue;
     }
     // '--'로 시작하는 주석을 제거합니다
     if (line.contains("--")) {
      line = line.substring(0, line.indexOf("--"));
     }
     // 결과 파일에 주석이 제거된 줄을 씁니다
     if (!line.trim().isEmpty()) {
      resultBuilder.append(line);
      resultBuilder.append(System.lineSeparator());
     }
    }
   } catch (IOException e) {
    e.printStackTrace();
    log.info("file :{} , error : {} ", sqlFile.toFile(), e.getMessage());
   }
   // === SQL 추출 로직 추가 ===
   List<String> sqlStatements = new ArrayList<>();
   StringBuilder current = new StringBuilder();
   boolean inStatement = false;
   for (String l : resultBuilder.toString().split("\\R")) { // 줄 단위
    String trimmed = l.trim();
    if (trimmed.isEmpty())
     continue;
    // SQL 시작 키워드로 문장 시작
    if (!inStatement && trimmed.matches("(?i)^(MERGE|UPDATE|INSERT|CREATE|WITH|TRUNCATE).*")) {
     inStatement = true;
    }
    if (inStatement) {
     current.append(l).append(System.lineSeparator());
     // 세미콜론으로 끝나면 문장 종료
     if (trimmed.endsWith(";")) {
      sqlStatements.add(current.toString().trim());
      current.setLength(0);
      inStatement = false;
     }
    }
   }
   log.info("filePath : {} , count : {}",sqlFile, sqlStatements.size());
   List<String> results = OpenAIClientVer3.analyzeAll(sqlStatements);
   for (int i = 0; i < results.size(); i++) {
    log.info("SQL #" + (i + 1) + ":");
    log.info(sqlStatements.get(i));
    log.info("---- LLM RESULT #" + (i + 1) + " ----");
    String result = results.get(i).replace("```", "").replace("json", "");
    log.info(result);
    String fileName = sqlFile.toString().replace(filePath, "");
    log.info("file : " + fileName);
    save(result, fileName, sqlStatements.get(i));
   }
  }
 }
    private static List<Path> findSQLFiles(String directoryPath) {
        List<Path> sqlFiles = new ArrayList<>();
        try {
            Files.walkFileTree(Paths.get(directoryPath), new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (file.toString().endsWith(".sql")) {
                        sqlFiles.add(file);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            log.error("디렉토리를 검색하는 동안 오류가 발생했습니다: {}", e.getMessage());
        }
        return sqlFiles;
    }
    
    private static void save(String result, String referenceFile, String query) {
        ObjectMapper objectMapper = new ObjectMapper();
        if(!result.toUpperCase().contains("ERROR")) {
         try {
          JsonNode rootNode = objectMapper.readTree(result);
          JsonNode lineagesNode = rootNode.path("lineages");
          Class.forName(MYSQL_DRIVER);
          try (Connection connection = DriverManager.getConnection(MYSQL_URL, MYSQL_USERNAME, MYSQL_PASSWORD)) {
           String insertOrUpdateSQL = "INSERT INTO midp_project.lineage_info (target_table, target_column, src_table, src_column, created_at, is_deleted, updated_at) " +
             "VALUES (?, ?, ?, ?, NOW(), 'N', NOW()) " +
             "ON DUPLICATE KEY UPDATE " +
             "updated_at = NOW(), is_deleted = 'N'";
           try (PreparedStatement preparedStatement = connection.prepareStatement(insertOrUpdateSQL, PreparedStatement.RETURN_GENERATED_KEYS)) {
            for (JsonNode lineage : lineagesNode) {
             String targetTable  = lineage.path("targetTable").asText().toUpperCase();
             String targetColumn = lineage.path("targetColumn").asText().toUpperCase();
             String sourceTable = lineage.path("sourceTable").asText().toUpperCase();
             String sourceColumn = lineage.path("sourceColumn").asText().toUpperCase();
             
             // 타겟과 소스가 동일하면 저장하지 않는다.
             if(!(targetTable.equals(sourceTable) && targetColumn.equals(sourceColumn))) {
              log.info("targetTable : "+targetTable+ ", targetColumn : "+targetColumn+" , sourceTable : "+sourceTable+" , sourceColumn : "+sourceColumn);
              preparedStatement.setString(1, targetTable);
              preparedStatement.setString(2, targetColumn);
              preparedStatement.setString(3, sourceTable);
              preparedStatement.setString(4, sourceColumn);
              preparedStatement.executeUpdate();
              
              // 방금 삽입한 lineage_info의 ID를 가져옵니다.
              try (ResultSet generatedKeys = preparedStatement.getGeneratedKeys()) {
               if (generatedKeys.next()) {
                int lineageId = generatedKeys.getInt(1);
                saveReferenceInfo(lineageId, referenceFile, query,"OK");
               }
              }
             }
            }
           }
          }
         } catch (Exception e) {
          e.printStackTrace();
         }
        }else {
         saveReferenceInfo(null, referenceFile, query,"ERROR");
        }
    }
    private static void saveReferenceInfo(Integer lineageId, String referenceFile, String relatedQuery, String result) {
        try {
            Class.forName(MYSQL_DRIVER);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            return; // 드라이버를 로드할 수 없는 경우, 이후 코드를 실행할 필요가 없습니다.
        }
        // relatedQuery의 길이가 TEXT의 최대 길이인 65535자를 초과하는지 확인
        if (relatedQuery != null && relatedQuery.length() > 60000) {
            relatedQuery = relatedQuery.substring(0, 60000); // 초과 부분 자르기
        }
        
        String insertReferenceSQL = "INSERT INTO midp_project.lineage_reference_info "
                                  + "(lineage_id, reference_file, related_query, result) VALUES (?, ?, ?, ?)";
        try (Connection connection = DriverManager.getConnection(MYSQL_URL, MYSQL_USERNAME, MYSQL_PASSWORD);
             PreparedStatement preparedStatement = connection.prepareStatement(insertReferenceSQL)) {
            if (lineageId != null) {
                preparedStatement.setInt(1, lineageId);
            } else {
                preparedStatement.setNull(1, java.sql.Types.INTEGER);
            }
            preparedStatement.setString(2, referenceFile);
            preparedStatement.setString(3, relatedQuery);
            preparedStatement.setString(4, result);
            preparedStatement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    
    public static boolean isFileExists(String referenceFile) {
        // '/'를 기준으로 문자열을 분리하여 배열 생성
     String[] pathParts = referenceFile.split("\\\\");
        String fileName = pathParts[pathParts.length - 1];
        
        // Path 객체를 생성하여 파일명 추출
        Path path = Paths.get(fileName);
        String fixedfileName = path.getFileName().toString();
        
//        log.info("fileName : {} ",fixedfileName);
        String checkFileSQL = "SELECT 1 FROM midp_project.lineage_reference_info WHERE reference_file LIKE ?";
        try (Connection connection = DriverManager.getConnection(MYSQL_URL, MYSQL_USERNAME, MYSQL_PASSWORD);
             PreparedStatement preparedStatement = connection.prepareStatement(checkFileSQL)) {
            preparedStatement.setString(1, "%" + fixedfileName + "%");
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                // 결과가 있는지 확인
                return resultSet.next();
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;  // 오류 발생 시 false 반환
        }
    }
}
