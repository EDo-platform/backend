FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# Gradle wrapper & 설정 복사 (둘 중 있는 것만 복사되게 패턴 사용)
COPY gradlew gradlew.bat ./
COPY gradle ./gradle
COPY build.gradle* settings.gradle* gradle.properties* ./

RUN chmod +x ./gradlew

# 의존성 캐시(옵션)
RUN ./gradlew --no-daemon dependencies > /dev/null || true

# 소스 복사 후 빌드
COPY src ./src
RUN ./gradlew --no-daemon clean bootJar

# ---------- 2) Runtime stage ----------
FROM eclipse-temurin:21-jre
WORKDIR /app
ENV PORT=8080
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0"
COPY --from=build /app/build/libs/*.jar /app/app.jar

# (선택) 보안상 non-root
RUN useradd -ms /bin/bash appuser
USER appuser

ENTRYPOINT ["java","-jar","/app/app.jar"]