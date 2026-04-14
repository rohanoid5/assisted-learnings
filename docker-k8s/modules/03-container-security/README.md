# Module 03 — Container Security

## Overview

Containers are only as secure as you make them. A container running as root with default capabilities, an unscanned base image, and no supply chain verification is an open invitation for attackers — yet this describes most containers in the wild.

This module teaches you to shift security left without slowing down delivery. You'll learn to scan images for CVEs, lock down runtime privileges, harden the container attack surface, and establish trust in your software supply chain. By the end, every DeployForge container will be production-hardened: minimal images, non-root execution, capability restrictions, and cryptographically signed artifacts.

---

## Learning Objectives

After completing this module, you should be able to:

- [ ] Scan container images for CVEs and misconfigurations
- [ ] Run containers as non-root users with minimal capabilities
- [ ] Implement image signing and verification for supply chain security
- [ ] Configure seccomp, AppArmor, and capability restrictions
- [ ] Design defense-in-depth strategies for containerized workloads
- [ ] Apply the principle of least privilege to container configurations
- [ ] Integrate security scanning into CI/CD pipelines
- [ ] Generate and verify SBOMs for container images

---

## Topics

| # | File | Topic | Est. Time |
|---|------|-------|-----------|
| 1 | [01-image-scanning-and-hardening.md](01-image-scanning-and-hardening.md) | Image Scanning & Hardening | 50 min |
| 2 | [02-runtime-security.md](02-runtime-security.md) | Runtime Security: Capabilities, Seccomp & AppArmor | 50 min |
| 3 | [03-supply-chain-security.md](03-supply-chain-security.md) | Supply Chain Security & Image Signing | 40 min |
| 4 | [exercises/README.md](exercises/README.md) | Hands-on Exercises | 60 min |

**Total estimated time: 3–4 hours**

---

## Prerequisites

- [Module 02 — Docker Mastery](../02-docker-mastery/) (Dockerfiles, multi-stage builds, Compose)
- Docker 24+ installed and running (`docker info` works)
- Basic understanding of Linux permissions and users
- Familiarity with CI/CD concepts is helpful but not required

---

## Capstone Milestone

> **Goal:** DeployForge images pass vulnerability scans, run as non-root, have minimal capabilities, and use signed base images.

By the end of this module you'll have:

| Artifact | Description |
|----------|-------------|
| `.trivyignore` | Accepted risk CVEs with documented justification |
| `docker/hadolint.yaml` | Hadolint configuration for Dockerfile linting |
| `docker/seccomp-default.json` | Custom seccomp profile for DeployForge services |
| `scripts/scan-image.sh` | CI-ready script: Trivy scan + Hadolint lint + SBOM generation |
| `scripts/sign-image.sh` | Cosign signing workflow for CI pipelines |
| `docker-compose.security.yml` | Security-hardened Compose overrides (read-only rootfs, caps, seccomp) |

```
┌──────────────────────────────────────────────────────────────────┐
│            DeployForge Security Pipeline                         │
│                                                                  │
│  ┌──────────┐    ┌──────────┐    ┌──────────┐    ┌──────────┐   │
│  │ Hadolint │───▶│  Build   │───▶│  Trivy   │───▶│  Cosign  │   │
│  │  Lint    │    │  Image   │    │   Scan   │    │   Sign   │   │
│  └──────────┘    └──────────┘    └────┬─────┘    └────┬─────┘   │
│                                       │               │         │
│                                  PASS │          PASS │         │
│                                       ▼               ▼         │
│                                 ┌──────────┐    ┌──────────┐    │
│                                 │  Push to  │───▶│  Deploy  │   │
│                                 │ Registry  │    │ (signed) │   │
│                                 └──────────┘    └──────────┘    │
│                                                                  │
│  FAIL at any stage → build breaks, image never reaches registry  │
└──────────────────────────────────────────────────────────────────┘
```

---

## How to Work Through This Module

1. Read each concept file in order (01 → 02 → 03).
2. Run every code example — scan real images, break things, see what Trivy catches.
3. Complete the exercises in `exercises/README.md`.
4. Check off the learning objectives above as you master each one.
5. Move to [Module 04 — Kubernetes Fundamentals](../04-kubernetes-fundamentals/) when ready.
