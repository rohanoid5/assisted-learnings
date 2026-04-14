# 13.1 — Production Readiness Review

## Concept

Deploying software to production is easy. Keeping it running reliably is the hard part. A production readiness review (PRR) is a systematic evaluation of whether a service is prepared for real traffic — not just "does it work?" but "what happens when things go wrong, and will we know about it in time?" Companies like Google, Netflix, and Uber formalize this as a gate: no service goes live without passing a review that covers security, reliability, observability, scalability, and operational maturity.

The PRR isn't a bureaucratic checkbox exercise. It's a forcing function that surfaces blind spots before users find them. The review produces a living document — updated before every major launch — that captures the architecture, failure modes, monitoring coverage, and operational procedures for the service. If your SLO dashboard from Module 09 is the *what* of reliability, the PRR is the *how* and *why*.

---

## Deep Dive

### Why Production Readiness Reviews Exist

Most production incidents share a common root cause: assumptions. The developer assumed the database would always be reachable. The SRE assumed the service owner documented the rollback procedure. The product manager assumed the system could handle 10× traffic during a launch. A PRR systematically eliminates these assumptions by forcing teams to answer hard questions before the pager goes off.

```
┌──────────────────────────────────────────────────────────────────┐
│                   Production Readiness Flow                       │
│                                                                   │
│  Development ──▶ PRR Checklist ──▶ Review Meeting ──▶ Launch      │
│       │              │                   │               │        │
│       │              ▼                   ▼               ▼        │
│       │         Gap Analysis        Action Items     Go/No-Go     │
│       │              │                   │                        │
│       │              └──── Remediation ──┘                        │
│       │                       │                                   │
│       └───────────────────────┘                                   │
│              (iterate until all gaps closed)                       │
└──────────────────────────────────────────────────────────────────┘
```

### The PRR Checklist — Eight Pillars

A comprehensive production readiness review covers eight pillars. Each pillar has specific criteria that must be satisfied before a service is considered production-ready.

#### Pillar 1: Architecture & Design

| Criterion | Question | Evidence |
|-----------|----------|----------|
| Architecture documented | Is there an up-to-date architecture diagram? | Architecture decision records in `docs/` |
| Dependencies mapped | Are all upstream/downstream dependencies identified? | Dependency graph with SLO impact analysis |
| Single points of failure | Are there any single points of failure? | HA design for every stateful component |
| Data flow documented | Is the data flow (ingress → processing → storage) clear? | Data flow diagram with PII annotations |
| API contracts stable | Are API contracts versioned and backward-compatible? | OpenAPI spec + contract tests |

```
┌─────────────────────────────────────────────────────────┐
│              DeployForge Dependency Map                   │
│                                                          │
│  External:  GitHub API ──┐                               │
│             DNS (Route53) ┤                               │
│             Container Reg ─┤                              │
│                            ▼                              │
│  ┌──────────────────────────────────────┐                │
│  │          API Gateway                  │                │
│  │  ┌──────┐  ┌──────┐  ┌──────────┐   │                │
│  │  │Routes│  │AuthN │  │Rate Limit│   │                │
│  │  └──┬───┘  └──┬───┘  └────┬─────┘   │                │
│  │     └─────────┼───────────┘          │                │
│  └───────────────┼──────────────────────┘                │
│                  │                                        │
│        ┌─────────┼─────────┐                             │
│        ▼         ▼         ▼                             │
│  ┌──────────┐ ┌──────┐ ┌──────────┐                     │
│  │PostgreSQL│ │Redis │ │  Worker  │                      │
│  │ (primary)│ │(cache)│ │(BullMQ) │                      │
│  └──────────┘ └──────┘ └──────────┘                     │
└─────────────────────────────────────────────────────────┘
```

#### Pillar 2: Security

| Criterion | Question | Evidence |
|-----------|----------|----------|
| Image scanning | Are container images scanned for CVEs in CI? | Trivy scan in GitHub Actions (Module 03) |
| Secrets management | Are secrets stored in a secrets manager, not env vars? | Kubernetes Secrets with encryption at rest |
| Network policies | Is east-west traffic restricted to necessary paths? | NetworkPolicies per namespace (Module 06) |
| RBAC configured | Do service accounts follow least-privilege? | Per-service ServiceAccount with minimal ClusterRole |
| TLS everywhere | Is all traffic encrypted in transit? | cert-manager TLS for Ingress + mTLS between services |
| Supply chain | Are base images pinned and provenance verified? | Pinned digests in Dockerfiles + Cosign signatures |

> **Key insight:** Security isn't a layer you bolt on at the end — it's a property of the system at every level. The PRR forces you to verify that security decisions made in Module 03 survived integration with real infrastructure.

#### Pillar 3: Reliability

| Criterion | Question | Evidence |
|-----------|----------|----------|
| SLOs defined | Are SLOs defined for every user-facing endpoint? | SLO spec documents from Module 09 |
| Error budgets tracked | Is error budget consumption visible in real-time? | Grafana error budget dashboard |
| Failure modes analyzed | Has FMEA been performed? | Failure mode table with severity/probability/detection |
| Graceful degradation | Does the service degrade gracefully under partial failure? | Circuit breakers, fallback responses, queue backpressure |
| Chaos tested | Have chaos experiments validated resilience? | Chaos experiment results from Module 09 |
| Rollback tested | Has the rollback procedure been tested end-to-end? | Documented rollback with verified recovery time |

#### Pillar 4: Observability

| Criterion | Question | Evidence |
|-----------|----------|----------|
| Metrics instrumented | Are RED metrics (Rate, Error, Duration) instrumented? | Prometheus metrics on API + Worker (Module 08) |
| Dashboards exist | Are there dashboards for service health and SLO tracking? | Grafana dashboards with ownership labels |
| Alerts configured | Are alerts tied to SLOs with multi-burn-rate windows? | PrometheusRules with critical/warning/ticket tiers |
| Traces instrumented | Are distributed traces connected across services? | OpenTelemetry SDK → Jaeger backend |
| Logs structured | Are logs structured (JSON) with correlation IDs? | Structured logging with request_id propagation |
| On-call integration | Do alerts route to the correct on-call rotation? | Alertmanager routes → PagerDuty/Slack |

#### Pillar 5: Scalability

| Criterion | Question | Evidence |
|-----------|----------|----------|
| Load tested | Has the service been load-tested at 2× expected traffic? | Load test results with latency percentiles |
| Auto-scaling configured | Are HPA/VPA configured with appropriate metrics? | HPA for API, VPA for Worker (Module 12) |
| Resource limits set | Are CPU/memory requests and limits defined? | ResourceQuota + LimitRange per namespace |
| Database scaling plan | Is there a plan for database scaling (read replicas, sharding)? | Documented scaling triggers and procedures |
| Rate limiting | Is the API rate-limited to prevent abuse? | Nginx rate limiting or API gateway middleware |

#### Pillar 6: Operability

| Criterion | Question | Evidence |
|-----------|----------|----------|
| Runbooks exist | Are there runbooks for common operational procedures? | `docs/runbook.md` covering deploy, rollback, scale, incidents |
| On-call trained | Has the on-call team been trained on the service? | Training session completed, shadow on-call done |
| Deployment automated | Is deployment fully automated (GitOps)? | ArgoCD Application with auto-sync (Module 10) |
| Rollback procedure | Can the service be rolled back in under 5 minutes? | Argo Rollout abort + ArgoCD revision rollback |
| Feature flags | Can new features be toggled without redeployment? | Feature flag integration (environment variables or flag service) |

#### Pillar 7: Data Management

| Criterion | Question | Evidence |
|-----------|----------|----------|
| Backup/restore tested | Has database backup and restore been tested? | pg_dump schedule + verified restore procedure |
| Data retention policy | Is there a data retention and cleanup policy? | Automated cleanup jobs for stale data |
| Migration strategy | Are database migrations backward-compatible? | Rolling migration pattern (expand/contract) |
| PII handling | Is PII identified, encrypted, and access-logged? | PII inventory with encryption and audit trail |

#### Pillar 8: Compliance & Documentation

| Criterion | Question | Evidence |
|-----------|----------|----------|
| ADRs written | Are significant architectural decisions documented? | Architecture Decision Records in `docs/adr/` |
| API documented | Is the API documented with examples? | OpenAPI spec + generated docs |
| Change log maintained | Is there a change log or release notes process? | Conventional commits → auto-generated changelog |
| Incident response plan | Is there an incident response plan? | Incident playbook with severity classification |

### Failure Mode and Effects Analysis (FMEA)

FMEA is a structured technique for identifying what can go wrong, how bad it would be, and whether you'd detect it. Each failure mode gets three scores (1–10) that multiply into a Risk Priority Number (RPN):

```
RPN = Severity × Probability × Detection Difficulty
```

| Failure Mode | Component | Severity (1–10) | Probability (1–10) | Detection (1–10) | RPN | Mitigation |
|-------------|-----------|-----------------|--------------------|--------------------|-----|------------|
| Database connection pool exhausted | API Gateway | 9 | 4 | 3 | 108 | Connection pool metrics + alert at 80% |
| Redis cache unavailable | API Gateway | 5 | 3 | 2 | 30 | Fallback to direct DB query |
| Worker OOM killed | Worker Service | 7 | 5 | 2 | 70 | VPA + memory limit alerts |
| Canary deployment fails silently | Argo Rollout | 8 | 3 | 6 | 144 | Analysis templates with automated checks |
| Certificate expiration | Ingress | 10 | 2 | 4 | 80 | cert-manager auto-renewal + expiry alert |
| etcd data corruption | Control Plane | 10 | 1 | 7 | 70 | Automated etcd backups + restore testing |
| Image registry unreachable | Deployment | 8 | 2 | 3 | 48 | ImagePullPolicy: IfNotPresent + registry mirror |

> **Key insight:** The highest RPN items aren't necessarily the most severe — they're the ones most likely to happen *and* go undetected. Invest in detection (monitoring and alerting) to bring down the detection score, which reduces RPN without changing the underlying risk.

### Capacity Planning Methodology

Capacity planning answers: "How much infrastructure do we need, and when do we need more?" It combines current utilization data with growth projections to produce a provisioning plan.

```
┌────────────────────────────────────────────────────────────┐
│                  Capacity Planning Process                   │
│                                                             │
│  1. Measure Current State                                   │
│     ├── CPU/memory utilization per service                  │
│     ├── Request rate, latency, error rate                   │
│     └── Database connections, query rate, storage growth    │
│                                                             │
│  2. Model Growth                                            │
│     ├── Historical trend analysis (linear, exponential)     │
│     ├── Business projections (new features, traffic events) │
│     └── Seasonal patterns (daily, weekly, annual)           │
│                                                             │
│  3. Define Headroom Policy                                  │
│     ├── Normal: 30% headroom above peak utilization         │
│     ├── Critical services: 50% headroom                     │
│     └── Burst: 3× sustained traffic for 15 minutes          │
│                                                             │
│  4. Produce Provisioning Plan                               │
│     ├── When to add replicas (HPA thresholds)               │
│     ├── When to add nodes (Cluster Autoscaler triggers)     │
│     └── When to upsize databases (connection/storage limits) │
│                                                             │
│  5. Review Quarterly                                        │
│     └── Compare actual vs projected, adjust model           │
└────────────────────────────────────────────────────────────┘
```

### PRR at Scale — How Companies Do It

**Google** pioneered the PRR as part of the SRE engagement model. A service must pass the PRR before it earns SRE support. The review is owned by a "launch coordination engineer" who shepherds the service through a multi-week checklist. Google's PRR template has over 70 items across security, reliability, scalability, and operability.

**Netflix** uses the concept of "production readiness" as a cultural norm rather than a formal gate. Teams self-assess using a maturity model with levels (basic → intermediate → advanced) across pillars like observability, resilience, and operational excellence.

**Uber** and **Airbnb** use a tiered approach: Tier-1 services (user-facing, high traffic) get a formal PRR with SRE involvement; Tier-3 services (internal tools, low traffic) use a lightweight self-service checklist.

> **Production note:** The formality of your PRR should match the blast radius of the service. A payments service needs a rigorous multi-week review. An internal admin tool might just need a one-page checklist.

---

## Code Examples

### Production Readiness Checklist as Code

Encode the PRR as a machine-readable YAML file that can be version-controlled and tracked over time:

```yaml
# docs/production-readiness.yaml — DeployForge PRR
apiVersion: prr/v1
kind: ProductionReadinessReview
metadata:
  service: deployforge
  version: "1.0.0"
  reviewDate: "2025-01-15"
  reviewers:
    - name: "SRE Lead"
      role: reviewer
    - name: "Security Engineer"
      role: reviewer
    - name: "Service Owner"
      role: author

pillars:
  architecture:
    status: pass  # pass | fail | partial
    items:
      - name: Architecture documented
        status: pass
        evidence: "docs/architecture.md — updated 2025-01-10"
      - name: Dependencies mapped
        status: pass
        evidence: "docs/dependency-graph.md — all 5 external deps documented"
      - name: Single points of failure eliminated
        status: partial
        evidence: "PostgreSQL is single-instance; read replica planned for Q2"
        remediation: "Deploy PostgreSQL HA with Patroni"
        deadline: "2025-03-31"

  security:
    status: pass
    items:
      - name: Image scanning in CI
        status: pass
        evidence: "Trivy scan in .github/workflows/ci.yml — fails on CRITICAL"
      - name: Network policies applied
        status: pass
        evidence: "k8s/network-policies/ — deny-all default + explicit allow rules"
      - name: Secrets encrypted at rest
        status: pass
        evidence: "Kubernetes Secrets with etcd encryption config"

  reliability:
    status: pass
    items:
      - name: SLOs defined
        status: pass
        evidence: "monitoring/slo-spec.yaml — availability 99.9%, latency p99 < 500ms"
      - name: Error budget dashboard
        status: pass
        evidence: "monitoring/dashboards/slo-budget.json"
      - name: Chaos experiments run
        status: pass
        evidence: "Chaos results in docs/chaos-results.md — all experiments passed"
      - name: Rollback tested
        status: pass
        evidence: "Argo Rollout abort tested — recovery in 45 seconds"

  observability:
    status: pass
    items:
      - name: RED metrics instrumented
        status: pass
        evidence: "http_requests_total, http_request_duration_seconds, http_errors_total"
      - name: Dashboards deployed
        status: pass
        evidence: "monitoring/dashboards/ — 4 dashboards (overview, SLO, worker, infra)"
      - name: Alerts tied to SLOs
        status: pass
        evidence: "monitoring/alerts/slo-burn-rate.yaml — 3-tier burn rate"

  scalability:
    status: partial
    items:
      - name: HPA configured
        status: pass
        evidence: "k8s/scaling/hpa-api.yaml — targets http_requests_per_second"
      - name: Load tested
        status: partial
        evidence: "Tested at 1.5× expected load; need to retest at 2×"
        remediation: "Run k6 load test at 2× peak traffic"
        deadline: "2025-02-15"

summary:
  status: conditional-pass
  conditions:
    - "Complete 2× load test by 2025-02-15"
    - "Deploy PostgreSQL HA by 2025-03-31"
  nextReviewDate: "2025-04-01"
```

### FMEA Document

```yaml
# docs/fmea.yaml — DeployForge Failure Mode Analysis
apiVersion: fmea/v1
kind: FailureModeAnalysis
metadata:
  service: deployforge
  lastUpdated: "2025-01-15"

failureModes:
  - id: FM-001
    component: api-gateway
    mode: "Database connection pool exhausted"
    cause: "Slow queries or connection leak under high concurrency"
    effect: "All API requests fail with 503"
    severity: 9
    probability: 4
    detection: 3
    rpn: 108
    mitigation:
      - "Connection pool size metric with alert at 80% utilization"
      - "PgBouncer connection pooler to limit backend connections"
      - "Query timeout of 5s to prevent connection hoarding"
    owner: "backend-team"

  - id: FM-002
    component: argo-rollout
    mode: "Canary deployment fails silently"
    cause: "AnalysisTemplate misconfigured — always returns success"
    effect: "Bad code promoted to 100% traffic"
    severity: 8
    probability: 3
    detection: 6
    rpn: 144
    mitigation:
      - "AnalysisTemplate integration test in CI"
      - "Manual approval gate after canary phase"
      - "Error rate alert independent of rollout analysis"
    owner: "platform-team"

  - id: FM-003
    component: worker-service
    mode: "Worker OOM killed during large job"
    cause: "Memory-intensive job exceeds container limit"
    effect: "Job lost and retried, latency spike for queue consumers"
    severity: 7
    probability: 5
    detection: 2
    rpn: 70
    mitigation:
      - "VPA in Auto mode to right-size memory"
      - "Job-level memory profiling metric"
      - "Dead letter queue for jobs that fail 3× due to OOM"
    owner: "backend-team"
```

### Architecture Decision Record (ADR) Template

```markdown
# docs/adr/001-use-argocd-for-gitops.md

# ADR-001: Use ArgoCD for GitOps Deployment

## Status
Accepted

## Context
DeployForge needs an automated deployment mechanism that:
- Uses Git as the single source of truth for cluster state
- Supports canary and blue-green deployment strategies
- Integrates with Prometheus for deployment health checks
- Provides a UI for deployment visibility

## Decision
We will use ArgoCD with Argo Rollouts for GitOps-based progressive delivery.

## Consequences
### Positive
- Git history provides full audit trail of every deployment
- ArgoCD auto-sync detects and corrects drift automatically
- Argo Rollouts AnalysisTemplates automate canary verification

### Negative
- Additional operational complexity (ArgoCD is itself a stateful service)
- Team needs training on ArgoCD Application CRDs
- Secret management requires sealed-secrets or external-secrets operator

### Risks
- ArgoCD becomes a single point of failure for all deployments
- Mitigation: HA ArgoCD deployment with Redis sentinel backend
```

---

## Try It Yourself

### Challenge 1: Build a PRR Checklist for DeployForge

Using the eight-pillar framework above, create a production readiness checklist for DeployForge. For each pillar:
1. List at least 3 criteria
2. Mark each as pass/partial/fail based on what you've built in Modules 01–12
3. For any partial/fail items, write a remediation plan with a deadline

<details>
<summary>Show solution</summary>

```yaml
# docs/production-readiness.yaml
apiVersion: prr/v1
kind: ProductionReadinessReview
metadata:
  service: deployforge
  version: "1.0.0"
  reviewDate: "2025-01-15"
  reviewers:
    - name: "Your Name"
      role: author

pillars:
  architecture:
    status: pass
    items:
      - name: Architecture documented
        status: pass
        evidence: "README.md + docs/architecture.md with component diagram"
      - name: Dependencies mapped
        status: pass
        evidence: "5 components documented — API, Worker, PostgreSQL, Redis, Nginx"
      - name: No single points of failure
        status: partial
        evidence: "PostgreSQL is single-instance in dev"
        remediation: "Add read replica for production"

  security:
    status: pass
    items:
      - name: Image scanning
        status: pass
        evidence: "Trivy in CI pipeline (Module 03)"
      - name: Network policies
        status: pass
        evidence: "Deny-all default + service-to-service allow (Module 06)"
      - name: Non-root containers
        status: pass
        evidence: "All Dockerfiles use non-root USER (Module 03)"

  reliability:
    status: pass
    items:
      - name: SLOs defined
        status: pass
        evidence: "99.9% availability, p99 < 500ms (Module 09)"
      - name: Chaos tested
        status: pass
        evidence: "Pod kill + network partition experiments (Module 09)"
      - name: Rollback tested
        status: pass
        evidence: "Argo Rollout abort recovers in < 60s (Module 10)"

  observability:
    status: pass
    items:
      - name: RED metrics
        status: pass
        evidence: "Prometheus instrumentation (Module 08)"
      - name: Dashboards
        status: pass
        evidence: "Grafana service health + SLO dashboards (Module 08–09)"
      - name: Alerting
        status: pass
        evidence: "Multi-burn-rate SLO alerts (Module 09)"

  scalability:
    status: partial
    items:
      - name: HPA configured
        status: pass
        evidence: "HPA v2 with custom metrics (Module 12)"
      - name: Load tested at 2×
        status: fail
        evidence: "Not yet performed"
        remediation: "Run k6 load test at 2× expected peak"
      - name: Rate limiting
        status: partial
        evidence: "Nginx rate limiting configured but not tuned"
        remediation: "Tune rate limits based on load test results"

  operability:
    status: partial
    items:
      - name: Runbooks exist
        status: fail
        evidence: "Not yet written"
        remediation: "Write runbook in Exercise 4 of this module"
      - name: Deployment automated
        status: pass
        evidence: "ArgoCD GitOps (Module 10)"
      - name: On-call trained
        status: fail
        evidence: "No on-call rotation defined"
        remediation: "Complete Exercise 3"

summary:
  status: conditional-pass
  conditions:
    - "Complete load testing at 2× peak"
    - "Write operational runbooks"
    - "Establish on-call rotation"
```

Verify by reviewing:
```bash
cat docs/production-readiness.yaml | grep "status: fail" | wc -l
# → 2  (load test + on-call — these are your action items)

cat docs/production-readiness.yaml | grep "status: partial" | wc -l
# → 1  (rate limiting — needs tuning)
```

</details>

### Challenge 2: Perform FMEA for a New Component

DeployForge is adding a **Notification Service** that sends Slack/email alerts when deployments complete. Perform FMEA:
1. Identify at least 4 failure modes
2. Score each on severity, probability, and detection (1–10)
3. Calculate RPN and rank by priority
4. Propose mitigations for the top-2 RPN items

<details>
<summary>Show solution</summary>

| # | Failure Mode | Severity | Probability | Detection | RPN | Mitigation |
|---|-------------|----------|-------------|-----------|-----|------------|
| 1 | Slack API rate limited | 6 | 5 | 3 | 90 | Exponential backoff + notification queue with BullMQ |
| 2 | Email provider outage | 7 | 3 | 5 | 105 | Multi-provider failover (SendGrid + SES) + delivery status tracking |
| 3 | Duplicate notifications sent | 4 | 4 | 7 | 112 | Idempotency key per deployment event + dedup window |
| 4 | Notification delay > 5min | 5 | 4 | 6 | 120 | Queue depth metric + alert on p99 delivery latency |

**Top 2 by RPN:**

1. **Notification delay > 5min (RPN 120):** Add a `notification_delivery_seconds` histogram metric. Alert when p99 exceeds 60 seconds. Set BullMQ concurrency to process up to 10 notifications in parallel. Add a dedicated notification worker scaled by queue depth.

2. **Duplicate notifications sent (RPN 112):** Generate an idempotency key from `deployment_id + event_type + timestamp_minute`. Check a Redis set before sending. Set TTL to 1 hour. Log and metric on dedup hits to track frequency.

</details>

---

## Capstone Connection

**DeployForge** uses the production readiness review as the gate between "it works in dev" and "it's ready for production":

- **PRR checklist** (`docs/production-readiness.yaml`) — A version-controlled, machine-readable checklist that tracks readiness across all eight pillars. The checklist is reviewed before every major release and updated as the system evolves. Items marked `partial` or `fail` become tickets in the backlog.
- **FMEA document** (`docs/fmea.yaml`) — A living failure mode analysis that ranks every known risk by RPN. The top-10 items by RPN drive reliability investment: if the highest RPN is "canary fails silently," the next sprint prioritizes AnalysisTemplate testing. FMEA is updated after every incident postmortem.
- **Architecture Decision Records** (`docs/adr/`) — Every significant architectural choice (ArgoCD over Flux, BullMQ over RabbitMQ, Kustomize overlays over Helm values) is documented as an ADR. New team members read the ADR log to understand *why* the system looks the way it does.
- **Dependency map** — The dependency graph from the architecture review feeds directly into the chaos experiments from Module 09. If the dependency map shows that the API Gateway depends on Redis for rate limiting, then a chaos experiment should test what happens when Redis is unavailable.

In [02-end-to-end-deployment.md](02-end-to-end-deployment.md), you'll deploy DeployForge through the entire pipeline — and the PRR checklist will tell you whether the result is production-ready.
