# Plataforma de Votación de Alta Disponibilidad (Live Reality Show Voting System)

Sistema distribuido de alta disponibilidad, concurrencia y baja latencia para votaciones masivas en vivo con restricciones críticas de **Validación de Votantes** y **Unicidad del Voto (Deduplicación / Last Vote Wins)**.

---

## 1. Justificación y Defensa del Diseño Híbrido

El sistema implementa una arquitectura híbrida que aprovecha las fortalezas complementarias de **Apache Kafka**, **RabbitMQ** y **PostgreSQL**:

```
[ Clientes / Votantes ]
         │
         ▼ (HTTP POST /vote)
 ┌───────────────┐        KTable Local In-Memory
 │  voting_api   │ ◄─────────────────────────────────┐
 └───────┬───────┘                                   │
         │ (user_id partition key)                   │
         ▼                                           │
  [ Kafka: raw_votes (Compacted) ]        [ Kafka: eligible_voters (Compacted) ]
   ├──► analytics_archiver ──► PostgreSQL            ▲
   ├──► bot_detector ──► security_alerts & Postgres  │
   ├──► vote_processor (1s)                          │
   │         │                                       │
   │         ▼                               ┌───────┴────────────────────┐
   │    [ RabbitMQ: live_results_global ]    │ voter_registration_service │
   │         │ (Fanout)                      └────────────────────────────┘
   │         ▼
   │    [ global_dashboard_worker ] ──► Web UI (WebSocket / SSE)
   │
   └──► regional_rollup_service (5s)
             │ (Strategy Pattern: Electoral Weighting)
             ▼
        [ RabbitMQ: live_results_regional ]
             │ (Topic: results.<region>)
             ▼
        [ regional_dashboard_worker ] ──► Web UI (WebSocket / SSE)
```

### ¿Por qué Kafka como Fuente de Verdad y Registro de Estado?
- **Log Inmutable y Replayable:** Kafka almacena cada sufragio en orden estricto de llegada por partición.
- **Log Compaction como KTable en Memoria:**
  - Topic `eligible_voters`: Se consume desde el inicio (`auto.offset.reset=earliest`) para materializar el estado de los votantes autorizados directamente en memoria (`ConcurrentHashMap`), permitiendo resolver la elegibilidad en sub-milisegundos sin golpear una base de datos en el camino crítico.
  - Topic `raw_votes`: Particionado por `user_id`. La compactación de Kafka (`cleanup.policy=compact`) garantiza que el log retenga el último voto emitido por cada votante, implementando la deduplicación de negocio de forma nativa a nivel broker.
- **Múltiples Consumidores Desacoplados:** Cada microservicio (`vote_processor`, `regional_rollup_service`, `bot_detector_service`, `analytics_archiver`) lee del mismo flujo de eventos a su propio ritmo mediante distintos `group-id` sin degradar el rendimiento de los demás.

### ¿Por qué RabbitMQ como Bus de Distribución y Dashboards?
- **Enrutamiento Flexible y Reactivo:** RabbitMQ sobresale en la distribución efímera y de alta tasa de refresco hacia clientes y trabajadores de presentación:
  - **Exchange Fanout (`live_results_global`):** Difusión masiva tipo broadcast cada 1 segundo a todos los dashboards globales conectados.
  - **Exchange Topic (`live_results_regional`):** Enrutamiento inteligente basado en patrones de routing key (`results.norte`, `results.sur`, `results.centro`), permitiendo que trabajadores regionales se suscriban selectivamente a `results.*`.
- **Baja Sobrecarga para Mensajería Efímera:** Los dashboards requieren la última foto consolidada; si un dashboard se desconecta, no necesita retener millones de mensajes pasados, liberando memoria de inmediato.

### ¿Por qué PostgreSQL para Persistencia y Auditoría?
- **Auditoría Forense y Trazabilidad:** Almacenamiento formal e inmutable de votos históricos (`audit_votes`) e incidentes de seguridad detectados (`security_incidents`).
- **Aislamiento de Red:** Aloja la base de datos dentro de una red virtual privada sin exposición innecesaria a clientes externos.

---

## 2. Modelado de Dominio Rico (POO) y Patrones de Diseño (GoF)

El proyecto rechaza los modelos anémicos y aplica principios estrictos de **Clean Code** y **SOLID**:

### Inmutabilidad y Fail-Fast con Java `record`
Los Value Objects e Identificadores están modelados como `record` de Java 17+, aplicando validación Fail-Fast en sus constructores compactos:
- `VoterId`: Valida no nulos, no vacíos y sintaxis regex (`^[a-zA-Z0-9_-]{3,64}$`).
- `CandidateId`: Valida identificadores de candidatos.
- `Region`: Enum con sufijos de enrutamiento (`toRoutingKey()`).
- `IpAddress`: Valida direcciones IPv4/IPv6 y previene entradas vacías o malformadas.
- `VoteRecord`: Evento inmutable de dominio que vincula votante, candidato, región, IP y timestamp.

### Patrón Strategy (Ponderación Regional Polimórfica)
Ubicado en `com.voting.platform.domain.strategy`:
- `RegionalWeightingStrategy`: Interfaz de estrategia para algoritmos de cálculo.
- `UniformWeightingStrategy`: Ponderación 1:1 estándar.
- `ElectoralWeightingStrategy`: Aplica multiplicadores regionales configurables:
  - NORTE: 1.25
  - SUR: 1.15
  - ESTE: 0.90
  - OESTE: 1.05
  - CENTRO: 1.40
- `RegionalStrategyContext`: Permite intercambiar estrategias en caliente en tiempo de ejecución de manera desacoplada y thread-safe.

### Patrón State (Ciclo de Vida y Anti-Fraude)
1. **Ciclo de Votación (`com.voting.platform.domain.state.lifecycle`):**
   - Estados: `HabilitadoState`, `CerradoState`, `EnAuditoriaState`.
   - Modela transiciones limpias: `HABILITADO` permite votos; `CERRADO` lanza `VotingClosedException`; `EN_AUDITORIA` lanza `VotingInAuditException` sin anidar `if/else`.
2. **Clasificación de Riesgo IP (`com.voting.platform.domain.state.risk`):**
   - Estados: `NormalRiskState`, `SuspiciousRiskState`, `BlockedRiskState`.
   - `IpTracker`: Ventana deslizante de tiempo que monitorea la frecuencia de votos por IP. Si se superan los umbrales de advertencia o bloqueo, transiciona de estado y emite alertas de seguridad inmediatas a Kafka `security_alerts` y PostgreSQL.

### Agregados de Dominio Rico
- `VoteCounterAggregate`: Protege el invariante de deduplicación. Si un votante emite un voto por el Candidato A y luego por el Candidato B, el agregado decrementa A e incrementa B atómicamente, manteniendo la cuenta de votantes únicos inalterada.
- `RegionalVoteCounterAggregate`: Maneja la deduplicación cruzada entre regiones y candidatos.

### Inversión de Dependencias (DIP)
- Todos los servicios inyectan sus dependencias exclusivamente por constructor.
- Desacoplamiento total mediante interfaces/puertos: `EligibleVoterPublisher`, `VoteEventPublisher`, `GlobalResultPublisher`, `RegionalResultPublisher`, `VoterEligibilityStore`, `VoteAuditRepositoryPort`, `SecurityIncidentRepositoryPort`.

---

## 3. Suite de Pruebas Unitarias Aisladas (JUnit 5 + Mockito)

Las pruebas unitarias cumplen con los requisitos más rigurosos:
- **Ejecución en Memoria RAM en Milisegundos:** No inician contextos pesados de Spring ni dependen de la red o brokers externos.
- **Prohibición de Brokers y Bases de Datos:** Cero contenedores o brokers reales levantados durante los tests unitarios.
- **Patrón AAA (Arrange, Act, Assert):** Verificación explícita de comportamiento, contratos y excepciones.
- **Cobertura Completa:** 60+ pruebas unitarias automatizadas probando Value Objects, GoF Strategy, GoF State, Agregados, Servicios de API, Procesadores y Dashboards.

Para ejecutar todas las pruebas unitarias:
```bash
.\mvnw.cmd test
```

---

## 4. Contenerización Multietapa (Multi-Stage Build)

Cada microservicio cuenta con un Dockerfile estructurado en dos etapas:

```dockerfile
# ETAPA 1: BUILDER
FROM maven:3.9.6-eclipse-temurin-17-alpine AS builder
WORKDIR /build
COPY pom.xml .
COPY common-domain/pom.xml common-domain/
...
RUN mvn clean install -pl common-domain -DskipTests -B
RUN mvn clean package -pl <microservicio> -DskipTests -B

# ETAPA 2: RUNTIME LIGERO Y SEGURO (NON-ROOT)
FROM eclipse-temurin:17-jre-alpine
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
WORKDIR /app
COPY --from=builder /build/<microservicio>/target/*.jar /app/app.jar
RUN chown -R appuser:appgroup /app
USER appuser
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
```

---

## 5. Orquestación con Docker Compose y Gobernanza de Recursos

El archivo [docker-compose.yml](file:///d:/Poo_Operativos/docker-compose.yml) orquesta la arquitectura completa con límites estrictos de hardware (`deploy.resources.limits`) y parámetros JVM optimizados para cgroups:

| Servicio | Límite CPU | Límite RAM | Reserva CPU | Reserva RAM | Puerto Host |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `postgres` | 0.75 | 512 MB | 0.25 | 256 MB | 5432 |
| `zookeeper` | 0.50 | 384 MB | 0.20 | 192 MB | - |
| `kafka` | 1.00 | 1024 MB | 0.50 | 512 MB | 9092, 29092 |
| `rabbitmq` | 0.75 | 512 MB | 0.25 | 256 MB | 5672, 15672 |
| `voter-registration-service` | 0.50 | 384 MB | 0.20 | 192 MB | - |
| `voting-api` | 1.00 | 512 MB | 0.30 | 256 MB | 8080 |
| `vote-processor` | 0.75 | 512 MB | 0.25 | 256 MB | - |
| `regional-rollup-service` | 0.75 | 512 MB | 0.25 | 256 MB | - |
| `bot-detector-service` | 0.75 | 512 MB | 0.25 | 256 MB | - |
| `analytics-archiver` | 0.75 | 512 MB | 0.25 | 256 MB | - |
| `global-dashboard-worker` | 0.50 | 384 MB | 0.20 | 192 MB | 8082 |
| `regional-dashboard-worker` | 0.50 | 384 MB | 0.20 | 192 MB | 8083 |

---

## 6. Guía de Ejecución y Simulación

### Paso 1: Compilar y Ejecutar Pruebas Unitarias
```powershell
.\mvnw.cmd test
```

### Paso 2: Desplegar la Plataforma con Docker Compose
```bash
docker compose up --build -d
```

### Paso 3: Verificar los Dashboards en Tiempo Real
- **Dashboard Global:** [http://localhost:8082/dashboard.html](http://localhost:8082/dashboard.html)
- **Dashboard Regional:** [http://localhost:8083/dashboard.html](http://localhost:8083/dashboard.html)
- **RabbitMQ Management UI:** [http://localhost:15672](http://localhost:15672) (Usuario: `guest`, Password: `guest`)

### Paso 4: Ejecutar la Simulación de Votos
En PowerShell:
```powershell
.\scripts\simulate_voting.ps1
```
O con Python:
```bash
python scripts/simulate_voting.py
```

El script simula:
1. Emisión de votos válidos para usuarios elegibles (`HTTP 200`).
2. Rechazo instantáneo de votantes no elegibles (`HTTP 403 Forbidden`).
3. Deduplicación (un mismo votante revota por otro candidato; se observa cómo el consolidado descuenta el voto previo y asigna el nuevo).
4. Detección de bots: Ráfaga de votos desde una misma IP que activa la transición a estado `SOSPECHOSA` / `BLOQUEADA`, publicando alertas en Kafka y persistiendo en PostgreSQL.
