# ResourceLane Kafka

The local stack uses the `order`, `trade`, `orderbook`, `ltp`, and `status` topics, plus `trade.DLT` and `status.DLT` for settlement messages that fail validation. `order`, `trade`, `orderbook`, and `ltp` have one partition because each carries a single order book; `status` is keyed by order ID. The root Docker Compose file starts Kafka and creates the topics through `kafka/script.sh`. The [Kubernetes guide](../k8s/readme.md) uses `k8s/kafka/topics-job.yaml` instead.

For the complete startup order and verification steps, see the [root README](../README.md).
