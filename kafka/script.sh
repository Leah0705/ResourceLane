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
  echo "Creating topic: $topic"
  kafka-topics \
    --create --if-not-exists \
    --bootstrap-server "$BOOTSTRAP_SERVER" \
    --replication-factor "$REPLICATION_FACTOR" \
    --partitions "$PARTITIONS" \
    --topic "$topic"

  local current_partitions
  current_partitions=$(kafka-topics --bootstrap-server "$BOOTSTRAP_SERVER" \
    --describe --topic "$topic" 2>/dev/null | sed -n 's/.*PartitionCount: \([0-9]*\).*/\1/p' | head -1)
  if [ -n "$current_partitions" ] && [ "$current_partitions" -lt "$PARTITIONS" ]; then
    kafka-topics --bootstrap-server "$BOOTSTRAP_SERVER" \
      --alter --topic "$topic" --partitions "$PARTITIONS"
  fi
}

wait_for_kafka

echo "Creating topics..."
for topic in $TOPICS; do
  create_topic "$topic"
done

echo -e "\nCurrent topics in Kafka:"
kafka-topics --bootstrap-server "$BOOTSTRAP_SERVER" --list
touch /tmp/resourcelane-topics-ready
