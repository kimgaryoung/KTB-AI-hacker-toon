# syntax=docker/dockerfile:1

# --- build: Gradle 래퍼로 bootJar 생성 (Java 21 toolchain) ---
FROM eclipse-temurin:21-jdk-noble AS build
WORKDIR /workspace

# 의존성 레이어를 소스와 분리해 캐시 적중률을 높인다.
COPY gradlew gradle.properties settings.gradle build.gradle ./
COPY gradle gradle
RUN chmod +x gradlew

COPY src src
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon --console=plain clean bootJar

# --- runtime ---
FROM eclipse-temurin:21-jre-noble AS runtime
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/* \
 && useradd --system --create-home --uid 10001 spring
WORKDIR /app

COPY --from=build /workspace/build/libs/*-SNAPSHOT.jar /app/app.jar

# 업로드 저장 경로. compose에서 named volume으로 마운트한다.
RUN mkdir -p /app/data/uploads && chown -R spring:spring /app
USER spring

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
