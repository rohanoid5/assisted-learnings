# Module 1 — Foundations of System Design

## Overview

Before drawing a single box on a whiteboard, you need a mental framework: how to define what you're building, how to reason about tradeoffs, and how to talk confidently about scalability, consistency, and availability. This module builds that foundation.

These concepts appear in every system design conversation — whether you're debugging a production incident, planning a new service, or in a system design interview. Get them clear once, and everything else clicks faster.

---

## Learning Objectives

By the end of this module you will be able to:

- [ ] Define system design and explain its scope (functional vs. non-functional requirements)
- [ ] Walk through a structured framework for approaching any system design problem
- [ ] Distinguish **performance** from **scalability** and know when each is the bottleneck
- [ ] Calculate the relationship between **latency** and **throughput** and explain the tension between them
- [ ] Explain the tradeoff between **availability** and **consistency**, and calculate availability in nines
- [ ] State the **CAP theorem** and classify real databases as AP or CP systems
- [ ] Describe **Weak**, **Eventual**, and **Strong** consistency with practical examples

---

## Topics

| # | File | Concept |
|---|------|---------|
| 1 | [01-what-is-system-design.md](01-what-is-system-design.md) | Definition, scope, functional vs. non-functional requirements |
| 2 | [02-how-to-approach-system-design.md](02-how-to-approach-system-design.md) | Step-by-step design framework, capacity estimation, back-of-envelope math |
| 3 | [03-performance-vs-scalability.md](03-performance-vs-scalability.md) | Single-server performance vs. horizontal scalability |
| 4 | [04-latency-vs-throughput.md](04-latency-vs-throughput.md) | Measuring latency (p50/p95/p99), throughput, and the tradeoff |
| 5 | [05-availability-vs-consistency.md](05-availability-vs-consistency.md) | Nines of availability, SLAs, the consistency spectrum |
| 6 | [06-cap-theorem.md](06-cap-theorem.md) | CAP theorem, partition tolerance, real-world database classification |
| 7 | [07-consistency-patterns.md](07-consistency-patterns.md) | Weak, eventual, and strong consistency — implementations and tradeoffs |

---

## Estimated Time

**4–5 hours** (including exercises)

---

## Prerequisites

None — this is the starting point.

---

## Capstone Milestone

By the end of Module 1, you will have written `capstone/scaleforge/docs/architecture.md` — a structured document containing:

- [ ] Functional requirements (what the system does)
- [ ] Non-functional requirements (latency, availability, throughput targets)
- [ ] Capacity estimation (storage, bandwidth, QPS)
- [ ] A high-level component diagram (drawn as ASCII art or described in words)
