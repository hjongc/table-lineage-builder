package com.tablelineagebuilder.config;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * .env 파일을 읽어서 설정값을 제공하는 클래스
 */
public class Config {
    private static final Map<String, String> ENV_MAP = new HashMap<>();

    static {
        loadEnv();
    }

    private static void loadEnv() {
        try (BufferedReader reader = new BufferedReader(new FileReader(".env"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                // 주석이나 빈 줄 무시
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                // KEY=VALUE 형식 파싱
                int idx = line.indexOf('=');
                if (idx > 0) {
                    String key = line.substring(0, idx).trim();
                    String value = line.substring(idx + 1).trim();
                    ENV_MAP.put(key, value);
                }
            }
        } catch (IOException e) {
            System.err.println("Warning: .env 파일을 읽을 수 없습니다. 기본값을 사용합니다.");
        }
    }

    public static String get(String key) {
        return ENV_MAP.get(key);
    }

    public static String get(String key, String defaultValue) {
        return ENV_MAP.getOrDefault(key, defaultValue);
    }

    // LLM 설정
    public static String getLlmServerUrl() {
        return get("LLM_SERVER_URL", "http://150.6.15.80:9393");
    }

    public static String getLlmModelName() {
        return get("LLM_MODEL_NAME", "gpt-4o-mini");
    }

    public static int getLlmTimeout() {
        return Integer.parseInt(get("LLM_TIMEOUT_MS", "60000"));
    }

    public static double getLlmTemperature() {
        return Double.parseDouble(get("LLM_TEMPERATURE", "0.2"));
    }

    public static int getLlmMaxTokens() {
        return Integer.parseInt(get("LLM_MAX_TOKENS", "16384"));
    }

    public static int getLlmMaxCompletionTokens() {
        // MAX_COMPLETION_TOKENS 우선, 없으면 MAX_TOKENS 사용 (하위 호환)
        String value = get("LLM_MAX_COMPLETION_TOKENS");
        if (value != null) {
            return Integer.parseInt(value);
        }
        return getLlmMaxTokens();
    }

    // MySQL 설정
    public static String getMysqlUrl() {
        return get("MYSQL_URL");
    }

    public static String getMysqlUsername() {
        return get("MYSQL_USERNAME");
    }

    public static String getMysqlPassword() {
        return get("MYSQL_PASSWORD");
    }

    public static String getMysqlDriver() {
        return get("MYSQL_DRIVER", "com.mysql.cj.jdbc.Driver");
    }
}
