package org.dallyeo.matuabom.common.config;

import lombok.extern.slf4j.Slf4j;
import org.dallyeo.matuabom.common.crypto.EncryptedTokenReadConverter;
import org.dallyeo.matuabom.common.crypto.EncryptedTokenWriteConverter;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * MongoDB 단일 저장소 구성.
 * - 정본(canonical) 리포지토리 패키지 스캔
 * - GoogleOAuthClient 토큰 암복호화 컨버터 등록
 * - TokenStore 컬렉션의 TTL 인덱스(expireAfterSeconds=0) 프로그램적 생성
 */
@Slf4j
@Configuration
@EnableMongoRepositories(
        basePackages = {
                "org.dallyeo.matuabom.user.repository",
                "org.dallyeo.matuabom.timetable.repository",
                "org.dallyeo.matuabom.meeting.repository",
                "org.dallyeo.matuabom.calendar.repository"
        }
)
public class MongoConfig {

    @Bean
    public MongoCustomConversions mongoCustomConversions(
            EncryptedTokenReadConverter readConverter,
            EncryptedTokenWriteConverter writeConverter) {
        return new MongoCustomConversions(List.of(readConverter, writeConverter));
    }

    /**
     * TokenStore 컬렉션에 expiresAt 기준 TTL 인덱스 생성.
     * expireAfterSeconds=0 → 문서의 expiresAt 시각이 지나면 Mongo 백그라운드 스윕이 제거.
     * (정확한 만료는 TokenStore가 애플리케이션 레벨에서 검사)
     */
    @Bean
    public ApplicationRunner tokenStoreTtlIndexInitializer(MongoTemplate mongoTemplate) {
        return args -> {
            String[] collections = {
                    "refresh_tokens",
                    "grace_jtis",
                    "invite_cache",
                    "token_refresh_locks",
                    "auth_codes"
            };
            for (String collection : collections) {
                try {
                    mongoTemplate.indexOps(collection).createIndex(
                            new Index().on("expiresAt", org.springframework.data.domain.Sort.Direction.ASC)
                                    .expire(0, TimeUnit.SECONDS));
                } catch (Exception e) {
                    log.warn("Failed to create TTL index on {}: {}", collection, e.getMessage());
                }
            }
        };
    }
}
