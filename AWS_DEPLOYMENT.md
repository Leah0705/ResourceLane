# AWS deployment checklist (not yet deployed)

The local Kubernetes setup in [k8s/readme.md](k8s/readme.md) is verified. AWS deployment awaits an account, a domain/origin TLS decision, and a budget. No AWS resources or performance results are claimed here.

## Target layout

- Build React with `npm ci && npm run build` in `frontend/`. Put `frontend/dist/` in a private S3 bucket. Serve it through a CloudFront S3 origin with origin access control (OAC), using `/index.html` as the default object.
- Run the three Java services, PostgreSQL, Kafka, ZooKeeper, and the matching engine on one EC2 instance for this demo. Persist both PostgreSQL data and matcher state; back them up together. Keep the application secrets in an AWS secret store or a restricted instance file, outside Git.
- Put an HTTPS API origin in front of the EC2 services. The current frontend Nginx routes `/api/auth/`, `/api/wallet/`, `/api/trade/`, and `/ws` to their services and can be adapted for this purpose. CloudFront routes `/api/*` and `/ws*` to that origin and all other paths to S3. The React build already uses relative API URLs and same-host WebSocket URLs.

## Before creating resources

1. Pick a region, domain name, monthly spending limit, and an EC2 size that can hold the database, Kafka, matcher, and three Java processes. Set a billing alert. Select how TLS will terminate at the API origin; test the certificate and origin hostname before connecting CloudFront.
2. Create an AWS-specific Compose configuration that exposes only the HTTPS reverse proxy. The current `docker-compose.yml` publishes database, Kafka, and application ports to the host and must not be deployed unchanged on a public EC2 instance. Restrict SSH and origin access with security groups; do not expose PostgreSQL, Kafka, ZooKeeper, Actuator, Prometheus, or Loki publicly.
3. Decide how to restore the database and matcher snapshot together. This single-instance design is a portfolio demo, not a highly available service.

## Deploy and verify when an account is available

1. Create the private S3 bucket and CloudFront OAC. Upload the compiled React `dist/` files. Test a JS/CSS asset and a direct React route. Make the SPA fallback apply only to the S3 behavior, so API errors are not rewritten as `index.html`.
2. Configure CloudFront behaviors: `/api/*` accepts the application's HTTP methods, forwards the authentication cookie and required request headers, and disables API caching. `/ws*` forwards the WebSocket handshake headers and cookie and disables caching. Both behaviors use the HTTPS API origin. Test that unauthenticated API calls return the expected status through CloudFront.
3. Start the backend on EC2 with persistent volumes and restricted secrets. Confirm all services and Kafka topics are ready before accepting orders. Run the repository smoke test against the CloudFront URL, then verify a browser login, a trade update over WebSocket, order settlement, and a direct page refresh.
4. Confirm the backend logs and service metrics can be queried from an authorized operator location. Reboot the EC2 instance and check that database data, matcher state, and order processing recover. Record the region, resource sizes, test result, and monthly cost before describing this as an AWS deployment on a resume.

Official references: [CloudFront S3 OAC](https://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/private-content-restricting-access-to-s3.html), [multiple origins](https://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/DownloadDistS3AndCustomOrigins.html), [WebSockets](https://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/distribution-working-with.websockets.html).
