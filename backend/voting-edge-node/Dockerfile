# ========================================================
# Etapa 1: Builder (Maven + JDK 17 Alpine - Cache de capas)
# ========================================================
FROM maven:3.9.6-eclipse-temurin-17-alpine AS builder
WORKDIR /build

COPY pom.xml .
COPY common-domain/pom.xml common-domain/
COPY voting-edge-node/pom.xml voting-edge-node/
COPY voting-stream-processor/pom.xml voting-stream-processor/

RUN mvn dependency:go-offline -pl common-domain,voting-edge-node -am -B

COPY common-domain/src common-domain/src
COPY voting-edge-node/src voting-edge-node/src
COPY voters.json voting-edge-node/src/main/resources/voters.json

RUN mvn clean package -pl common-domain,voting-edge-node -am -DskipTests -B

# ========================================================
# Etapa 2: Runtime Ligero (JRE 17 Alpine + Usuario no root)
# ========================================================
FROM eclipse-temurin:17-jre-alpine AS runtime
WORKDIR /app

RUN addgroup -S appgroup && adduser -S appuser -G appgroup

COPY --from=builder /build/voting-edge-node/target/voting-edge-node-*.jar /app/app.jar

RUN chown -R appuser:appgroup /app
USER appuser

ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
