# Module 08 — Observability

## Overview

You can't manage what you can't see. As DeployForge grows from a handful of pods into a multi-service platform, the first question after "Is it running?" becomes "How well is it running?" — and when something breaks, "Why did it break?" Observability is the discipline of answering those questions with data instead of guesswork. It's the difference between reading a dashboard that says "p99 latency spiked 3× at 14:32 after the redis connection pool saturated" and getting a 3 AM page that says "something is slow."

This module builds a complete observability stack across the three pillars: **metrics** (Prometheus), **traces** (OpenTelemetry + Jaeger), and **logs** (structured JSON + Loki). You'll learn how to instrument your TypeScript services, write PromQL queries that actually catch problems before users notice, build Grafana dashboards that tell a story, and configure alerting that wakes you up for the right reasons. Along the way, you'll internalize the RED method (Rate, Errors, Duration) for request-driven services and the USE method (Utilization, Saturation, Errors) for infrastructure resources.

By the end of this module, DeployForge will have Prometheus scraping metrics from every service, OpenTelemetry traces flowing through the API Gateway and Worker Service, structured JSON logs with correlation IDs linking traces to logs, Grafana dashboards for both service health and infrastructure, and alerting rules that fire before customers notice.

---

## Learning Objectives

After completing this module, you should be able to:

- [ ] Describe the three pillars of observability and how they complement each other
- [ ] Deploy Prometheus to a Kubernetes cluster and configure ServiceMonitor-based scraping
- [ ] Explain the four Prometheus metric types and when to use each
- [ ] Write PromQL queries using rate(), histogram_quantile(), and aggregation operators
- [ ] Instrument a Node.js/TypeScript application with OpenTelemetry (metrics, traces, logs)
- [ ] Explain the trace/span/context model and W3C TraceContext propagation
- [ ] Choose appropriate sampling strategies for different traffic volumes
- [ ] Implement structured JSON logging with correlation IDs across services
- [ ] Deploy Fluent Bit as a DaemonSet for log collection and forwarding
- [ ] Build Grafana dashboards with variables, annotations, and meaningful panels
- [ ] Configure Prometheus alerting rules and Alertmanager notification routing
- [ ] Apply the RED method to monitor request-driven services
- [ ] Apply the USE method to monitor infrastructure resources
- [ ] Correlate metrics, traces, and logs for end-to-end incident investigation

---

## Topics

| # | File | Topic | Est. Time |
|---|------|-------|-----------|
| 1 | [01-metrics-and-prometheus.md](01-metrics-and-prometheus.md) | Metrics & Prometheus | 60 min |
| 2 | [02-distributed-tracing.md](02-distributed-tracing.md) | Distributed Tracing with OpenTelemetry | 50 min |
| 3 | [03-logging-and-aggregation.md](03-logging-and-aggregation.md) | Structured Logging & Log Aggregation | 45 min |
| 4 | [04-dashboards-and-alerting.md](04-dashboards-and-alerting.md) | Grafana Dashboards & Alerting | 45 min |
| 5 | [exercises/README.md](exercises/README.md) | Hands-on Exercises | 90 min |

**Total estimated time: 5–6 hours**

---

## Prerequisites

- [Module 07 — Storage & Configuration](../07-storage-and-configuration/) (PVCs, ConfigMaps, Secrets, Helm, Kustomize)
- A running `kind` cluster with the `deployforge` namespace and workloads from Module 07
- kubectl installed (`brew install kubectl`)
- Helm 3.x installed (`brew install helm`)
- Node.js 20 LTS installed for OpenTelemetry instrumentation examples
- Basic familiarity with HTTP APIs and JSON

---

## Capstone Milestone

> **Goal:** DeployForge fully instrumented with Prometheus metrics, OpenTelemetry distributed tracing, structured JSON logs, Grafana dashboards, and alerting rules — a production-grade observability stack.

By the end of this module you'll have:

| Artifact | Description |
|----------|-------------|
| Prometheus | Deployed via Helm with ServiceMonitors scraping all DeployForge services |
| Application Metrics | Custom counters, histograms, and gauges exposed from API Gateway and Worker |
| OpenTelemetry SDK | Traces and spans flowing from API Gateway → Worker → PostgreSQL/Redis |
| Jaeger | Trace backend deployed in the cluster for trace visualization |
| Structured Logging | JSON logs with trace_id, span_id, request_id correlation fields |
| Fluent Bit | DaemonSet collecting logs from all pods and forwarding to Loki |
| Grafana Dashboards | Service health (RED), infrastructure (USE), and SLO dashboards |
| Alerting Rules | PrometheusRules for error rate, latency, pod restarts, and disk pressure |

```
┌─────────────────────────────────────────────────────────────────────┐
│              kind Cluster — DeployForge Observability                │
│                                                                     │
│  ┌──── Application Layer ─────────────────────────────────────────┐ │
│  │                                                                 │ │
│  │  ┌─────────────┐   ┌─────────────┐   ┌──────────┐             │ │
│  │  │ API Gateway  │──▶│ Worker Svc  │   │ Nginx    │             │ │
│  │  │ (Express/TS) │   │ (BullMQ)    │   │ Frontend │             │ │
│  │  │              │   │              │   │          │             │ │
│  │  │ OTel SDK     │   │ OTel SDK     │   │          │             │ │
│  │  │ prom-client  │   │ prom-client  │   │          │             │ │
│  │  │ JSON logs    │   │ JSON logs    │   │          │             │ │
│  │  └──────┬───────┘   └──────┬───────┘   └──────────┘             │ │
│  │         │ metrics,traces,logs│                                   │ │
│  └─────────┼───────────────────┼───────────────────────────────────┘ │
│            │                   │                                     │
│  ┌─────── ▼ ── Metrics ───────▼──────────────────────────────────┐  │
│  │  ┌──────────────┐         ┌─────────────────┐                  │  │
│  │  │ Prometheus    │◀── scrape ──│ ServiceMonitors │               │  │
│  │  │ (TSDB)        │         └─────────────────┘                  │  │
│  │  │ AlertManager  │                                              │  │
│  │  └──────┬───────┘                                              │  │
│  └─────────┼──────────────────────────────────────────────────────┘  │
│            │                                                         │
│  ┌─────── ▼ ── Traces ───────────────────────────────────────────┐  │
│  │  ┌────────────────┐     ┌──────────────┐                       │  │
│  │  │ OTel Collector  │────▶│ Jaeger        │                      │  │
│  │  │ (receive/export)│     │ (query + UI)  │                      │  │
│  │  └────────────────┘     └──────────────┘                       │  │
│  └────────────────────────────────────────────────────────────────┘  │
│                                                                     │
│  ┌──── Logs ──────────────────────────────────────────────────────┐ │
│  │  ┌────────────────┐     ┌──────────────┐                       │ │
│  │  │ Fluent Bit      │────▶│ Loki          │                      │ │
│  │  │ (DaemonSet)     │     │ (log store)   │                      │ │
│  │  └────────────────┘     └──────────────┘                       │ │
│  └────────────────────────────────────────────────────────────────┘ │
│                                                                     │
│  ┌──── Visualization ─────────────────────────────────────────────┐ │
│  │  ┌──────────────────────────────────────┐                       │ │
│  │  │ Grafana                               │                      │ │
│  │  │  ├── Prometheus datasource (metrics)  │                      │ │
│  │  │  ├── Jaeger datasource (traces)       │                      │ │
│  │  │  ├── Loki datasource (logs)           │                      │ │
│  │  │  ├── Service Health Dashboard (RED)   │                      │ │
│  │  │  ├── Infrastructure Dashboard (USE)   │                      │ │
│  │  │  └── Alert Rules + Notification       │                      │ │
│  │  └──────────────────────────────────────┘                       │ │
│  └────────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────┘
```

---

## How to Work Through This Module

1. Read each concept file in order (01 → 02 → 03 → 04).
2. Deploy each observability component to your kind cluster and verify it's collecting data.
3. Use `kubectl port-forward` to access Prometheus, Grafana, and Jaeger UIs.
4. Complete the exercises in `exercises/README.md`.
5. Check off the learning objectives above as you master each one.
6. Move to [Module 09 — Reliability Engineering](../09-reliability-engineering/) when ready.
