# ---------- 1) Build stage ----------
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

COPY gradlew gradlew.bat ./
COPY gradle ./gradle
COPY build.gradle* settings.gradle* gradle.properties* ./

RUN chmod +x ./gradlew
ENV GRADLE_USER_HOME=/gradle_cache
RUN ./gradlew --no-daemon dependencies || true

COPY src ./src
RUN ./gradlew --no-daemon clean bootJar

# ---------- 2) Runtime stage ----------
# ✅ JRE slim은 없으므로 일반 jre 사용
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app

# ffmpeg 설치
USER root
RUN apt-get update && \
    apt-get install -y --no-install-recommends ffmpeg && \
    rm -rf /var/lib/apt/lists/*

ENV PORT=8080
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0"

COPY --from=build /app/build/libs/*.jar /app/app.jar

RUN useradd -ms /bin/bash appuser
USER appuser

ENTRYPOINT ["java","-jar","/app/app.jar"]