# ResourceLane on local Kubernetes

This guide uses a fresh local database and a single PostgreSQL, Kafka, and matching-engine instance. The steps were verified with an isolated Colima Kubernetes profile on macOS.

## Prepare images and secrets

Start an isolated Kubernetes profile and confirm the node reports `Ready`. Build images in that profile and pull the infrastructure images used by manifests with `imagePullPolicy: Never`:

```bash
colima start --profile resourcelane --cpu 4 --memory 8 --disk 20 --kubernetes
kubectl --context colima-resourcelane get nodes
docker --context colima-resourcelane compose build
docker --context colima-resourcelane pull postgres:15
docker --context colima-resourcelane pull confluentinc/cp-kafka:7.6.0
docker --context colima-resourcelane pull confluentinc/cp-zookeeper:7.6.0
```

Copy `.env.example` to `.env` and replace each placeholder. Use one JWT key across the three Java services and one internal token between the order and wallet services. Keep `.env` out of Git.

```bash
kubectl --context colima-resourcelane apply -f k8s/namespace.yaml
kubectl --context colima-resourcelane -n resource-lane create secret generic resource-lane-secrets --from-env-file=.env
```

## Deploy

```bash
kubectl --context colima-resourcelane apply -f k8s/DB --recursive
kubectl --context colima-resourcelane -n resource-lane rollout status statefulset/postgres
kubectl --context colima-resourcelane apply -f k8s/kafka/zookeeper-service.yaml -f k8s/kafka/zookeeper-deployment.yaml
kubectl --context colima-resourcelane -n resource-lane rollout status statefulset/zookeeper
kubectl --context colima-resourcelane apply -f k8s/kafka/kafka-service.yaml -f k8s/kafka/kafka-deployment.yaml
kubectl --context colima-resourcelane -n resource-lane rollout status statefulset/kafka
kubectl --context colima-resourcelane apply -f k8s/kafka/topics-job.yaml
kubectl --context colima-resourcelane -n resource-lane wait --for=condition=Complete job/kafka-topics --timeout=180s
kubectl --context colima-resourcelane apply -f k8s/backend --recursive
kubectl --context colima-resourcelane apply -f k8s/MatchingEngine --recursive
kubectl --context colima-resourcelane apply -f k8s/frontend --recursive
kubectl --context colima-resourcelane -n resource-lane rollout status deployment/auth-service
kubectl --context colima-resourcelane -n resource-lane rollout status deployment/wallet-service
kubectl --context colima-resourcelane -n resource-lane rollout status deployment/trade-service
kubectl --context colima-resourcelane -n resource-lane rollout status statefulset/matching-engine
kubectl --context colima-resourcelane -n resource-lane rollout status deployment/frontend
kubectl --context colima-resourcelane -n resource-lane get pods,svc
```

Forward the frontend with `kubectl --context colima-resourcelane -n resource-lane port-forward svc/frontend 8088:80` and run `python3 scripts/smoke_k8s.py http://127.0.0.1:8088` in a second terminal. The smoke test also checks that a buy market order respects its submitted maximum price and releases a cancelled order's reservation.

This deployment starts with an empty database and creates a persistent volume claim for matcher state. It does not migrate data from an older schema. Back up an existing ResourceLane database and matcher volume before an upgrade; outstanding orders from earlier versions have no reservations. Do not start the new matcher against old committed Kafka offsets without a matching state snapshot or a deliberate recovery plan.

ZooKeeper persists both its data and transaction-log directories. Existing deployments created without the log volume need a planned migration or a fresh demo namespace; Kubernetes cannot update a StatefulSet's volume claim templates in place. Keep Kafka and ZooKeeper metadata together when recovering the broker.
