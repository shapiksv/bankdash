#!/usr/bin/env bash
# Creates every topic from topics.txt in the local Kafka of docker-compose.yml. Safe to re-run.
# Usage (from anywhere, with `docker compose up -d --wait` already done): ./infra/kafka/create-topics.sh
set -euo pipefail

cd "$(dirname "$0")/../.."

grep -Ev '^[[:space:]]*(#|$)' infra/kafka/topics.txt | while read -r topic partitions; do
  docker compose exec -T kafka /opt/kafka/bin/kafka-topics.sh \
    --bootstrap-server kafka:29092 \
    --create --if-not-exists \
    --topic "$topic" \
    --partitions "$partitions" \
    --replication-factor 1 </dev/null  # exec would otherwise swallow the rest of the list from stdin
done

docker compose exec -T kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server kafka:29092 --list
