-- ============================================================================
-- PLATAFORMA DISTRIBUIDA DE VOTACIÓN DE ALTA DISPONIBILIDAD
-- DDL DE PERSISTENCIA TRANSACCIONAL, TICKETS, ANTI-FRAUDE Y TELEGRAM - POSTGRESQL
-- ============================================================================

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- 1. Estado global compartido de la plataforma (Patrón GoF State + Simulador + Telegram)
CREATE TABLE IF NOT EXISTS election_control (
    id INT PRIMARY KEY DEFAULT 1,
    lifecycle_state VARCHAR(32) NOT NULL DEFAULT 'HABILITADO',
    strict_census_mode BOOLEAN NOT NULL DEFAULT FALSE,
    simulator_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    simulator_rate_ms INT NOT NULL DEFAULT 1800,
    simulator_batch_size INT NOT NULL DEFAULT 2,
    bot_attack_requested BOOLEAN NOT NULL DEFAULT FALSE,
    telegram_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    telegram_bot_token VARCHAR(256) NOT NULL DEFAULT '',
    telegram_chat_id VARCHAR(128) NOT NULL DEFAULT '',
    telegram_notify_votes BOOLEAN NOT NULL DEFAULT TRUE,
    telegram_notify_fraud BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO election_control (id, lifecycle_state, strict_census_mode, simulator_enabled, simulator_rate_ms, simulator_batch_size)
VALUES (1, 'HABILITADO', FALSE, TRUE, 1800, 2)
ON CONFLICT (id) DO NOTHING;

-- 2. Padrón Electoral Maestro (Sincronizado hacia Kafka compacted topic eligible_voters)
CREATE TABLE IF NOT EXISTS registered_voters (
    voter_id VARCHAR(64) PRIMARY KEY,
    full_name VARCHAR(128) NOT NULL,
    city VARCHAR(80) NOT NULL DEFAULT 'Bogotá',
    region VARCHAR(32) NOT NULL,
    eligible BOOLEAN NOT NULL DEFAULT TRUE,
    registered_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 3. Tickets Oficiales de Votación (Garantiza unicidad atómica: 1 voto por cédula por elección)
CREATE TABLE IF NOT EXISTS vote_tickets (
    ticket_code VARCHAR(48) PRIMARY KEY,
    voter_id VARCHAR(64) NOT NULL,
    voter_name VARCHAR(128) NOT NULL,
    city VARCHAR(80) NOT NULL,
    region VARCHAR(32) NOT NULL,
    election_type VARCHAR(32) NOT NULL,
    candidate_id VARCHAR(64) NOT NULL,
    candidate_name VARCHAR(128) NOT NULL,
    vote_source VARCHAR(16) NOT NULL DEFAULT 'REAL',
    handled_by_node VARCHAR(64) NOT NULL,
    verification_hash VARCHAR(64) NOT NULL,
    voted_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_voter_election UNIQUE (voter_id, election_type)
);

CREATE INDEX IF NOT EXISTS idx_vote_tickets_voter ON vote_tickets(voter_id);
CREATE INDEX IF NOT EXISTS idx_vote_tickets_election ON vote_tickets(election_type);
CREATE INDEX IF NOT EXISTS idx_vote_tickets_voted_at ON vote_tickets(voted_at DESC);

-- 4. Auditoría Histórica Inmutable de Votos (Persistida por Analytics Archiver desde Kafka raw_votes)
CREATE TABLE IF NOT EXISTS audit_votes (
    id BIGSERIAL PRIMARY KEY,
    ticket_code VARCHAR(48) NOT NULL,
    voter_id VARCHAR(64) NOT NULL,
    candidate_id VARCHAR(64) NOT NULL,
    candidate_name VARCHAR(128) NOT NULL,
    city VARCHAR(80) NOT NULL,
    region VARCHAR(32) NOT NULL,
    election_type VARCHAR(32) NOT NULL,
    vote_source VARCHAR(16) NOT NULL DEFAULT 'REAL',
    handled_by_node VARCHAR(64) NOT NULL,
    ip_address VARCHAR(64) NOT NULL,
    voted_at TIMESTAMP WITH TIME ZONE NOT NULL,
    archived_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_audit_votes_voter_id ON audit_votes(voter_id);
CREATE INDEX IF NOT EXISTS idx_audit_votes_election ON audit_votes(election_type);
CREATE INDEX IF NOT EXISTS idx_audit_votes_source ON audit_votes(vote_source);
CREATE INDEX IF NOT EXISTS idx_audit_votes_region ON audit_votes(region);
CREATE INDEX IF NOT EXISTS idx_audit_votes_node ON audit_votes(handled_by_node);
CREATE INDEX IF NOT EXISTS idx_audit_votes_voted_at ON audit_votes(voted_at DESC);

-- 5. Registro Forense de Incidentes Anti-Fraude (Patrón GoF State: NORMAL / SOSPECHOSA / BLOQUEADA)
CREATE TABLE IF NOT EXISTS security_incidents (
    id VARCHAR(64) PRIMARY KEY,
    ip_address VARCHAR(64) NOT NULL,
    risk_level VARCHAR(32) NOT NULL,
    reason VARCHAR(512) NOT NULL,
    vote_count INT NOT NULL,
    detected_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_security_incidents_ip ON security_incidents(ip_address);
CREATE INDEX IF NOT EXISTS idx_security_incidents_level ON security_incidents(risk_level);
CREATE INDEX IF NOT EXISTS idx_security_incidents_detected_at ON security_incidents(detected_at DESC);

-- 6. Bloqueos Activos Anti-Fraude (IPs y Cédulas bloqueadas por suplantación o repetición abusiva)
CREATE TABLE IF NOT EXISTS blocked_entities (
    entity_key VARCHAR(128) PRIMARY KEY,
    entity_type VARCHAR(32) NOT NULL,
    reason VARCHAR(512) NOT NULL,
    attempt_count INT NOT NULL DEFAULT 1,
    blocked_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 7. Telemetría de Distribución de Carga entre Nodos AWS
CREATE TABLE IF NOT EXISTS node_telemetry (
    node_id VARCHAR(64) PRIMARY KEY,
    availability_zone VARCHAR(32) NOT NULL DEFAULT 'us-east-1',
    requests_handled BIGINT NOT NULL DEFAULT 0,
    votes_processed BIGINT NOT NULL DEFAULT 0,
    last_heartbeat TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Padrón inicial con cédulas de ejemplo + 1 cédula revocada para pruebas de inelegibilidad (HTTP 403)
INSERT INTO registered_voters (voter_id, full_name, city, region, eligible) VALUES
('1001234567', 'Carlos Mendoza', 'Barranquilla', 'NORTE', TRUE),
('1002345678', 'María Fernández', 'Pasto', 'SUR', TRUE),
('1003456789', 'Juan Pérez', 'Bogotá', 'CENTRO', TRUE),
('1004567890', 'Ana Lucía Gómez', 'Cúcuta', 'ESTE', TRUE),
('1005678901', 'Diego Ramírez', 'Cali', 'OESTE', TRUE),
('1006789012', 'Sofía Castillo', 'Cartagena', 'NORTE', TRUE),
('1007890123', 'Andrés Morales', 'Popayán', 'SUR', TRUE),
('1008901234', 'Camila Herrera', 'Medellín', 'CENTRO', TRUE),
('1009012345', 'Mateo Ríos', 'Villavicencio', 'ESTE', TRUE),
('1010123456', 'Valentina Silva', 'Pereira', 'OESTE', TRUE),
('voter_001', 'Carlos Mendoza (Demo)', 'Barranquilla', 'NORTE', TRUE),
('voter_002', 'María Fernández (Demo)', 'Pasto', 'SUR', TRUE),
('voter_003', 'Juan Pérez (Demo)', 'Bogotá', 'CENTRO', TRUE),
('0000000000', 'Cédula Inhabilitada (Prueba 403)', 'Bogotá', 'CENTRO', FALSE)
ON CONFLICT (voter_id) DO NOTHING;
