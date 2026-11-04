FROM eclipse-temurin:21-jdk-jammy AS builder

WORKDIR /workspace

COPY gradlew build.gradle settings.gradle ./
COPY gradle ./gradle
COPY src ./src
COPY observability ./observability

RUN chmod +x gradlew && ./gradlew bootJar --no-daemon

FROM eclipse-temurin:21-jre-jammy

LABEL com.pheeeew.press-generation-contract="2"

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl util-linux \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY --from=builder /workspace/build/libs/*.jar app.jar

ARG APP_RELEASE=unknown
ENV APP_RELEASE=${APP_RELEASE}
ENV PHEEEEW_PRESS_RACE_ADMISSION_FILE=/app/press-admission \
    PHEEEEW_PRESS_RACE_ADMISSION_REQUIRED=true

EXPOSE 8080

ENTRYPOINT ["java", "-Duser.timezone=Asia/Seoul", "-jar", "/app/app.jar"]
