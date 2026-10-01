# ResourceLane Kafka

The local stack uses the `order`, `trade`, `orderbook`, `ltp`, and `status` topics. The root Docker Compose file starts Kafka and creates the topics through `kafka/script.sh`. The [Kubernetes guide](../k8s/readme.md) uses `k8s/kafka/topics-job.yaml` instead.

For the complete startup order and verification steps, see the [root README](../README.md).
