package org.dallyeo.matuabom.global.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

/**
 * JPA(PostgreSQL)와 MongoDB Repository를 패키지별로 분리.
 * Spring Boot가 두 저장소를 동시에 자동 구성할 때 충돌하는 문제를 방지.
 */
@Configuration
@EnableJpaRepositories(
    basePackages = {
        "org.dallyeo.matuabom.user.repository.jpa",
        "org.dallyeo.matuabom.timetable.repository.jpa"
    }
)
@EnableMongoRepositories(
    basePackages = {
        "org.dallyeo.matuabom.user.repository.mongo",
        "org.dallyeo.matuabom.timetable.repository.mongo",
        "org.dallyeo.matuabom.meeting.repository",
        "org.dallyeo.matuabom.calendar.repository"
    }
)
@EntityScan(basePackages = "org.dallyeo.matuabom")
public class JpaConfig {
}
