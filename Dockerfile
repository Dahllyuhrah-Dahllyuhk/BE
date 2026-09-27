# ── Build stage ────────────────────────────────────────────────────────
FROM gradle:8.9-jdk21-alpine AS builder

WORKDIR /app

# 의존성만 먼저 다운로드 (레이어 캐시)
COPY --chown=gradle:gradle build.gradle settings.gradle* gradle.properties* ./
COPY --chown=gradle:gradle gradle ./gradle
RUN gradle dependencies --no-daemon -q || true

# 소스 복사 후 빌드
COPY --chown=gradle:gradle src ./src
RUN gradle clean bootJar -x test --no-daemon

# ── Run stage ──────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS runner

WORKDIR /app

# non-root 사용자 (보안)
RUN addgroup -S spring && adduser -S spring -G spring && \
    apk add --no-cache curl

COPY --from=builder --chown=spring:spring /app/build/libs/*.jar app.jar

USER spring

ENV SPRING_PROFILES_ACTIVE=prod \
    TZ=Asia/Seoul \
    JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
