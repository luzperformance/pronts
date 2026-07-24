FROM maven:3.9.16-eclipse-temurin-21@sha256:2b4496088e7b80ae10a8c9f74e574ea21380325a006ec684532ad6bad5bc7273 AS build

WORKDIR /workspace

COPY pom.xml ./
RUN mvn --batch-mode --no-transfer-progress dependency:go-offline

COPY src/ src/
RUN mvn --batch-mode --no-transfer-progress -Dmaven.test.skip=true package \
    && cp target/primeiro-prontuario-api-0.0.1-SNAPSHOT.jar application.jar

FROM eclipse-temurin:21-jre-jammy@sha256:d63bd8d9b171999cbed8576f2c76e874dd4856791a358536e5c4d407e77edc13

RUN groupadd --system --gid 10001 app \
    && useradd --system --uid 10001 --gid app --home-dir /app --shell /usr/sbin/nologin app \
    && mkdir --parents /app /var/lib/primeiro-prontuario/attachments \
    && chown --recursive app:app /app /var/lib/primeiro-prontuario

COPY --from=build --chown=10001:10001 /workspace/application.jar /app/application.jar

USER 10001:10001
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/application.jar"]
