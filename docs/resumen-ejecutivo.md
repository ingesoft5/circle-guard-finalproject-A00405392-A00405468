# CircleGuard — Executive Summary (Part 2)

*Ingeniería de Software V, Universidad Icesi · José Rojas (platform) and Pierre Cuevas (application and quality) · November 2026*

**What it is.** CircleGuard is a privacy-preserving campus health-status and access-control platform: eight Spring Boot services plus an Expo web client, backed by PostgreSQL, Neo4j, Kafka and Redis. Our job was not to rewrite it but to **deliver it safely**: build, test, version, secure, deploy and operate it automatically.

**What we delivered**

- **CI/CD:** a Jenkins pipeline with commit lint, build, over 120 automated tests, a SonarQube quality gate, semantic versioning (`v1.0.0` to `v1.4.0`), immutable images, promotion dev → stage → prod with a human approval, and a tested rollback.
- **Infrastructure as code:** Terraform with reusable modules, four environments, remote state with locking and drift detection. Everything created in tests was destroyed and verified.
- **Kubernetes:** one single-node k3s cluster per environment and one Helm chart for every service and the middleware, with probes, autoscaling, Ingress and TLS (cert-manager). Deployments use `--atomic`, so a release that does not become ready rolls back by itself.
- **Design patterns:** Circuit Breaker + Retry, External Configuration + Feature Toggle, and Cache-aside, each tested with injected failures.
- **Security:** blocking Trivy and gitleaks scans in the pipeline, Sealed Secrets (no secrets in the repository), default-deny network policies, non-root read-only pods, RBAC in Kubernetes and Jenkins, and TLS.
- **Release management:** release notes per version, a per-environment change log and an executed rollback.

**What it cost.** AWS Cost Explorer shows **USD 53.56 of gross usage from 1 to 22 November (USD 49.45 by day 18), fully offset by AWS credits (net USD 0.00)**; the team budgets on the gross figure (USD 60). Running everything 24×7 would cost about **USD 360 a month**, so the cloud is switched on only in short, planned windows (a Jenkins window lasts about 12 minutes, ≈ USD 0.03) and an idle environment costs **USD 0.93 a day** (disks and reserved IPs). Choosing k3s over EKS avoided about USD 216 a month in control-plane fees. The two largest costs were avoidable (machines left on for two days, USD 21.7, and Jenkins left on for four, USD 19) and produced our "nothing stays on" rule.

**What we learned.** Test the real thing: template tests passed while a real cluster exposed start-up failures, and our scanning tools silently read empty folders when run inside Jenkins' container. Every "blocked" test now has a matching positive control. Cost is a feature: measuring against the budget changed how we work. And one pull request at a time, reviewed by the other member, kept the history clean.

**Risks and open items.** 74 library CVEs inherited from Spring Boot 3.2.4 (all eight services) are accepted per CVE with an expiry date of 2026-12-31; the fix is an upgrade to 3.5.x. Each environment is a single node by a deliberate cost trade-off (no high availability). Real Let's Encrypt certificates and Jenkins roles are exercised only in a cloud window. Observability (Prometheus, Grafana, Alertmanager, Jaeger, Loki) runs only in Docker Compose, not in Kubernetes. Load testing (Locust, with a measured BCrypt fix) and OWASP ZAP are done; the Terraform and Kubernetes pipeline jobs have not yet run against AWS.

*Details (architecture, Terraform, Kubernetes, security, costs, operations manual): `docs/` in the repository.*
