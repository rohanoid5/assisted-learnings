# 10.1 — CI Pipelines: Build, Test, Push

## Concept

A CI pipeline is the automated gauntlet that every code change runs through before it reaches a container registry. If your pipeline doesn't catch the bug, your users will. The goal is fast, deterministic feedback: within minutes of pushing code, a developer knows whether their change is safe to ship.

But "run the tests" is the baseline, not the finish line. A production-grade CI pipeline also builds multi-architecture container images, scans for CVEs, enforces branch protection, and produces immutable, content-addressed artifacts. The pipeline itself is code — versioned, reviewed, and tested alongside the application it protects.

This section covers the anatomy of a CI pipeline using GitHub Actions, from workflow syntax to advanced patterns like matrix builds, layer caching, and security scanning.

---

## Deep Dive

### CI Pipeline Stages

A well-structured pipeline moves from cheapest/fastest checks to most expensive. Fail early, fail fast.

```
┌─────────────────────────────────────────────────────────────────┐
│                    CI Pipeline — Stage Order                     │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│   ┌────────┐   ┌────────┐   ┌────────┐   ┌────────┐   ┌─────┐│
│   │  Lint  │──▶│  Test  │──▶│ Build  │──▶│  Scan  │──▶│Push ││
│   │ ~10s   │   │ ~2min  │   │ ~3min  │   │ ~1min  │   │~30s ││
│   └────────┘   └────────┘   └────────┘   └────────┘   └─────┘│
│                                                                  │
│   cheapest ◀─────────────────────────────────────▶ most expensive│
│   fastest                                            slowest     │
└─────────────────────────────────────────────────────────────────┘
```

| Stage | Purpose | Tools | Fails When |
|-------|---------|-------|------------|
| **Lint** | Code style, formatting, static analysis | ESLint, Prettier, hadolint, shellcheck | Style violations, Dockerfile anti-patterns |
| **Test** | Unit tests, integration tests | Jest, pytest, JUnit, go test | Assertion failures, timeouts |
| **Build** | Compile code, build container image | `docker build`, `docker buildx`, Kaniko | Compilation errors, missing deps |
| **Scan** | CVE scanning, SAST, secret detection | Trivy, Grype, Semgrep, gitleaks | Critical/high CVEs, hardcoded secrets |
| **Push** | Tag and push image to registry | `docker push`, crane | Auth failures, registry unavailable |

> **Key insight:** Each stage gates the next. If linting fails, you never burn compute on a Docker build. This ordering saves both time and money — especially at scale where you might run thousands of pipelines per day.

### GitHub Actions Workflow Syntax

GitHub Actions uses YAML workflow files in `.github/workflows/`. Here's the anatomy:

```yaml
# .github/workflows/ci.yml
name: CI Pipeline

# When does this pipeline run?
on:
  push:
    branches: [main]
  pull_request:
    branches: [main]

# Environment variables available to all jobs
env:
  REGISTRY: ghcr.io
  IMAGE_NAME: ${{ github.repository }}

# Prevent concurrent runs on the same branch
concurrency:
  group: ci-${{ github.ref }}
  cancel-in-progress: true

jobs:
  lint:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Run hadolint
        uses: hadolint/hadolint-action@v3.1.0
        with:
          dockerfile: Dockerfile

      - name: Run shellcheck
        uses: ludeeus/action-shellcheck@2.0.0

  test:
    runs-on: ubuntu-latest
    needs: [lint]              # ← depends on lint passing
    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-node@v4
        with:
          node-version: '20'
          cache: 'npm'         # ← built-in npm cache

      - run: npm ci
      - run: npm test -- --coverage

      - name: Upload coverage
        uses: actions/upload-artifact@v4
        with:
          name: coverage-report
          path: coverage/

  build-and-push:
    runs-on: ubuntu-latest
    needs: [test]
    permissions:
      contents: read
      packages: write          # ← needed for GHCR push
    steps:
      - uses: actions/checkout@v4

      - name: Set up Docker Buildx
        uses: docker/setup-buildx-action@v3

      - name: Log in to GHCR
        uses: docker/login-action@v3
        with:
          registry: ghcr.io
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}

      - name: Build and push
        uses: docker/build-push-action@v5
        with:
          context: .
          push: ${{ github.event_name != 'pull_request' }}
          tags: ${{ steps.meta.outputs.tags }}
          cache-from: type=gha
          cache-to: type=gha,mode=max
```

> **Production note:** The `concurrency` block is often overlooked. Without it, pushing three commits in quick succession spawns three parallel pipelines — wasting resources and potentially causing race conditions on image tags.

### Matrix Builds

When you need to test across multiple versions or build for multiple architectures:

```yaml
jobs:
  test:
    strategy:
      fail-fast: false         # ← don't cancel other matrix jobs on failure
      matrix:
        node-version: [18, 20, 22]
        os: [ubuntu-latest, ubuntu-24.04]
        exclude:
          - node-version: 18
            os: ubuntu-24.04   # ← skip specific combinations
    runs-on: ${{ matrix.os }}
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with:
          node-version: ${{ matrix.node-version }}
      - run: npm ci && npm test
```

### Caching Strategies

Caching is the single biggest lever for CI performance. Two layers matter:

**1. Dependency cache (npm, Maven, pip)**

```yaml
- uses: actions/setup-node@v4
  with:
    node-version: '20'
    cache: 'npm'               # ← caches ~/.npm based on package-lock.json hash
```

**2. Docker layer cache**

```yaml
- uses: docker/build-push-action@v5
  with:
    context: .
    cache-from: type=gha       # ← pull cache from GitHub Actions cache
    cache-to: type=gha,mode=max # ← push all layers, not just final
```

Without caching, a typical Node.js build takes 4-6 minutes. With both caches warm, it drops to 60-90 seconds.

```
┌──────────────────────────────────────────────────────┐
│           Docker Layer Cache — mode=max               │
├──────────────────────────────────────────────────────┤
│                                                       │
│  mode=min (default):                                  │
│  Only caches layers in the final build stage          │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐             │
│  │ Stage 1  │ │ Stage 2  │ │ Stage 3  │ ← cached    │
│  │ (deps)   │ │ (build)  │ │ (final)  │             │
│  │ ✗        │ │ ✗        │ │ ✓        │             │
│  └──────────┘ └──────────┘ └──────────┘             │
│                                                       │
│  mode=max:                                            │
│  Caches ALL layers from ALL stages                    │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐             │
│  │ Stage 1  │ │ Stage 2  │ │ Stage 3  │             │
│  │ (deps)   │ │ (build)  │ │ (final)  │             │
│  │ ✓        │ │ ✓        │ │ ✓        │ ← all cached│
│  └──────────┘ └──────────┘ └──────────┘             │
│                                                       │
│  Always use mode=max for multi-stage builds.          │
└──────────────────────────────────────────────────────┘
```

### Container Image Tagging Strategies

Tags are metadata, not guarantees. `:latest` is a lie — it's the last image someone pushed, not necessarily the newest. Production-grade tagging needs to be deterministic and traceable.

| Strategy | Tag Format | Use Case | Traceability |
|----------|-----------|----------|-------------|
| **Git SHA** | `sha-abc1234` | Every build | Exact commit → image mapping |
| **Semver** | `v1.2.3` | Releases | Human-readable version |
| **Branch** | `main`, `feature-x` | Dev environments | Current branch state |
| **PR** | `pr-42` | Preview environments | Ephemeral review apps |
| **Timestamp** | `20240115-143022` | Audit trail | When it was built |

The best approach is to apply **multiple tags** to each image:

```yaml
- name: Docker metadata
  id: meta
  uses: docker/metadata-action@v5
  with:
    images: ghcr.io/${{ github.repository }}
    tags: |
      type=sha,prefix=sha-
      type=ref,event=branch
      type=ref,event=pr,prefix=pr-
      type=semver,pattern=v{{version}}
      type=semver,pattern=v{{major}}.{{minor}}
```

> **Key insight:** Git SHA tags are the backbone of production deployments. When an incident happens at 3 AM, you need `sha-abc1234` → `git show abc1234` to instantly know what code is running. Semver tags are for humans; SHA tags are for machines.

### Multi-Architecture Builds

If you deploy to both amd64 (Intel/AMD) and arm64 (AWS Graviton, Apple Silicon), you need multi-arch images:

```yaml
- name: Set up QEMU
  uses: docker/setup-qemu-action@v3

- name: Set up Docker Buildx
  uses: docker/setup-buildx-action@v3

- name: Build and push multi-arch
  uses: docker/build-push-action@v5
  with:
    context: .
    platforms: linux/amd64,linux/arm64
    push: true
    tags: ${{ steps.meta.outputs.tags }}
    cache-from: type=gha
    cache-to: type=gha,mode=max
```

The resulting manifest list lets `docker pull` automatically select the right architecture:

```bash
docker manifest inspect ghcr.io/org/deployforge:v1.0.0
# → shows both amd64 and arm64 digests under one tag
```

> **Production note:** Multi-arch builds via QEMU emulation are 3-5x slower for the non-native architecture. For large projects, consider native runners (`runs-on: [self-hosted, arm64]`) or a build matrix that fans out per platform then merges manifests.

### Security Scanning in CI

Ship no image with known critical CVEs. Trivy is the de facto open-source scanner:

```yaml
  scan:
    runs-on: ubuntu-latest
    needs: [build-and-push]
    steps:
      - name: Run Trivy vulnerability scanner
        uses: aquasecurity/trivy-action@0.28.0
        with:
          image-ref: ghcr.io/${{ github.repository }}:sha-${{ github.sha }}
          format: 'sarif'
          output: 'trivy-results.sarif'
          severity: 'CRITICAL,HIGH'
          exit-code: '1'         # ← fail the pipeline on critical/high

      - name: Upload Trivy scan results
        uses: github/codeql-action/upload-sarif@v3
        if: always()             # ← upload even if scan found vulnerabilities
        with:
          sarif_file: 'trivy-results.sarif'
```

For secret detection, add gitleaks as a pre-push check:

```yaml
      - name: Detect secrets
        uses: gitleaks/gitleaks-action@v2
        env:
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
```

### Branch Protection and Required Checks

CI pipelines are only effective when they're mandatory. Configure branch protection rules:

```
Repository Settings → Branches → Branch protection rules → main

✓ Require a pull request before merging
  ✓ Require approvals: 1
  ✓ Dismiss stale pull request approvals
✓ Require status checks to pass before merging
  ✓ Required checks: lint, test, scan
  ✓ Require branches to be up to date before merging
✓ Require signed commits
✓ Do not allow bypassing the above settings
```

> **Caution:** "Require branches to be up to date" means every PR must be rebased on the latest `main` before merge. This prevents "merge skew" (where two PRs individually pass but together break), but it creates serialization — a bottleneck on high-throughput repos. Consider a merge queue for teams with >10 PRs/day.

---

## Code Examples

### Complete CI Workflow for a Node.js + Docker Project

```yaml
# .github/workflows/ci.yml
name: CI

on:
  push:
    branches: [main]
    tags: ['v*']
  pull_request:
    branches: [main]

env:
  REGISTRY: ghcr.io
  IMAGE_NAME: ${{ github.repository }}

concurrency:
  group: ci-${{ github.ref }}
  cancel-in-progress: true

jobs:
  lint:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-node@v4
        with:
          node-version: '20'
          cache: 'npm'

      - run: npm ci
      - run: npm run lint
      - run: npm run typecheck

      - name: Lint Dockerfile
        uses: hadolint/hadolint-action@v3.1.0
        with:
          dockerfile: Dockerfile

  test:
    runs-on: ubuntu-latest
    needs: [lint]
    services:
      postgres:
        image: postgres:15
        env:
          POSTGRES_USER: test
          POSTGRES_PASSWORD: test
          POSTGRES_DB: test
        ports:
          - 5432:5432
        options: >-
          --health-cmd pg_isready
          --health-interval 10s
          --health-timeout 5s
          --health-retries 5
    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-node@v4
        with:
          node-version: '20'
          cache: 'npm'

      - run: npm ci
      - run: npm test -- --coverage --forceExit
        env:
          DATABASE_URL: postgres://test:test@localhost:5432/test

      - name: Upload coverage
        uses: actions/upload-artifact@v4
        if: always()
        with:
          name: coverage
          path: coverage/lcov.info

  build-and-push:
    runs-on: ubuntu-latest
    needs: [test]
    permissions:
      contents: read
      packages: write
    outputs:
      image-digest: ${{ steps.build.outputs.digest }}
      image-tag: ${{ steps.meta.outputs.version }}
    steps:
      - uses: actions/checkout@v4

      - name: Docker metadata
        id: meta
        uses: docker/metadata-action@v5
        with:
          images: ${{ env.REGISTRY }}/${{ env.IMAGE_NAME }}
          tags: |
            type=sha,prefix=sha-
            type=ref,event=branch
            type=ref,event=pr,prefix=pr-
            type=semver,pattern=v{{version}}
            type=semver,pattern=v{{major}}.{{minor}}
            type=raw,value=latest,enable={{is_default_branch}}

      - name: Set up QEMU
        uses: docker/setup-qemu-action@v3

      - name: Set up Docker Buildx
        uses: docker/setup-buildx-action@v3

      - name: Log in to GHCR
        uses: docker/login-action@v3
        with:
          registry: ${{ env.REGISTRY }}
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}

      - name: Build and push
        id: build
        uses: docker/build-push-action@v5
        with:
          context: .
          platforms: linux/amd64,linux/arm64
          push: ${{ github.event_name != 'pull_request' }}
          tags: ${{ steps.meta.outputs.tags }}
          labels: ${{ steps.meta.outputs.labels }}
          cache-from: type=gha
          cache-to: type=gha,mode=max
          build-args: |
            BUILD_DATE=${{ github.event.head_commit.timestamp }}
            VCS_REF=${{ github.sha }}

  scan:
    runs-on: ubuntu-latest
    needs: [build-and-push]
    if: github.event_name != 'pull_request'
    steps:
      - name: Run Trivy
        uses: aquasecurity/trivy-action@0.28.0
        with:
          image-ref: ${{ env.REGISTRY }}/${{ env.IMAGE_NAME }}:sha-${{ github.sha }}
          format: 'table'
          exit-code: '1'
          severity: 'CRITICAL,HIGH'
          ignore-unfixed: true
```

### Reusable Workflow for Multi-Service Repos

```yaml
# .github/workflows/build-service.yml (reusable)
name: Build Service

on:
  workflow_call:
    inputs:
      service-name:
        required: true
        type: string
      dockerfile-path:
        required: true
        type: string
      context:
        required: true
        type: string

jobs:
  build:
    runs-on: ubuntu-latest
    permissions:
      contents: read
      packages: write
    steps:
      - uses: actions/checkout@v4

      - name: Build and push ${{ inputs.service-name }}
        uses: docker/build-push-action@v5
        with:
          context: ${{ inputs.context }}
          file: ${{ inputs.dockerfile-path }}
          push: ${{ github.ref == 'refs/heads/main' }}
          tags: |
            ghcr.io/${{ github.repository }}/${{ inputs.service-name }}:sha-${{ github.sha }}
            ghcr.io/${{ github.repository }}/${{ inputs.service-name }}:latest
          cache-from: type=gha,scope=${{ inputs.service-name }}
          cache-to: type=gha,scope=${{ inputs.service-name }},mode=max
```

```yaml
# .github/workflows/ci.yml (caller)
name: CI
on:
  push:
    branches: [main]

jobs:
  build-api:
    uses: ./.github/workflows/build-service.yml
    with:
      service-name: api
      dockerfile-path: services/api/Dockerfile
      context: services/api

  build-worker:
    uses: ./.github/workflows/build-service.yml
    with:
      service-name: worker
      dockerfile-path: services/worker/Dockerfile
      context: services/worker
```

### Validating CI Locally with `act`

Don't wait for GitHub to tell you your workflow is broken:

```bash
# Install act (runs GitHub Actions locally via Docker)
brew install act

# Run all jobs
act push

# Run a specific job
act push -j lint

# Use a specific event payload
act pull_request -e event.json

# List all jobs without running
act push --list
```

---

## Try It Yourself

### Challenge 1: Add a Build Matrix with Selective Caching

You have a monorepo with three services. Each needs its own Docker build with per-service cache scoping. Write a workflow that:
- Uses a matrix strategy to build all three services in parallel
- Scopes the GitHub Actions cache per service name
- Only pushes images when merging to `main`

<details>
<summary>Show solution</summary>

```yaml
name: Monorepo CI

on:
  push:
    branches: [main]
  pull_request:
    branches: [main]

jobs:
  build:
    runs-on: ubuntu-latest
    permissions:
      contents: read
      packages: write
    strategy:
      fail-fast: false
      matrix:
        service:
          - name: api
            context: services/api
          - name: worker
            context: services/worker
          - name: gateway
            context: services/gateway
    steps:
      - uses: actions/checkout@v4

      - name: Set up Docker Buildx
        uses: docker/setup-buildx-action@v3

      - name: Log in to GHCR
        if: github.event_name != 'pull_request'
        uses: docker/login-action@v3
        with:
          registry: ghcr.io
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}

      - name: Build ${{ matrix.service.name }}
        uses: docker/build-push-action@v5
        with:
          context: ${{ matrix.service.context }}
          push: ${{ github.event_name != 'pull_request' }}
          tags: |
            ghcr.io/${{ github.repository }}/${{ matrix.service.name }}:sha-${{ github.sha }}
          cache-from: type=gha,scope=build-${{ matrix.service.name }}
          cache-to: type=gha,scope=build-${{ matrix.service.name }},mode=max
```

</details>

### Challenge 2: Implement a Version Tagging Strategy

Write the `docker/metadata-action` configuration that produces these tags:
- On `main` push: `latest`, `sha-<7chars>`, `main`
- On tag `v1.2.3`: `v1.2.3`, `v1.2`, `v1`, `sha-<7chars>`
- On PR #42: `pr-42`, `sha-<7chars>`

<details>
<summary>Show solution</summary>

```yaml
- name: Docker metadata
  id: meta
  uses: docker/metadata-action@v5
  with:
    images: ghcr.io/${{ github.repository }}
    tags: |
      # SHA tag on every event
      type=sha,prefix=sha-

      # Branch name on push
      type=ref,event=branch

      # PR number on pull request
      type=ref,event=pr,prefix=pr-

      # Full semver on tag: v1.2.3
      type=semver,pattern=v{{version}}

      # Minor semver on tag: v1.2
      type=semver,pattern=v{{major}}.{{minor}}

      # Major semver on tag: v1
      type=semver,pattern=v{{major}}

      # latest only on default branch
      type=raw,value=latest,enable={{is_default_branch}}
```

This produces a manifest with all applicable tags. The `docker/metadata-action` evaluates each rule against the current event and only emits tags that match.

</details>

### Challenge 3: Add Security Scanning That Blocks Merges

Configure a CI job that:
1. Scans the built image with Trivy for CRITICAL and HIGH vulnerabilities
2. Uploads results as a SARIF file to GitHub Security tab
3. Fails the pipeline if any CRITICAL vulnerability is found (but not HIGH — those are warnings)

<details>
<summary>Show solution</summary>

```yaml
  scan:
    runs-on: ubuntu-latest
    needs: [build-and-push]
    permissions:
      security-events: write
    steps:
      # Scan for CRITICAL — fail the build
      - name: Trivy scan (critical — gate)
        uses: aquasecurity/trivy-action@0.28.0
        with:
          image-ref: ghcr.io/${{ github.repository }}:sha-${{ github.sha }}
          format: 'table'
          exit-code: '1'
          severity: 'CRITICAL'
          ignore-unfixed: true

      # Scan for all severities — upload to GitHub Security tab
      - name: Trivy scan (full — report)
        uses: aquasecurity/trivy-action@0.28.0
        if: always()
        with:
          image-ref: ghcr.io/${{ github.repository }}:sha-${{ github.sha }}
          format: 'sarif'
          output: 'trivy-results.sarif'
          severity: 'CRITICAL,HIGH,MEDIUM'

      - name: Upload to GitHub Security
        uses: github/codeql-action/upload-sarif@v3
        if: always()
        with:
          sarif_file: 'trivy-results.sarif'
```

The key distinction: the first Trivy step uses `exit-code: '1'` with only `CRITICAL` severity — this is the gate. The second step scans for all severities and uploads to GitHub's Security tab for visibility, but doesn't block the merge.

</details>

---

## Capstone Connection

**DeployForge** needs a CI pipeline that automatically validates every change:

- **`.github/workflows/ci.yml`** — The main CI pipeline: lint TypeScript and Dockerfiles, run the full test suite against a service container (Postgres), build multi-arch images, scan with Trivy, and push to GHCR. Every PR gets a `pr-<number>` tag; every merge to `main` gets `sha-<hash>` and `latest`.
- **Image tagging** — DeployForge uses `sha-<hash>` for all deployments (ArgoCD will reference this exact tag). Semver tags (`v1.0.0`) are added on release for external consumers.
- **Reusable workflows** — If DeployForge grows to multiple services (API, worker, gateway), extract a `build-service.yml` reusable workflow to keep CI DRY.
- **Security gates** — Trivy scans block any merge with CRITICAL CVEs. Results are visible in GitHub's Security tab. Branch protection rules make CI status checks mandatory.
- **Caching** — Both npm dependency caching and Docker layer caching (`type=gha,mode=max`) keep pipeline runs under 2 minutes for cached builds.
