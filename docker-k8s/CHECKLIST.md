# Docker, Kubernetes & SRE — Knowledge Checklist

Use this file to periodically self-assess. Review it after each module and update your ratings.

**Legend:** `[ ]` Not yet · `[~]` In progress · `[x]` Confident

---

## Module 1 — Container Fundamentals

### 1.1 Linux Namespaces & Cgroups
- [ ] Can explain all 8 namespace types (pid, net, mnt, uts, ipc, user, cgroup, time)
- [ ] Can describe the isolation each namespace provides to a containerised process
- [ ] Can create an isolated process using `unshare` with selected namespaces
- [ ] Can set cgroup v2 memory and CPU limits for a process and verify enforcement
- [ ] Can explain the difference between cgroup v1 hierarchy and cgroup v2 unified tree

### 1.2 OCI Images & Runtimes
- [ ] Can explain the OCI image spec: manifest, config, and layer tarballs
- [ ] Can inspect image layers with `docker inspect`, `crane`, or `skopeo`
- [ ] Can differentiate the roles of containerd (high-level runtime) vs runc (low-level runtime)
- [ ] Can describe how image layers are stacked using overlay filesystems
- [ ] Can pull and examine an image without Docker using `skopeo` and `umoci`

### 1.3 Containers vs VMs
- [ ] Can explain kernel sharing: containers share the host kernel, VMs each run their own
- [ ] Can compare isolation boundaries: hypervisor-based (VM) vs namespace-based (container)
- [ ] Can describe how Kata Containers provides VM-level isolation for container workloads
- [ ] Can explain gVisor's user-space kernel approach and its security/performance trade-offs
- [ ] Can articulate when to choose VMs over containers (e.g., untrusted multi-tenant workloads)

---

## Module 2 — Docker Mastery

### 2.1 Dockerfile Best Practices
- [ ] Can optimise layer caching by ordering instructions from least to most frequently changing
- [ ] Can choose minimal base images (distroless, Alpine, scratch) and justify the trade-offs
- [ ] Can configure a `HEALTHCHECK` instruction with interval, timeout, and retries
- [ ] Can use `.dockerignore` to reduce build context size
- [ ] Can explain why running as a non-root `USER` is a security requirement

### 2.2 Multi-Stage Builds
- [ ] Can implement a multi-stage Dockerfile for a TypeScript build pipeline
- [ ] Can achieve final images under 100 MB by copying only production artefacts
- [ ] Can use BuildKit features: `--mount=type=cache`, `--mount=type=secret`, parallel stages
- [ ] Can target a specific stage with `docker build --target` for development vs production

### 2.3 Networking & Volumes
- [ ] Can configure bridge, host, and overlay Docker networks and explain each use case
- [ ] Can create and manage named volumes vs bind mounts and explain persistence semantics
- [ ] Can debug container DNS resolution using `docker exec` with `dig` or `nslookup`
- [ ] Can explain how Docker's embedded DNS server resolves container names on user-defined networks

### 2.4 Docker Compose
- [ ] Can write production-quality Compose files with resource limits and restart policies
- [ ] Can configure `healthcheck` in Compose and use `depends_on` with `condition: service_healthy`
- [ ] Can use Compose profiles to selectively start services (e.g., `--profile debug`)
- [ ] Can override configuration with multiple Compose files (`docker compose -f base.yml -f override.yml`)
- [ ] Can manage multi-service logs with `docker compose logs --follow`

---

## Module 3 — Container Security

### 3.1 Image Scanning
- [ ] Can run Trivy to scan images for OS and application-level CVEs
- [ ] Can lint Dockerfiles with Hadolint and fix common warnings (DL3008, DL3018, etc.)
- [ ] Can pin images by digest (`image@sha256:...`) and explain why tags are mutable
- [ ] Can integrate image scanning into a CI pipeline as a blocking gate

### 3.2 Runtime Security
- [ ] Can drop all capabilities and add back only what is needed (`--cap-drop ALL --cap-add NET_BIND_SERVICE`)
- [ ] Can configure a seccomp profile to restrict dangerous syscalls
- [ ] Can enable read-only root filesystem (`--read-only`) and use `tmpfs` for writable paths
- [ ] Can run containers with a non-root user and explain user namespace remapping
- [ ] Can use AppArmor or SELinux profiles to further restrict container behaviour

### 3.3 Supply Chain
- [ ] Can sign container images with `cosign` and store signatures in an OCI registry
- [ ] Can generate a Software Bill of Materials (SBOM) using `syft` or `docker sbom`
- [ ] Can verify image signatures before deployment in a Kubernetes admission controller
- [ ] Can explain the SLSA framework levels and how they relate to build provenance

---

## Module 4 — Kubernetes Architecture

### 4.1 Control Plane
- [ ] Can trace the full API server request flow: authentication → authorisation → admission → etcd
- [ ] Can describe etcd's role as the sole source of truth and its Raft consensus algorithm
- [ ] Can explain the scheduler's algorithm: filtering (predicates) → scoring (priorities) → binding
- [ ] Can describe controller manager's reconciliation loop pattern
- [ ] Can explain what happens when the API server is temporarily unavailable

### 4.2 Node Architecture
- [ ] Can explain the kubelet's probe lifecycle: startup → liveness → readiness
- [ ] Can compare kube-proxy modes: iptables (default) vs IPVS (hash-based, better at scale)
- [ ] Can describe the Container Runtime Interface (CRI) and how kubelet communicates with containerd
- [ ] Can explain how the kubelet watches the API server and reconciles desired pod state
- [ ] Can describe the role of CoreDNS in cluster DNS resolution

### 4.3 kubectl & API
- [ ] Can use `kubectl debug` to attach ephemeral containers for live troubleshooting
- [ ] Can query the API server with verbose logging (`kubectl -v=8`) to inspect HTTP requests
- [ ] Can use server-side apply (`kubectl apply --server-side`) and explain conflict detection
- [ ] Can use `kubectl diff` to preview changes before applying
- [ ] Can create and use custom kubeconfig contexts for multi-cluster management

---

## Module 5 — Workloads & Scheduling

### 5.1 Pods & Deployments
- [ ] Can configure rolling update strategy with `maxSurge` and `maxUnavailable`
- [ ] Can manage deployment revisions: rollout history, rollback to specific revision
- [ ] Can set QoS classes (Guaranteed, Burstable, BestEffort) by configuring resource requests/limits
- [ ] Can explain the difference between resource requests (scheduling) and limits (enforcement)

### 5.2 StatefulSets & DaemonSets
- [ ] Can deploy a stateful application (e.g., database) with StatefulSet and stable network identities
- [ ] Can configure ordered scaling and rolling updates with `podManagementPolicy`
- [ ] Can use Jobs for batch processing and CronJobs for scheduled tasks
- [ ] Can configure DaemonSets for node-level agents (log collectors, monitoring)
- [ ] Can explain `activeDeadlineSeconds`, `backoffLimit`, and `ttlSecondsAfterFinished` for Jobs

### 5.3 Scheduling
- [ ] Can configure `nodeAffinity` with required and preferred rules
- [ ] Can set taints on nodes and matching tolerations on pods
- [ ] Can use `topologySpreadConstraints` to distribute pods evenly across zones
- [ ] Can use `podAffinity` and `podAntiAffinity` for co-location and spreading
- [ ] Can explain the difference between soft (preferred) and hard (required) scheduling constraints

### 5.4 Pod Lifecycle
- [ ] Can implement all three probe types: startup, liveness, and readiness (HTTP, TCP, exec)
- [ ] Can configure graceful shutdown with `preStop` hook and `terminationGracePeriodSeconds`
- [ ] Can set `PodDisruptionBudgets` to protect availability during voluntary disruptions
- [ ] Can explain the pod termination sequence: preStop → SIGTERM → grace period → SIGKILL

---

## Module 6 — Networking & Services

### 6.1 K8s Networking Model
- [ ] Can explain the flat network model: every pod gets a unique IP, all pods can reach each other
- [ ] Can compare CNI plugins (Calico, Cilium, Flannel) and their trade-offs
- [ ] Can trace a packet from pod A to pod B across nodes through the CNI overlay/underlay
- [ ] Can explain how Cilium uses eBPF to bypass iptables for improved performance

### 6.2 Services & Discovery
- [ ] Can configure all service types: ClusterIP, NodePort, LoadBalancer, ExternalName
- [ ] Can debug DNS resolution with `nslookup <service>.<namespace>.svc.cluster.local` from a pod
- [ ] Can explain EndpointSlices and why they replaced Endpoints for scalability
- [ ] Can configure headless services (`clusterIP: None`) for StatefulSet DNS

### 6.3 Ingress
- [ ] Can set up an Nginx Ingress Controller and create Ingress resources with routing rules
- [ ] Can configure TLS termination with cert-manager and Let's Encrypt (ClusterIssuer)
- [ ] Can implement path-based and host-based routing in a single Ingress resource
- [ ] Can configure Ingress annotations for rate limiting, CORS, and custom timeouts
- [ ] Can explain the Gateway API as the successor to the Ingress resource

### 6.4 NetworkPolicies & Service Mesh
- [ ] Can write a default-deny ingress/egress NetworkPolicy and selectively allow traffic
- [ ] Can explain Istio's data plane (Envoy sidecars) and control plane (istiod)
- [ ] Can describe how mTLS between services is implemented transparently via the mesh
- [ ] Can articulate when a service mesh is overkill vs when it provides clear value

---

## Module 7 — Storage & Configuration

### 7.1 Persistent Storage
- [ ] Can configure PersistentVolumes (PV) and PersistentVolumeClaims (PVC) with correct access modes
- [ ] Can use StorageClasses for dynamic provisioning and set reclaim policies
- [ ] Can implement backup strategies for persistent volumes (Velero, volume snapshots)
- [ ] Can explain the difference between ReadWriteOnce, ReadOnlyMany, and ReadWriteMany access modes

### 7.2 ConfigMaps & Secrets
- [ ] Can externalise application config using ConfigMaps (env vars, volume mounts, CLI args)
- [ ] Can use External Secrets Operator to sync secrets from Vault/AWS Secrets Manager
- [ ] Can manage sealed-secrets for GitOps-safe secret storage in version control
- [ ] Can explain why Kubernetes Secrets are base64-encoded (not encrypted) by default
- [ ] Can enable encryption at rest for Secrets using an `EncryptionConfiguration`

### 7.3 Helm Charts
- [ ] Can create a Helm chart with `values.yaml`, templates, helpers, and `NOTES.txt`
- [ ] Can manage releases: install, upgrade, rollback, and view history
- [ ] Can use chart dependencies and subcharts for complex deployments
- [ ] Can write Helm tests to validate a release post-install
- [ ] Can use `helm template` to render manifests locally for review

### 7.4 Kustomize
- [ ] Can write base and overlay `kustomization.yaml` files for multiple environments
- [ ] Can use generators for ConfigMaps and Secrets with content-hash suffixes
- [ ] Can apply patches (strategic merge, JSON 6902) for environment-specific overrides
- [ ] Can compare Kustomize vs Helm and articulate when to prefer each

---

## Module 8 — Observability

### 8.1 Metrics & Prometheus
- [ ] Can write PromQL queries: `rate()`, `histogram_quantile()`, aggregation with `by`/`without`
- [ ] Can explain RED (Rate, Errors, Duration) and USE (Utilisation, Saturation, Errors) methods
- [ ] Can configure recording rules to pre-compute expensive queries
- [ ] Can set up Prometheus ServiceMonitors to auto-discover scrape targets in Kubernetes
- [ ] Can explain the pull-based scraping model and when to use Pushgateway

### 8.2 Distributed Tracing
- [ ] Can instrument a service with OpenTelemetry SDK to emit traces and spans
- [ ] Can configure head-based and tail-based sampling strategies and their trade-offs
- [ ] Can correlate traces with logs using trace IDs and span IDs
- [ ] Can use Jaeger or Tempo to visualise trace waterfalls and identify latency bottlenecks

### 8.3 Logging
- [ ] Can implement structured JSON logging with consistent fields (timestamp, level, trace_id)
- [ ] Can configure Fluent Bit as a DaemonSet for cluster-wide log collection
- [ ] Can query logs with LogQL in Grafana Loki (label filters, line filters, parsers)
- [ ] Can explain the difference between node-level logging and sidecar-based logging

### 8.4 Dashboards & Alerting
- [ ] Can build Grafana dashboards with RED metrics panels and variable dropdowns
- [ ] Can configure alerting rules in Prometheus with `for` duration and severity labels
- [ ] Can prevent alert fatigue: group alerts, set inhibition rules, tune thresholds with error budgets
- [ ] Can route alerts to the right channel (PagerDuty, Slack, OpsGenie) via Alertmanager

---

## Module 9 — Reliability Engineering

### 9.1 SLOs & Error Budgets
- [ ] Can define SLIs (the metric), SLOs (the target), and SLAs (the contract)
- [ ] Can calculate error budgets: `budget = 1 − SLO target` per period
- [ ] Can implement burn-rate alerting (fast-burn for pages, slow-burn for tickets)
- [ ] Can explain multi-window, multi-burn-rate alerting as recommended by the Google SRE book
- [ ] Can use error budget as a decision lever: freeze deploys when budget is exhausted

### 9.2 Chaos Engineering
- [ ] Can design a chaos experiment with a hypothesis, blast radius, and stop condition
- [ ] Can use Chaos Mesh to inject pod failures, network delays, and I/O faults in Kubernetes
- [ ] Can plan and run a GameDay exercise with a cross-functional team
- [ ] Can document experiment results and convert findings into reliability improvements

### 9.3 Incident Response
- [ ] Can lead an incident through detection → triage → mitigation → resolution → review
- [ ] Can write blameless postmortems with timeline, root cause, and action items
- [ ] Can identify toil (repetitive, automatable operational work) and propose elimination strategies
- [ ] Can define incident severity levels and appropriate escalation procedures

---

## Module 10 — CI/CD & GitOps

### 10.1 CI Pipelines
- [ ] Can write GitHub Actions workflows with build, test, scan, and push stages
- [ ] Can implement dependency and Docker layer caching to speed up CI runs
- [ ] Can automate image scanning (Trivy) and SAST as pipeline gates
- [ ] Can configure matrix builds for multi-platform image creation
- [ ] Can manage secrets and environment variables securely in CI

### 10.2 GitOps
- [ ] Can deploy ArgoCD and configure it to sync from a Git repository
- [ ] Can implement the app-of-apps pattern for managing multiple applications
- [ ] Can configure drift detection and auto-sync policies with health checks
- [ ] Can explain the GitOps principles: declarative, versioned, automated, auditable
- [ ] Can implement a promotion workflow across environments (dev → staging → production)

### 10.3 Progressive Delivery
- [ ] Can configure a canary deployment with Argo Rollouts and traffic splitting
- [ ] Can set up automated analysis with Prometheus metrics to promote or rollback canaries
- [ ] Can use feature flags to decouple deployment from release
- [ ] Can implement blue-green deployments and explain the trade-offs vs canary

---

## Module 11 — Infrastructure as Code

### 11.1 Terraform Fundamentals
- [ ] Can write HCL configurations with resources, variables, outputs, and locals
- [ ] Can manage provider versions and lock files (`terraform.lock.hcl`)
- [ ] Can use `count` and `for_each` for creating multiple resources from a collection
- [ ] Can use `terraform plan` to preview and `terraform apply` to execute changes
- [ ] Can explain the Terraform lifecycle: init → plan → apply → destroy

### 11.2 Modules & State
- [ ] Can design reusable Terraform modules with clear input/output interfaces
- [ ] Can configure remote state backends (S3 + DynamoDB, Terraform Cloud)
- [ ] Can manage workspaces for environment isolation (dev, staging, production)
- [ ] Can use `terraform import` to bring existing infrastructure under management
- [ ] Can handle state operations: `state mv`, `state rm`, `state pull/push`

### 11.3 IaC for K8s
- [ ] Can provision managed Kubernetes clusters (EKS, GKE, AKS) with Terraform
- [ ] Can compare Terraform (imperative provisioning) vs Crossplane (declarative, K8s-native)
- [ ] Can test IaC with `terraform validate`, `tflint`, and integration tests (Terratest)
- [ ] Can manage Kubernetes resources with the Terraform Kubernetes provider when appropriate

---

## Module 12 — Scaling & Cost

### 12.1 HPA & VPA
- [ ] Can configure Horizontal Pod Autoscaler (HPA) with CPU, memory, and custom metrics
- [ ] Can set up Vertical Pod Autoscaler (VPA) in recommendation mode and auto mode
- [ ] Can use KEDA for event-driven autoscaling (queue depth, cron, external metrics)
- [ ] Can explain the interaction between HPA and VPA and when to use each

### 12.2 Cluster Autoscaling
- [ ] Can configure the Kubernetes Cluster Autoscaler with node group limits
- [ ] Can use Karpenter for just-in-time, bin-packing-aware node provisioning
- [ ] Can manage spot/preemptible instances with graceful termination handling
- [ ] Can explain scale-down policies and how to prevent thrashing

### 12.3 Resource Management & FinOps
- [ ] Can set ResourceQuotas and LimitRanges per namespace
- [ ] Can use Kubecost or OpenCost to track per-team and per-service cloud spend
- [ ] Can implement right-sizing recommendations based on actual resource utilisation
- [ ] Can explain the FinOps cycle: inform → optimise → operate
- [ ] Can identify waste: idle resources, over-provisioned pods, orphaned volumes

---

## Module 13 — Capstone Integration

### 13.1 Production Readiness
- [ ] Can complete a production readiness checklist covering security, observability, and reliability
- [ ] Can perform a Failure Mode and Effects Analysis (FMEA) for each critical component
- [ ] Can document the full system architecture with data flow and failure domains
- [ ] Can present a production readiness review to a team and justify design decisions

### 13.2 End-to-End Deployment
- [ ] Can deploy a full pipeline from Git commit to production via GitOps
- [ ] Can demonstrate the CI/CD flow: build → test → scan → push → sync → verify
- [ ] Can perform a zero-downtime deployment with automated rollback on failure
- [ ] Can verify health with smoke tests and synthetic monitoring post-deployment

### 13.3 Operational Excellence
- [ ] Can perform a Kubernetes version upgrade with zero downtime (control plane + nodes)
- [ ] Can backup and restore etcd snapshots and verify cluster recovery
- [ ] Can write operational runbooks for common failure scenarios
- [ ] Can automate routine operations with Kubernetes operators or CronJobs
- [ ] Can conduct a capacity planning exercise based on growth projections

---

## Capstone — DeployForge

- [ ] Can deploy the full DeployForge stack from scratch on a fresh cluster
- [ ] Can explain the DeployForge architecture: all services, dependencies, and data flows
- [ ] Can break the system (kill pods, inject faults) and restore it using runbooks
- [ ] Can demonstrate the CI/CD pipeline from code change to production deployment
- [ ] Can present the architecture to a team with clear trade-off explanations
- [ ] Can identify and implement at least three reliability improvements to the baseline

---

## Interview Readiness

- [ ] Can explain Docker image layering, caching, and multi-stage builds under interview pressure
- [ ] Can whiteboard the Kubernetes architecture (control plane + node components) from memory
- [ ] Can troubleshoot a pod stuck in CrashLoopBackOff, ImagePullBackOff, or Pending
- [ ] Can design a zero-downtime deployment strategy and explain rollback mechanisms
- [ ] Can discuss SLOs, error budgets, and incident response with concrete examples
- [ ] Can explain the difference between horizontal and vertical scaling with K8s primitives
- [ ] Can describe a GitOps workflow and its advantages over imperative CI/CD
- [ ] Can discuss container security best practices: least privilege, scanning, signing, RBAC

---

## Review Log

| Date | Modules Reviewed | Gaps Identified |
|------|-----------------|-----------------|
|      |                 |                 |
|      |                 |                 |
|      |                 |                 |
