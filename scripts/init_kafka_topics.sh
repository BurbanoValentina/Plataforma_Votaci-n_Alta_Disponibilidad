#!/bin/sh
# Script para inicializar topics compactados en Kafka
echo "Esperando que el broker Kafka esté disponible..."
cub kafka-ready -b kafka:9092 1 40 2>/dev/null || sleep 10

echo "Creando topic compactado: eligible_voters..."
kafka-topics --create --if-not-exists \
  --bootstrap-server kafka:9092 \
  --topic eligible_voters \
  --partitions 3 \
  --replication-factor 1 \
  --config cleanup.policy=compact \
  --config delete.retention.ms=86400000 \
  --config min.cleanable.dirty.ratio=0.01

echo "Creando topic compactado: raw_votes (Deduplicación por user_id)..."
kafka-topics --create --if-not-exists \
  --bootstrap-server kafka:9092 \
  --topic raw_votes \
  --partitions 3 \
  --replication-factor 1 \
  --config cleanup.policy=compact \
  --config delete.retention.ms=86400000 \
  --config min.cleanable.dirty.ratio=0.01

echo "Creando topic de incidentes: security_alerts..."
kafka-topics --create --if-not-exists \
  --bootstrap-server kafka:9092 \
  --topic security_alerts \
  --partitions 3 \
  --replication-factor 1

echo "Topics de Kafka configurados exitosamente:"
kafka-topics --list --bootstrap-server kafka:9092
