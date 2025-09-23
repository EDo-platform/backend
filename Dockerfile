# ---------- 1) Build stage ----------
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# Gradle 캐시 최적화: 먼저 설정/의존성만 복사
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties* ./ 2>/dev/null || true
COPY gradle ./gradle
RUN chmod +x ./gradlew

# 의존성 미리 받아 캐시
RUN ./gradlew --no-daemon dependencies > /dev/null || true

# 소스 복사 후 빌드
COPY src ./src
RUN ./gradlew --no-daemon clean bootJar

# ---------- 2) Runtime stage ----------
FROM eclipse-temurin:21-jre
WORKDIR /app

# Render가 기본으로 1000 포트를 쓰진 않지만, 관례상 노출만
ENV PORT=8080
EXPOSE 8080

# 메모리 제한 환경(무료/저사양 플랜) 대응 JVM 옵션
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0"

# 빌드 결과 JAR 복사 (build/libs 안의 단일 jar)
COPY --from=build /app/build/libs/*.jar /app/app.jar

# non-root로 실행 (보안)
RUN useradd -ms /bin/bash appuser
USER appuser

# Spring이 Render가 주는 PORT를 따르도록 (application.yml에 server.port=${PORT:8080} 권장)
ENTRYPOINT ["java","-jar","/app/app.jar"]