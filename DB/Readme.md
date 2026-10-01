# ResourceLane PostgreSQL

The local database name is `resourcelane`. The tested deployment starts PostgreSQL using the manifests in [`k8s/DB`](../k8s/DB). The root [deployment guide](../k8s/readme.md) has the startup order.

For the optional standalone database Compose file, copy [`.env.example`](../.env.example) to a root `.env`, set `DB_PASSWORD`, and run `docker compose --env-file .env -f DB/docker-compose.yml up -d` from the repository root.
