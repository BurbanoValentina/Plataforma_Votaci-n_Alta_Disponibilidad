# Plataforma Distribuida de Votación de Alta Disponibilidad (AWS ALB + Kafka + RabbitMQ + PostgreSQL)

Sistema distribuido de votación en vivo (Elección **Nacional** y Elección **Regional**) construido con **Java 17 + Spring Boot**, **Programación Orientada a Objetos (Dominio Rico, Records, Patrones GoF Strategy y State, SOLID/DIP)**, **Pruebas Unitarias Aisladas (JUnit 5 + Mockito)**, **Apache Kafka (KRaft + Topics Compactados + KTable)**, **RabbitMQ (Fanout + Topic Exchanges)**, **PostgreSQL (Auditoría Forense y Tickets Únicos)** y **Despliegue Distribuido con Balanceo de Carga en AWS**.

## Arquitectura Distribuida (Menos Contenedores, Mayor Distribución)

Se optimizó la arquitectura pasando de 14 contenedores monolíticos sin balanceo a **6 contenedores totales distribuidos en 3 instancias AWS EC2 en 2 Zonas de Disponibilidad detrás de un AWS Application Load Balancer (ALB)**:

- **AWS Application Load Balancer (`voting-alb`)**: Distribuye peticiones HTTP, REST y SSE entre los nodos de aplicación con Health Checks en `/api/health`.
- **EC2 Core Node (`voting-core-node` — `us-east-1a`, 4 contenedores en VPC privada)**:
  1. `postgres` (PostgreSQL 15 Alpine): Persistencia transaccional de `vote_tickets` (unicidad por cédula y elección), `audit_votes`, `registered_voters`, `security_incidents`, `election_control` y `node_telemetry`.
  2. `kafka` (Apache Kafka en modo KRaft, sin Zookeeper): Topics compactados `eligible_voters` (KTable) y `raw_votes` (3 particiones, deduplicación por clave), más `security_alerts`.
  3. `rabbitmq`: Fanout Exchange `live_results_global` (1s) y Topic Exchange `live_results_regional` (`results.*`, 5s).
  4. `voting-stream-processor`: 4 grupos de consumidores Kafka independientes (`VoteProcessorService`, `RegionalRollupService` con Patrón Strategy, `BotDetectorService` con Patrón State, `AnalyticsArchiverService`), sincronización inicial de padrón y simulador en tiempo real.
- **EC2 App Node 1 (`voting-app-node-1` — `us-east-1a`, 1 contenedor)** & **EC2 App Node 2 (`voting-app-node-2` — `us-east-1b`, 1 contenedor)**:
  5. `voting-edge-node` (réplica 1) y 6. `voting-edge-node` (réplica 2): Sirven el portal ciudadano (`/`), panel de administración (`/admin.html`), tableros en vivo, API de votación verificada, KTable local sincronizada desde Kafka `eligible_voters` y suscripción por nodo a RabbitMQ Fanout/Topic.

## Funcionalidades Principales (Sin Firebase ni Login)

- **Portal Ciudadano (`/`)**: Permite votar en **Elección Nacional** o **Elección Regional** ingresando **cédula**, **ciudad** (mapeada automáticamente a su región electoral) y **candidato**.
- **Unicidad por Cédula + Ticket Electoral Digital**: Si una cédula ya votó en esa elección, el sistema bloquea el voto repetido (HTTP 409) y muestra inmediatamente el **Ticket Electoral Oficial** original con código verificable y hash SHA-256.
- **Panel de Administración (`/admin.html`)**:
  - Simulador de tráfico en tiempo real + diferenciación clara entre **Votos Reales (Ciudadanos)** y **Votos Simulados**.
  - Tablas completas en vivo: Escrutinio Nacional, Escrutinio Regional Ponderado (Strategy), Registro Maestro de Votaciones y Tickets (PostgreSQL), Padrón Electoral (Kafka KTable) e Incidentes Anti-Fraude (Patrón State).
  - Control del ciclo electoral (`HABILITADO`, `EN_AUDITORIA`, `CERRADO`), botón de ráfaga, simulador de ataque Bot y monitor de balanceo de carga entre nodos AWS.

## Ejecución de Pruebas Unitarias Aisladas (TDD en RAM)

```bash
cd backend
./mvnw test
```

## Despliegue Local con Balanceo de Carga (7 Contenedores)

```bash
docker compose up -d --build
```
