package org.dallyeo.matuabom.global.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 필수 환경변수 누락 시 애플리케이션 시작을 즉시 중단.
 * 런타임 NPE 대신 시작 시점에 명확한 오류 메시지 출력.
 */
@Getter
@Setter
@Component
public class AppProperties {

    @Value("${JWT_SECRET:}")
    private String jwtSecret;

    @Value("${GOOGLE_CLIENT_ID:}")
    private String googleClientId;

    @Value("${GOOGLE_CLIENT_SECRET:}")
    private String googleClientSecret;

    @Value("${KAKAO_CLIENT_ID:}")
    private String kakaoClientId;

    @Value("${MONGODB_URI:}")
    private String mongodbUri;

    @Value("${app.encrypt-key:}")
    private String encryptKey;

    @PostConstruct
    public void validate() {
        List<String> missing = new ArrayList<>();

        if (isBlank(jwtSecret))          missing.add("JWT_SECRET");
        if (isBlank(googleClientId))      missing.add("GOOGLE_CLIENT_ID");
        if (isBlank(googleClientSecret))  missing.add("GOOGLE_CLIENT_SECRET");
        if (isBlank(kakaoClientId))       missing.add("KAKAO_CLIENT_ID");
        if (isBlank(mongodbUri))          missing.add("MONGODB_URI");
        // ENCRYPT_KEY는 누락 시 AesEncryptor에서 개발용 기본키 사용 (운영에서는 설정 필수)

        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                "필수 환경변수가 누락되었습니다: " + String.join(", ", missing)
            );
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
