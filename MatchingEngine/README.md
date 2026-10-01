# ResourceLane matching engine

The C++ engine consumes orders from Kafka and publishes match, status, order-book, and last-price events. It keeps the book in memory and saves resting orders, the last price, and processed order IDs to the configured state file. In the root Docker Compose setup, that file is stored in the `matcher-state` volume so a restart can recover it.

Build it with the root Docker Compose file:

```bash
docker compose build matching-engine
```

The Docker build runs the C++ CTest suite. See the [root README](../README.md) for the full local run.
