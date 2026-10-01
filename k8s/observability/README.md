# Local logs and metrics

Deploy this after the main [Kubernetes setup](../readme.md). The three Java services expose `/actuator/prometheus` inside the cluster. Prometheus scrapes those endpoints every 15 seconds. Fluent Bit tails ResourceLane container logs and sends them to Loki. These services have ClusterIP access only; use port forwarding for local queries.

```bash
docker --context colima-resourcelane pull prom/prometheus:v3.14.0
docker --context colima-resourcelane pull grafana/loki:3.7.8
docker --context colima-resourcelane pull fluent/fluent-bit:5.1.2
kubectl --context colima-resourcelane apply -f k8s/observability/loki.yaml
kubectl --context colima-resourcelane -n resource-lane rollout status deployment/loki
kubectl --context colima-resourcelane apply -f k8s/observability/fluent-bit.yaml
kubectl --context colima-resourcelane -n resource-lane rollout status daemonset/fluent-bit
kubectl --context colima-resourcelane apply -f k8s/observability/prometheus.yaml
kubectl --context colima-resourcelane -n resource-lane rollout status deployment/prometheus
```

In separate terminals, forward `svc/prometheus 9090:9090` and `svc/loki 3100:3100` with `kubectl --context colima-resourcelane -n resource-lane port-forward`. Check the three scrape targets in Prometheus at `http://127.0.0.1:9090/targets`, or query `up{job=~"auth-service|wallet-service|trade-service"}`. Every target should report `1`.

Query recent order logs:

```bash
curl -G 'http://127.0.0.1:3100/loki/api/v1/query_range' \
  --data-urlencode 'query={job="resource-lane"} |= "Order queued"' \
  --data-urlencode 'since=1h'
```

The log collector mounts the K3s Docker log targets read-only because `/var/log/containers` points through `/var/log/pods` to `/var/lib/docker/containers` in this Colima profile. Loki stores logs on a 1 GiB local PVC. Prometheus retains one day of data in ephemeral storage. This setup is for local study; do not expose the unauthenticated Prometheus or Loki services on a public network.
