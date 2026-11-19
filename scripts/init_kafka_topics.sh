#!/bin/sh
# Delegador principal para inicialización de tópicos Kafka
# Invoca el módulo estructurado en scripts/shell/init_kafka_topics.sh
DIR="$(cd "$(dirname "$0")" && pwd)"
if [ -f "$DIR/shell/init_kafka_topics.sh" ]; then
  sh "$DIR/shell/init_kafka_topics.sh"
else
  echo "Error: no se encontró scripts/shell/init_kafka_topics.sh"
  exit 1
fi
