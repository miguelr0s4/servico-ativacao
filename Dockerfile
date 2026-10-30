FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

COPY pom.xml .
RUN mvn -q -B dependency:go-offline

COPY src src
RUN mvn -q -B package -DskipTests

FROM eclipse-temurin:21-jre

RUN groupadd --system ativacao && useradd --system --gid ativacao --no-create-home ativacao
WORKDIR /app
COPY --from=build /build/target/servico-ativacao-*.jar app.jar
RUN chown ativacao:ativacao app.jar
USER ativacao

EXPOSE 8080

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:InitialRAMPercentage=50"

HEALTHCHECK --interval=15s --timeout=3s --start-period=30s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health/liveness || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
