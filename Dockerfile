# syntax=docker/dockerfile:1.7@sha256:a57df69d0ea827fb7266491f2813635de6f17269be881f696fbfdf2d83dda33e
FROM eclipse-temurin:21-jdk-jammy@sha256:742daacb9316be4b6fc60258f6e4ac2a11882f6c597373c92290a57bfad0d2e0 AS build
WORKDIR /workspace
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN --mount=type=cache,target=/root/.m2 bash mvnw -B -q -DskipTests dependency:go-offline
COPY lombok.config ./
COPY src/main/ src/main/
RUN --mount=type=cache,target=/root/.m2 bash mvnw -B -q -Dmaven.test.skip=true package \
    && java -Djarmode=tools -jar target/socratesai-0.0.1-SNAPSHOT.jar extract --layers --destination /layers

FROM eclipse-temurin:21-jre-jammy@sha256:affcab5356e1270ac35d2361ee83135af2f03e8d580eca78a6d5d6fdbf048db0 AS runtime
RUN groupadd --gid 10001 app && useradd --uid 10001 --gid app --no-create-home --shell /usr/sbin/nologin app \
    && ! command -v javac && ! java --list-modules | grep -q '^jdk.compiler@'
WORKDIR /app
COPY --from=build --chown=10001:10001 /layers/dependencies/ ./
COPY --from=build --chown=10001:10001 /layers/spring-boot-loader/ ./
COPY --from=build --chown=10001:10001 /layers/snapshot-dependencies/ ./
COPY --from=build --chown=10001:10001 /layers/application/ ./
COPY deploy/backend/healthcheck.sh /opt/healthcheck.sh
USER 10001:10001
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=65 -XX:+ExitOnOutOfMemoryError -Djava.io.tmpdir=/tmp"
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=5 CMD ["bash", "/opt/healthcheck.sh"]
ENTRYPOINT ["java", "-jar", "/app/socratesai-0.0.1-SNAPSHOT.jar"]
