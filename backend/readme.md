# ResourceLane backend

The backend has three Spring Boot services. `auth-service` handles TOTP login and JWT sessions. `wallet-service` manages simulated credits, resource units, and idempotent settlement. `trade-service` accepts orders, publishes them through a database outbox, and consumes matching events. Kafka carries order, match, and status messages. The [root README](../README.md) describes the data flow and deployment limits.
