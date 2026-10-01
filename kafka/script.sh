#!/bin/bash
set -e

# Load variables from kafka.env
source "$(dirname "$0")/kafka.env"

if [ -z "$TOPICS" ]; then
  echo "⚠️  No topics specified in kafka.env. Nothing to create."
  exit 0
fi

wait_for_kafka() {
  echo "Waiting for Kafka to be ready..."
  until kafka-topics --bootstrap-server "$BOOTSTRAP_SERVER" --list >/dev/null 2>&1; do
    echo "Kafka not ready yet... waiting 5 seconds"
    sleep 5
  done
  echo "Kafka is ready!"
}

create_topic() {
  local topic="$1"
  local partitions="$2"
  echo "Creating topic: $topic ($partitions partitions)"
  kafka-topics \
    --create --if-not-exists \
    --bootstrap-server "$BOOTSTRAP_SERVER" \
    --replication-factor "$REPLICATION_FACTOR" \
    --partitions "$partitions" \
    --topic "$topic"

  local current_partitions
  current_partitions=$(kafka-topics --bootstrap-server "$BOOTSTRAP_SERVER" \
    --describe --topic "$topic" 2>/dev/null | sed -n 's/.*PartitionCount: \([0-9]*\).*/\1/p' | head -1)
  if [ -n "$current_partitions" ] && [ "$current_partitions" -lt "$partitions" ]; then
    kafka-topics --bootstrap-server "$BOOTSTRAP_SERVER" \
      --alter --topic "$topic" --partitions "$partitions"
  elif [ -n "$current_partitions" ] && [ "$current_partitions" -gt "$partitions" ]; then
    # Kafka cannot shrink a topic. Producers use one key per book, so ordering still holds.
    echo "Note: $topic already has $current_partitions partitions (wanted $partitions)."
  fi
}

wait_for_kafka

echo "Creating topics..."
for entry in $TOPICS; do
  topic="${entry%%:*}"
  partitions="$PARTITIONS"
  if [ "$entry" != "$topic" ]; then
    partitions="${entry#*:}"
  fi
  create_topic "$topic" "$partitions"
done

echo -e "\nCurrent topics in Kafka:"
kafka-topics --bootstrap-server "$BOOTSTRAP_SERVER" --list
touch /tmp/resourcelane-topics-ready
