# ========================================================
# Etapa 1: Compilacion y empaquetado (Builder multietapa)
# ========================================================
FROM maven:3.9.6-eclipse-temurin-17-alpine AS builder
WORKDIR /build

# Copia de descriptores del reactor multimodulo Maven
COPY pom.xml .
COPY common-domain/pom.xml common-domain/
COPY voter-registration-service/pom.xml voter-registration-service/
COPY voting-api/pom.xml voting-api/
COPY vote-processor/pom.xml vote-processor/
COPY regional-rollup-service/pom.xml regional-rollup-service/
COPY bot-detector-service/pom.xml bot-detector-service/
COPY analytics-archiver/pom.xml analytics-archiver/
COPY global-dashboard-worker/pom.xml global-dashboard-worker/
COPY regional-dashboard-worker/pom.xml regional-dashboard-worker/

# Codigo fuente necesario
COPY common-domain/src common-domain/src
COPY global-dashboard-worker/src global-dashboard-worker/src

# Compilacion del modulo y sus dependencias internas
RUN mvn clean package -pl common-domain,global-dashboard-worker -am -DskipTests

# ========================================================
# Etapa 2: Runtime Ligero (JRE 17 Alpine + Usuario no root)
# ========================================================
FROM eclipse-temurin:17-jre-alpine AS runtime
WORKDIR /app

RUN addgroup -S appgroup && adduser -S appuser -G appgroup

COPY --from=builder /build/global-dashboard-worker/target/*.jar /app/app.jar

RUN chown -R appuser:appgroup /app
USER appuser

ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8082
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
