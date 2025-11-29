# BE/Dockerfile

# ---- Build stage ----
FROM gradle:8.9-jdk21-alpine AS builder

WORKDIR /app

# gradle 캐시를 살리려면 보통 build.gradle / settings.gradle 먼저 복사하지만
# 지금은 전체 복사로도 충분히 쓸 수 있음
COPY --chown=gradle:gradle . .

# 테스트는 빼고 빌드
RUN gradle clean bootJar -x test

# ---- Run stage ----
FROM eclipse-temurin:21-jre-alpine AS runner

WORKDIR /app

COPY --from=builder /app/build/libs/*.jar app.jar

ENV SPRING_PROFILES_ACTIVE=prod \
    TZ=Asia/Seoul

EXPOSE 8080

ENTRYPOINT ["java","-jar","/app/app.jar"]
