package com.edo.backend.ocr.debug;

import com.google.auth.oauth2.GoogleCredentials;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;

@Configuration
public class GcpConfig {

    // 기존 env 이름 유지
    @Value("${GOOGLE_APPLICATION_CREDENTIALS:}")
    private String adc; // 경로 or JSON or Base64 모두 가능

    // 보조 경로(있으면 사용)
    @Value("${GOOGLE_APPLICATION_CREDENTIALS_JSON:}")
    private String adcJson;

    @Value("${GOOGLE_APPLICATION_CREDENTIALS_B64:}")
    private String adcB64;

    @Bean
    public GoogleCredentials googleCredentials() throws IOException {
        byte[] raw = resolveRawCredBytes();
        try (var in = new ByteArrayInputStream(raw)) {
            return GoogleCredentials.fromStream(in)
                    .createScoped(List.of("https://www.googleapis.com/auth/cloud-platform"));
        }
    }

    private byte[] resolveRawCredBytes() throws IOException {
        // 1) 최우선: GOOGLE_APPLICATION_CREDENTIALS 값이 존재
        if (notBlank(adc)) {
            String trimmed = adc.trim();

            // 1-a) 파일 경로로 존재?
            Path p = Path.of(trimmed);
            if (Files.exists(p) && Files.isRegularFile(p)) {
                return Files.readAllBytes(p);
            }

            // 1-b) JSON 처럼 보이면 그대로
            if (looksLikeJson(trimmed)) {
                return trimmed.getBytes(StandardCharsets.UTF_8);
            }

            // 1-c) Base64 처럼 보이면 디코드
            if (looksLikeBase64(trimmed)) {
                try {
                    return Base64.getDecoder().decode(trimmed.replaceAll("\\s+", ""));
                } catch (IllegalArgumentException ignore) {
                    // 못 디코드하면 아래 보조 경로로 폴백
                }
            }
            // 여기까지 오면 값이 유효하지 않은 것: 보조 변수로 폴백
        }

        if (notBlank(adcB64)) {
            return Base64.getDecoder().decode(adcB64.replaceAll("\\s+", ""));
        }

        // 3) 보조: _JSON
        if (notBlank(adcJson)) {
            return adcJson.getBytes(StandardCharsets.UTF_8);
        }

        // 4) 최후: ADC (GCE/GKE 등 메타데이터)
        return GoogleCredentials.getApplicationDefault()
                .createScoped(List.of("https://www.googleapis.com/auth/cloud-platform"))
                .refreshAccessToken()  // 유효성 빨리 확인
                .getTokenValue()       // 그냥 호출해서 예외 유도용
                .getBytes(StandardCharsets.UTF_8);
    }

    private static boolean looksLikeJson(String s) {
        String t = s.stripLeading();
        return t.startsWith("{") || t.startsWith("[") || t.contains("\"type\"") || t.contains("service_account");
    }

    private static boolean looksLikeBase64(String s) {
        // 알파벳/숫자/+/=/줄바꿈만 포함 & JSON 중괄호는 없어야
        return s.length() > 0
                && !s.contains("{")
                && s.replaceAll("\\s+", "").matches("^[A-Za-z0-9+/=]+$");
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }





}
