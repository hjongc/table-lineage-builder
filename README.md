# Table Lineage Builder

SQL 파일에서 쿼리를 추출하고, LLM을 사용하여 원천 테이블(Source)과 타겟 테이블(Target) 계보를 분석하는 도구입니다.

## 주요 기능

- **쿼리 추출**: 파일에서 SQL 쿼리만 추출 (주석, 쉘 명령어 제외)
- **테이블 계보 분석**: LLM을 사용하여 소스-타겟 테이블 관계 추출
- **데이터베이스 저장**: MySQL에 분석 결과 저장
- **멀티 모델 지원**: GPT-4o 계열, o3 계열 모델 자동 선택

## 아키텍처

```
파일 입력 → QueryExtractorAgent → TableAnalyzerAgent → TableLineageRepository
             (쿼리 추출)          (LLM 분석)           (DB 저장)
```

### Agent 구조
- **QueryExtractorAgent**: 주석 제거 및 SQL 쿼리만 추출
- **TableAnalyzerAgent**: LLM을 사용하여 테이블 계보 분석

### LLM Client
- **GPT4Client**: gpt-4o, gpt-4o-mini 호출
- **O3Client**: o3, o3-mini 호출
- 모델명에 따라 자동 선택

## 설치 및 빌드

### 1. 사전 요구사항
- Java 8 이상
- Maven 3.x
- MySQL 8.x

### 2. 데이터베이스 테이블 생성

```bash
mysql -h <host> -u <user> -p < create_table.sql
```

### 3. 환경 설정

`.env.example` 파일을 복사하여 `.env` 파일을 생성하고 수정:

```bash
cp .env.example .env
```

`.env` 파일 예시:

```properties
# LLM 설정
LLM_SERVER_URL=http://your-llm-server:port
LLM_MODEL_NAME=o3-mini
LLM_TIMEOUT_MS=60000
LLM_TEMPERATURE=0.2
LLM_MAX_TOKENS=16384
LLM_MAX_COMPLETION_TOKENS=16384

# MySQL 설정
MYSQL_URL=jdbc:mysql://your-host:3306/your_database?useSSL=true&serverTimezone=Asia/Seoul
MYSQL_USERNAME=your_username
MYSQL_PASSWORD=your_password
```

**⚠️ 보안 주의사항:**
- `.env` 파일은 민감한 정보를 포함하므로 절대 Git에 커밋하지 마세요
- `.gitignore`에 `.env`가 포함되어 있는지 확인하세요

### 4. 빌드

```bash
mvn clean package
```

실행 가능한 JAR 파일이 `target/table-lineage-builder-1.0.0.jar`에 생성됩니다.

## 사용법

```bash
java -jar target/table-lineage-builder-1.0.0.jar <SQL_파일_경로>
```

### 예제

```bash
java -jar target/table-lineage-builder-1.0.0.jar /path/to/your/script.sql
```

### 출력 예시

```
=== 테이블 계보 추출 시작 ===
파일: /path/to/your/script.sql

[1단계] 쿼리 추출 중...
추출된 쿼리 개수: 5

[2단계] 테이블 계보 분석 중...
  (1/5) 분석 중...
    -> SOURCE_TABLE1 → TARGET_TABLE
    -> SOURCE_TABLE2 → TARGET_TABLE
  (2/5) 분석 중...
    -> ORDER_TABLE → SALES_SUMMARY
총 추출된 계보: 10개

[3단계] 데이터베이스 저장 중...
저장 완료: 10개

=== 완료 ===
```

## 데이터베이스 스키마

### table_lineage 테이블

| 컬럼 | 타입 | 설명 |
|------|------|------|
| id | INT | 고유 ID (AUTO_INCREMENT) |
| source_table | VARCHAR(255) | 원천 테이블명 |
| target_table | VARCHAR(255) | 타겟 테이블명 |
| file_path | VARCHAR(500) | 분석한 파일 경로 |
| query_text | TEXT | 실제 SQL 쿼리 |
| model_used | VARCHAR(50) | 사용한 LLM 모델명 |
| created_at | DATETIME | 생성일시 |
| updated_at | DATETIME | 수정일시 |

**인덱스:**
- `idx_source_table`: source_table
- `idx_target_table`: target_table
- `idx_source_target`: (source_table, target_table)

**UNIQUE 제약:**
- `uk_source_target_file`: (source_table, target_table, file_path)

## 모델 변경

`.env` 파일에서 `LLM_MODEL_NAME`을 변경하면 됩니다:

```properties
# o3-mini (추천: 추론 능력 우수, 200K 컨텍스트)
LLM_MODEL_NAME=o3-mini

# o3 (최고 성능)
LLM_MODEL_NAME=o3

# GPT-4o mini (빠르고 저렴, 128K 컨텍스트)
LLM_MODEL_NAME=gpt-4o-mini

# GPT-4o (정확도 높음)
LLM_MODEL_NAME=gpt-4o
```

**참고:**
- o3 계열 모델은 `LLM_MAX_COMPLETION_TOKENS` 사용 (200K 컨텍스트)
- GPT-4o 계열 모델은 `LLM_MAX_TOKENS` 사용 (128K 컨텍스트)
- 매우 긴 SQL 쿼리 분석 시 o3-mini 권장

## 프로젝트 구조

```
table-lineage-builder/
├── .env                          # 환경 설정 파일 (Git 제외)
├── .env.example                  # 환경 설정 템플릿 (Git 포함)
├── .gitignore                    # Git 제외 파일 목록
├── pom.xml                       # Maven 설정
├── create_table.sql              # DB 테이블 생성 스크립트
├── README.md                     # 문서
├── reference/
│   └── test_sample.sql          # 테스트용 샘플 SQL
└── src/main/java/com/tablelineagebuilder/
    ├── TableLineageBuilderMain.java   # 메인 클래스
    ├── agent/
    │   ├── QueryExtractorAgent.java      # 쿼리 추출 에이전트
    │   └── TableAnalyzerAgent.java       # 테이블 분석 에이전트
    ├── client/
    │   ├── LLMClient.java               # LLM 클라이언트 추상 클래스
    │   ├── GPT4Client.java              # GPT-4o 계열 클라이언트
    │   └── O3Client.java                # o3 계열 클라이언트
    ├── config/
    │   └── Config.java                   # 설정 로더
    ├── model/
    │   ├── QueryInfo.java               # 쿼리 정보 모델
    │   └── TableLineage.java            # 테이블 계보 모델
    └── repository/
        └── TableLineageRepository.java   # DB 저장 Repository
```

## 참고사항

- 파일에 SQL 외에 쉘 명령어나 주석이 포함되어 있어도 자동으로 필터링됩니다
- 동일한 (소스, 타겟, 파일) 조합은 중복 저장되지 않고 업데이트됩니다
- query_text가 60,000자를 초과하면 자동으로 잘립니다 (DB 저장용)
- 1개의 타겟 테이블에 여러 소스 테이블이 매핑될 수 있습니다
- LLM에는 원본 쿼리 전체가 전달됩니다 (잘리지 않음)

## 보안 모범 사례

1. **환경 변수 관리**
   - `.env` 파일은 절대 Git에 커밋하지 마세요
   - `.env.example`을 참고하여 각 환경에 맞게 설정하세요

2. **데이터베이스 보안**
   - 프로덕션 환경에서는 읽기 전용 계정 사용 권장
   - 필요한 최소 권한만 부여하세요
   - SSL/TLS 연결 사용을 권장합니다

3. **LLM API 보안**
   - API 키나 서버 URL이 외부에 노출되지 않도록 주의하세요
   - 내부 네트워크에서만 접근 가능하도록 설정하세요

## 라이선스

내부 프로젝트용
