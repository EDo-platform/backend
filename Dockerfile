# ---------- 1) Build stage ----------
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# Gradle wrapper & 설정 복사
COPY gradlew gradlew.bat ./
COPY gradle ./gradle
COPY build.gradle* settings.gradle* gradle.properties* ./

RUN chmod +x ./gradlew

# Gradle 캐시 디렉터리 설정 (빌드 속도 향상)
ENV GRADLE_USER_HOME=/gradle_cache

# 의존성 캐시 (Gradle daemon 없이)
RUN ./gradlew --no-daemon dependencies || true

# 소스 복사 및 빌드
COPY src ./src
RUN ./gradlew --no-daemon clean bootJar

# ---------- 2) Runtime stage ----------
FROM eclipse-temurin:21-jre-slim AS runtime
WORKDIR /app

# ✅ ffmpeg 설치 (Safari 대응)
USER root
RUN apt-get update && \
    apt-get install -y --no-install-recommends ffmpeg && \
    rm -rf /var/lib/apt/lists/*

# 환경 변수
ENV PORT=8080
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0"

# JAR 복사
COPY --from=build /app/build/libs/*.jar /app/app.jar

# non-root 유저 생성 및 사용
RUN useradd -ms /bin/bash appuser
USER appuser

# 실행
ENTRYPOINT ["java","-jar","/app/app.jar"]