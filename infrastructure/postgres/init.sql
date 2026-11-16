-- ========================================================
-- PLATAFORMA DE VOTACIÓN DE ALTA DISPONIBILIDAD
-- DDL DE INICIALIZACIÓN Y AUDITORÍA FORENSE - POSTGRESQL
-- ========================================================

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Tabla de Auditoría Histórica Inmutable de Votos
CREATE TABLE IF NOT EXISTS audit_votes (
    id BIGSERIAL PRIMARY KEY,
    voter_id VARCHAR(64) NOT NULL,
    candidate_id VARCHAR(64) NOT NULL,
    region VARCHAR(32) NOT NULL,
    ip_address VARCHAR(64) NOT NULL,
    voted_at TIMESTAMP WITH TIME ZONE NOT NULL,
    archived_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_audit_votes_voter_id ON audit_votes(voter_id);
CREATE INDEX IF NOT EXISTS idx_audit_votes_candidate_id ON audit_votes(candidate_id);
CREATE INDEX IF NOT EXISTS idx_audit_votes_region ON audit_votes(region);
CREATE INDEX IF NOT EXISTS idx_audit_votes_voted_at ON audit_votes(voted_at);

-- Tabla de Registro Forense de Incidentes de Seguridad y Fraude (Bot Detector)
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
CREATE INDEX IF NOT EXISTS idx_security_incidents_detected_at ON security_incidents(detected_at);

-- Tabla Opcional de Registro Maestro de Votantes Elegibles
CREATE TABLE IF NOT EXISTS registered_voters (
    voter_id VARCHAR(64) PRIMARY KEY,
    full_name VARCHAR(128) NOT NULL,
    region VARCHAR(32) NOT NULL,
    registered_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Datos iniciales de cortesía en caso de sincronización con base de datos
INSERT INTO registered_voters (voter_id, full_name, region) VALUES
('voter_001', 'Carlos Mendoza', 'NORTE'),
('voter_002', 'Maria Fernandez', 'SUR'),
('voter_003', 'Juan Perez', 'CENTRO'),
('voter_004', 'Ana Lucia Gomez', 'ESTE'),
('voter_005', 'Diego Ramirez', 'OESTE')
ON CONFLICT (voter_id) DO NOTHING;
