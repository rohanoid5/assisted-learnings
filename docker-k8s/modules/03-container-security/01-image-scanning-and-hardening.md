# 3.1 — Image Scanning & Hardening

## Concept

Every container image is a bag of software — your application code, its dependencies, the OS packages in the base image, and every transitive dependency they pull in. Any one of those components could harbor a known vulnerability (CVE). Image scanning catches these *before* they reach production. Image hardening ensures there's as little to exploit as possible.

The goal isn't zero CVEs — that's often impossible for a real-world image. The goal is a **known, managed risk posture** where critical and high severity CVEs are remediated, medium CVEs are tracked, and your images contain only what they need to run.

---

## Deep Dive

### CVE Databases and Vulnerability Severity

Vulnerability scanners compare packages in your image against databases of known CVEs (Common Vulnerabilities and Exposures).

```
┌─────────────────────────────────────────────────────────────┐
│                  How Image Scanning Works                     │
│                                                              │
│  ┌────────────┐    ┌────────────┐    ┌─────────────────┐    │
│  │  Container  │───▶│  Scanner   │───▶│  CVE Databases  │   │
│  │   Image     │    │ (Trivy,    │    │  (NVD, GHSA,    │   │
│  │             │    │  Grype)    │    │   Alpine SecDB,  │   │
│  └────────────┘    └─────┬──────┘    │   Debian Tracker)│   │
│                          │           └─────────────────┘    │
│                          ▼                                   │
│                    ┌────────────┐                            │
│                    │  Report:   │                            │
│                    │  CRITICAL:2│                            │
│                    │  HIGH: 5   │                            │
│                    │  MEDIUM: 12│                            │
│                    │  LOW: 23   │                            │
│                    └────────────┘                            │
└─────────────────────────────────────────────────────────────┘
```

**CVSS severity levels:**

| Severity | CVSS Score | Action | Example |
|----------|-----------|--------|---------|
| Critical | 9.0–10.0 | Fix immediately, block deployment | Remote code execution in OpenSSL |
| High | 7.0–8.9 | Fix within days | Privilege escalation in kernel |
| Medium | 4.0–6.9 | Fix within sprint | Information disclosure |
| Low | 0.1–3.9 | Track, fix when convenient | Minor DoS under rare conditions |
| Negligible | — | Accept risk | Theoretical attack, no known exploit |

> **Key insight:** Severity alone doesn't tell you exploitability. A critical CVE in a library you import but never call is lower risk than a medium CVE in a code path you exercise on every request. Use EPSS (Exploit Prediction Scoring System) and reachability analysis for a more nuanced view.

### Scanning Tools Compared

| Tool | License | Speed | DB Freshness | CI Integration | Extras |
|------|---------|-------|-------------|----------------|--------|
| **Trivy** | Apache 2.0 | Fast | Excellent | GitHub Actions, GitLab CI, etc. | Also scans IaC, secrets, licenses |
| **Grype** | Apache 2.0 | Fast | Good | Easy CLI integration | Pairs with Syft for SBOM |
| **Snyk Container** | Freemium | Medium | Excellent | Deep CI/CD integrations | Fix suggestions, monitoring |
| **Docker Scout** | Freemium | Fast | Good | Built into Docker Desktop | Policy evaluation |
| **Clair** | Apache 2.0 | Slow | Good | Registry-native | Designed for registry integration |

### Running Trivy

Trivy is the de facto standard for open-source image scanning — fast, accurate, and easy to integrate into CI.

```bash
# Install Trivy (macOS)
brew install trivy

# Install Trivy (Linux)
curl -sfL https://raw.githubusercontent.com/aquasecurity/trivy/main/contrib/install.sh | sh -s -- -b /usr/local/bin

# Scan an image
trivy image node:20-alpine

# Scan and fail on critical/high (CI mode)
trivy image --severity CRITICAL,HIGH --exit-code 1 node:20-alpine

# Scan with SARIF output for GitHub Security tab
trivy image --format sarif --output trivy-results.sarif my-image:latest

# Scan a Dockerfile (misconfiguration scanning)
trivy config Dockerfile

# Scan a local image (not yet pushed to registry)
docker build -t my-app:test .
trivy image my-app:test
```

**Trivy output breakdown:**

```
my-app:test (alpine 3.19.1)

Total: 3 (CRITICAL: 0, HIGH: 1, MEDIUM: 2, LOW: 0, UNKNOWN: 0)

┌───────────────┬────────────────┬──────────┬────────┬───────────────────┬──────────────────┐
│    Library    │ Vulnerability  │ Severity │ Status │ Installed Version │  Fixed Version   │
├───────────────┼────────────────┼──────────┼────────┼───────────────────┼──────────────────┤
│ libcrypto3    │ CVE-2024-0727  │ HIGH     │ fixed  │ 3.1.4-r2          │ 3.1.4-r3         │
│ libssl3       │ CVE-2024-0727  │ MEDIUM   │ fixed  │ 3.1.4-r2          │ 3.1.4-r3         │
│ busybox       │ CVE-2023-42363 │ MEDIUM   │ fixed  │ 1.36.1-r15        │ 1.36.1-r16       │
└───────────────┴────────────────┴──────────┴────────┴───────────────────┴──────────────────┘
```

### Ignoring Accepted Risks with .trivyignore

Not every CVE is actionable. Some exist in packages you can't update, or affect code paths your app doesn't use. Use `.trivyignore` to document accepted risk:

```
# .trivyignore

# CVE-2024-0727: OpenSSL PKCS12 vulnerability — we don't use PKCS12 parsing.
# Accepted by: @security-team on 2024-03-15. Review by 2024-06-15.
CVE-2024-0727

# CVE-2023-42363: busybox wall vulnerability — container has no tty users.
CVE-2023-42363
```

> **Rule:** Every `.trivyignore` entry MUST have a justification comment and a review date. Blanket ignoring CVEs defeats the purpose of scanning.

### Static Analysis with Hadolint

Hadolint lints Dockerfiles against best practices. It catches problems scanners miss — like running as root or using `ADD` instead of `COPY`.

```bash
# Install
brew install hadolint

# Lint a Dockerfile
hadolint Dockerfile

# Lint with inline ignore rules
hadolint --ignore DL3018 Dockerfile

# Strict mode (treat warnings as errors)
hadolint --failure-threshold warning Dockerfile
```

**Common Hadolint rules:**

| Rule | Description | Severity |
|------|-------------|----------|
| DL3006 | Always tag the base image | Warning |
| DL3007 | Using `latest` is prone to errors | Warning |
| DL3008 | Pin versions in `apt-get install` | Warning |
| DL3018 | Pin versions in `apk add` | Warning |
| DL3025 | Use JSON notation for CMD/ENTRYPOINT | Info |
| DL3002 | Last USER should not be root | Warning |
| DL4006 | Set the SHELL option `-o pipefail` | Warning |
| SC2086 | Double quote to prevent globbing | Info |

```yaml
# .hadolint.yaml — project-level configuration
ignored:
  - DL3018   # We accept unpinned apk versions for patch updates
trustedRegistries:
  - docker.io
  - gcr.io
  - ghcr.io
override:
  error:
    - DL3002  # Promote "last user should not be root" to error
    - DL3007  # Promote "don't use latest" to error
```

### Distroless Images and Minimal Attack Surface

The best vulnerability is the one that doesn't exist in your image. Fewer packages = fewer CVEs = smaller attack surface.

```
┌─────────────────────────────────────────────────────────────┐
│             Attack Surface Comparison                        │
│                                                              │
│  ubuntu:22.04     ██████████████████████████████  ~400 pkgs  │
│  node:20          ███████████████████████████████ ~450 pkgs  │
│  node:20-slim     ████████████████░░░░░░░░░░░░░  ~200 pkgs  │
│  node:20-alpine   ██████░░░░░░░░░░░░░░░░░░░░░░░  ~40 pkgs   │
│  distroless       ██░░░░░░░░░░░░░░░░░░░░░░░░░░░  ~15 pkgs   │
│  scratch          ░░░░░░░░░░░░░░░░░░░░░░░░░░░░░  0 pkgs     │
│                                                              │
│  Fewer packages = fewer CVEs = smaller attack surface        │
└─────────────────────────────────────────────────────────────┘
```

**Distroless images** (by Google) contain only your app and its runtime dependencies — no shell, no package manager, no coreutils. An attacker who gets code execution inside a distroless container has almost nothing to work with.

```dockerfile
# Multi-stage build → distroless final image
FROM node:20-alpine AS builder
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

# Distroless has no shell, no package manager — just Node.js
FROM gcr.io/distroless/nodejs20-debian12
WORKDIR /app
COPY --from=builder /app/dist ./dist
COPY --from=builder /app/node_modules ./node_modules
COPY --from=builder /app/package.json ./

# No shell available — exec form is mandatory
CMD ["dist/server.js"]
```

**Distroless trade-offs:**

| Benefit | Drawback |
|---------|----------|
| Minimal CVE surface | No shell for debugging (`docker exec` won't help) |
| Smaller images | Can't install additional packages |
| No package manager to exploit | Harder to troubleshoot in production |
| Forces exec form (proper signal handling) | Not compatible with scripts that need bash |

> **Debugging distroless:** Use `kubectl debug` (Module 05) or ephemeral debug containers to attach a shell when needed.

### CIS Docker Benchmark

The Center for Internet Security (CIS) publishes benchmarks for Docker host and container configuration. Docker Bench Security automates the check.

```bash
# Run Docker Bench Security
docker run --rm --net host --pid host \
  --userns host --cap-add audit_control \
  -e DOCKER_CONTENT_TRUST=$DOCKER_CONTENT_TRUST \
  -v /etc:/etc:ro \
  -v /var/lib:/var/lib:ro \
  -v /var/run/docker.sock:/var/run/docker.sock:ro \
  docker/docker-bench-security
```

Key benchmark categories:
1. **Host Configuration** — audit daemon, file permissions
2. **Docker Daemon Configuration** — TLS, logging, user namespaces
3. **Container Images** — trusted base images, no secrets in build
4. **Container Runtime** — capabilities, read-only rootfs, resource limits
5. **Docker Security Operations** — scanning, monitoring, incident response

### Image Pinning: Digest vs Tag

Tags are mutable — `node:20-alpine` today might be a different image tomorrow. Digests are immutable.

```dockerfile
# ❌ Mutable — different image on each pull
FROM node:20-alpine

# ✅ Immutable — always the exact same image
FROM node:20-alpine@sha256:1a526b97cace0b7e97c2bbdc7ab12b4e3b6f30f20c1d8e4a7c94b8ee01ef4c96

# ✅ Practical compromise — tagged + digest for readability
FROM node:20-alpine@sha256:1a526b97cace0b7e97c2bbdc7ab12b4e3b6f30f20c1d8e4a7c94b8ee01ef4c96
```

```bash
# Get the digest of an image
docker inspect --format='{{index .RepoDigests 0}}' node:20-alpine
# → node@sha256:1a526b97cace0b7e97c2bbdc7ab12b4e3b6f30f20c1d8e4a7c94b8ee01ef4c96

# Pull by digest
docker pull node@sha256:1a526b97cace0b7e97c2bbdc7ab12b4e3b6f30f20c1d8e4a7c94b8ee01ef4c96
```

> **Automation tip:** Use Renovate or Dependabot to automatically update digest pins when new patch versions are released. You get reproducibility AND automatic security patches.

---

## Code Examples

### Example 1: CI Pipeline Scan Script

```bash
#!/usr/bin/env bash
# scripts/scan-image.sh — Scan a Docker image and fail on critical/high CVEs
set -euo pipefail

IMAGE="${1:?Usage: scan-image.sh <image:tag>}"
SEVERITY_THRESHOLD="${2:-CRITICAL,HIGH}"
EXIT_CODE=0

echo "=== Hadolint: Linting Dockerfile ==="
hadolint Dockerfile --failure-threshold warning || EXIT_CODE=1

echo ""
echo "=== Trivy: Scanning ${IMAGE} ==="
trivy image \
  --severity "${SEVERITY_THRESHOLD}" \
  --exit-code 1 \
  --no-progress \
  --format table \
  "${IMAGE}" || EXIT_CODE=1

echo ""
echo "=== Trivy: Misconfiguration scan ==="
trivy config Dockerfile --exit-code 1 || EXIT_CODE=1

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "❌ Security scan FAILED — fix findings before merging."
  exit 1
fi

echo "✅ All security checks passed."
```

### Example 2: Hardened Dockerfile (From Scratch)

```dockerfile
# Stage 1: Build
FROM node:20-alpine AS builder
WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci

COPY tsconfig.json ./
COPY src/ ./src/
RUN npm run build

# Prune dev dependencies
RUN npm prune --production

# Stage 2: Production — hardened
FROM node:20-alpine@sha256:1a526b97cace0b7e97c2bbdc7ab12b4e3b6f30f20c1d8e4a7c94b8ee01ef4c96

LABEL org.opencontainers.image.title="DeployForge API"
LABEL org.opencontainers.image.description="Hardened production image"

WORKDIR /app

# Create non-root user
RUN addgroup --system --gid 1001 appgroup && \
    adduser --system --uid 1001 --ingroup appgroup appuser

# Copy only production artifacts
COPY --from=builder --chown=appuser:appgroup /app/dist ./dist
COPY --from=builder --chown=appuser:appgroup /app/node_modules ./node_modules
COPY --from=builder --chown=appuser:appgroup /app/package.json ./

# Remove unnecessary binaries from the base image
RUN apk --no-cache add --virtual .remove-helpers && \
    rm -rf /usr/local/lib/node_modules/npm \
           /usr/local/bin/npm \
           /usr/local/bin/npx \
           /opt/yarn* \
           /usr/local/bin/yarn* && \
    apk del .remove-helpers

# Drop to non-root
USER appuser

EXPOSE 3000

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
  CMD node -e "fetch('http://localhost:3000/health').then(r=>{if(!r.ok)throw r.status})" || exit 1

ENTRYPOINT ["node"]
CMD ["dist/server.js"]
```

### Example 3: GitHub Actions Integration

```yaml
# .github/workflows/security-scan.yml
name: Container Security Scan

on:
  pull_request:
    paths:
      - 'docker/**'
      - 'Dockerfile'
      - 'package*.json'

jobs:
  hadolint:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: hadolint/hadolint-action@v3.1.0
        with:
          dockerfile: Dockerfile
          failure-threshold: warning

  trivy:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Build image
        run: docker build -t ${{ github.sha }} .

      - name: Run Trivy vulnerability scanner
        uses: aquasecurity/trivy-action@master
        with:
          image-ref: ${{ github.sha }}
          format: 'sarif'
          output: 'trivy-results.sarif'
          severity: 'CRITICAL,HIGH'
          exit-code: '1'

      - name: Upload Trivy scan results to GitHub Security tab
        uses: github/codeql-action/upload-sarif@v3
        if: always()
        with:
          sarif_file: 'trivy-results.sarif'
```

---

## Try It Yourself

### Challenge 1: Scan a Popular Image

Pull `nginx:latest` and `nginx:alpine` and scan both with Trivy. Compare the CVE counts.

```bash
trivy image nginx:latest
trivy image nginx:alpine
```

Questions to answer:
1. How many CRITICAL and HIGH CVEs does each have?
2. Which packages contribute the most vulnerabilities?
3. How much smaller is the alpine variant?

<details>
<summary>Show solution</summary>

```bash
# Scan both images
trivy image --severity CRITICAL,HIGH nginx:latest 2>&1 | tail -5
trivy image --severity CRITICAL,HIGH nginx:alpine 2>&1 | tail -5

# Compare image sizes
docker images nginx --format "table {{.Tag}}\t{{.Size}}"
# TAG       SIZE
# latest    ~190MB
# alpine    ~45MB

# Typical results (varies by date):
# nginx:latest  — 5-15 HIGH/CRITICAL (Debian packages: libssl, libc, etc.)
# nginx:alpine  — 0-3 HIGH/CRITICAL (fewer packages = fewer CVEs)
#
# The alpine variant often has 70-80% fewer vulnerabilities
# because it ships ~40 packages vs ~200+ in the Debian-based image.
```

**Key takeaway:** Choosing `alpine` over a Debian-based image can eliminate the majority of CVEs with zero code changes.

</details>

### Challenge 2: Lint a Dockerfile with Hadolint

Run Hadolint against this Dockerfile and fix every finding:

```dockerfile
FROM node:latest
RUN apt-get update && apt-get install -y curl
ADD . /app
WORKDIR /app
RUN npm install
EXPOSE 3000
CMD npm start
```

<details>
<summary>Show solution</summary>

Hadolint findings:
- `DL3007` — Using `latest` tag
- `DL3008` — Pin versions in `apt-get install`
- `DL3009` — Delete apt-get lists after install
- `DL3020` — Use COPY instead of ADD
- `DL3016` — Pin versions in `npm install`
- `DL3025` — Use JSON notation for CMD
- `DL3002` — Last USER should not be root

Fixed Dockerfile:

```dockerfile
FROM node:20-alpine

WORKDIR /app

RUN addgroup --system --gid 1001 appgroup && \
    adduser --system --uid 1001 --ingroup appgroup appuser

COPY package.json package-lock.json ./
RUN npm ci --only=production && npm cache clean --force

COPY --chown=appuser:appgroup . .

USER appuser

EXPOSE 3000

HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
  CMD node -e "fetch('http://localhost:3000/health').then(r=>{if(!r.ok)throw r.status})" || exit 1

ENTRYPOINT ["node"]
CMD ["server.js"]
```

```bash
# Verify — should produce no warnings
hadolint Dockerfile
```

</details>

### Challenge 3: Pin a Base Image by Digest

1. Pull `node:20-alpine` and find its digest.
2. Update a Dockerfile to use the digest-pinned version.
3. Verify the pinned image is identical to the tagged one.

<details>
<summary>Show solution</summary>

```bash
# Pull and get the digest
docker pull node:20-alpine
docker inspect --format='{{index .RepoDigests 0}}' node:20-alpine
# → node@sha256:<some-long-hash>

# Use it in a Dockerfile
# FROM node:20-alpine@sha256:<some-long-hash>

# Verify — both should have the same image ID
docker pull node:20-alpine
docker pull node@sha256:<some-long-hash>
docker images --digests node | head -5
# Both entries will show the same IMAGE ID
```

**Why this matters:** If someone pushes a compromised `node:20-alpine` tag, the digest pin protects you — Docker will refuse to pull because the checksum won't match.

</details>

---

## Capstone Connection

**DeployForge's CI pipeline scans every image with Trivy before pushing to the registry.** Here's how this topic connects to the capstone:

- **Trivy in CI** — The `scripts/scan-image.sh` script runs on every PR that modifies Docker files or dependencies. The pipeline fails on CRITICAL or HIGH CVEs, preventing vulnerable images from reaching the registry. This is the first gate in the security pipeline diagram from the module README.

- **Hadolint linting** — A `.hadolint.yaml` configuration enforces Dockerfile best practices across all DeployForge services. The rule `DL3002` (last USER must not be root) is promoted to an error — no service can ship running as root.

- **Image pinning by digest** — All base images in DeployForge Dockerfiles are pinned by digest for reproducibility. Renovate automatically opens PRs when new digests are available, so we get security patches without sacrificing reproducibility.

- **Distroless production images** — The API Gateway and Worker services use `gcr.io/distroless/nodejs20` for the final stage. The build stage uses `node:20-alpine` (needs npm, tsc), but the production image has no shell, no package manager, and a Trivy scan that typically shows 0 CRITICAL and 0 HIGH CVEs.

- **`.trivyignore` with justifications** — Accepted CVEs are documented with team owner, justification, and review date. The security review process (Module 10) periodically audits these exceptions.

In the next topic (3.2), we'll lock down what these hardened containers can do at runtime with Linux capabilities, seccomp profiles, and AppArmor.
