#!/bin/sh
# ============================================================================
# Inicialización de Tópicos Compactados y Particionados en Apache Kafka (KRaft)
# Plataforma de Votación de Alta Disponibilidad
# ============================================================================
set -e

BOOTSTRAP_SERVER="${KAFKA_BOOTSTRAP_SERVERS:-kafka:9092}"
PARTITIONS="${KAFKA_DEFAULT_PARTITIONS:-3}"
REPLICATION="${KAFKA_DEFAULT_REPLICATION:-1}"

echo "============================================================================"
echo " Conectando con Broker Kafka en: $BOOTSTRAP_SERVER"
echo "============================================================================"

# Esperar a que el broker Kafka responda
echo "[1/4] Verificando disponibilidad de Kafka..."
for i in $(seq 1 30); do
  if kafka-topics --list --bootstrap-server "$BOOTSTRAP_SERVER" >/dev/null 2>&1; then
    echo "      Kafka listo tras $i intento(s)."
    break
  fi
  echo "      Esperando broker... ($i/30)"
  sleep 2
done

# Crear topic compactado: eligible_voters (Censo KTable en memoria)
echo "[2/4] Configurando topic compactado: eligible_voters..."
kafka-topics --create --if-not-exists \
  --bootstrap-server "$BOOTSTRAP_SERVER" \
  --topic eligible_voters \
  --partitions "$PARTITIONS" \
  --replication-factor "$REPLICATION" \
  --config cleanup.policy=compact \
  --config delete.retention.ms=86400000 \
  --config min.cleanable.dirty.ratio=0.01

# Crear topic compactado: raw_votes (Deduplicación por voter_id + election_type)
echo "[3/4] Configurando topic compactado: raw_votes..."
kafka-topics --create --if-not-exists \
  --bootstrap-server "$BOOTSTRAP_SERVER" \
  --topic raw_votes \
  --partitions "$PARTITIONS" \
  --replication-factor "$REPLICATION" \
  --config cleanup.policy=compact \
  --config delete.retention.ms=86400000 \
  --config min.cleanable.dirty.ratio=0.01

# Crear topic particionado: security_alerts (Detección de bots y anomalías)
echo "[4/4] Configurando topic de incidentes: security_alerts..."
kafka-topics --create --if-not-exists \
  --bootstrap-server "$BOOTSTRAP_SERVER" \
  --topic security_alerts \
  --partitions "$PARTITIONS" \
  --replication-factor "$REPLICATION"

echo "============================================================================"
echo " Tópicos configurados exitosamente en $BOOTSTRAP_SERVER:"
kafka-topics --list --bootstrap-server "$BOOTSTRAP_SERVER"
echo "============================================================================"
