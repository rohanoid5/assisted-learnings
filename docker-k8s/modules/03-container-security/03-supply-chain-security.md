# 3.3 — Supply Chain Security & Image Signing

## Concept

You've hardened your images (3.1) and locked down their runtime (3.2). But how do you know the image running in production is the same one your CI pipeline built? How do you know your base image wasn't tampered with? How do you know *every dependency* in that image is what you think it is?

Software supply chain security answers these questions. High-profile attacks (SolarWinds, Codecov, event-stream, ua-parser-js) proved that attackers increasingly target the *build and delivery pipeline* rather than the application itself. A compromised base image, a poisoned npm package, or a man-in-the-middle registry swap can inject malicious code into thousands of deployments.

This topic covers image signing and verification (cosign/Sigstore), SBOM generation, provenance attestation (SLSA), and the workflows that ensure only trusted, verified artifacts reach production.

---

## Deep Dive

### Anatomy of a Supply Chain Attack

```
┌──────────────────────────────────────────────────────────────────┐
│                Supply Chain Attack Vectors                        │
│                                                                  │
│  ┌──────────┐    ┌──────────┐    ┌──────────┐    ┌──────────┐   │
│  │  Source   │    │  Build   │    │ Registry │    │  Deploy  │   │
│  │   Code   │    │ Pipeline │    │  (Store) │    │ (Runtime)│   │
│  └────┬─────┘    └────┬─────┘    └────┬─────┘    └────┬─────┘   │
│       │               │               │               │         │
│  ┌────┴─────┐    ┌────┴─────┐    ┌────┴─────┐    ┌────┴─────┐   │
│  │Compromised│   │Tampered  │    │Registry  │    │Image     │   │
│  │dependency │   │build env │    │poisoning │    │swap at   │   │
│  │(npm, pip) │   │(CI creds)│    │(tag over-│    │deploy    │   │
│  │           │   │          │    │ write)   │    │time      │   │
│  └──────────┘    └──────────┘    └──────────┘    └──────────┘   │
│                                                                  │
│  Real-world examples:                                            │
│  • SolarWinds (2020) — build pipeline compromised                │
│  • Codecov (2021) — CI script tampered, stole env vars          │
│  • event-stream (2018) — npm package hijacked                    │
│  • ua-parser-js (2021) — npm package supply chain attack         │
│  • PyTorch nightly (2022) — dependency confusion attack          │
└──────────────────────────────────────────────────────────────────┘
```

Each attack targets a different link in the chain. A comprehensive defense requires securing *every* link.

### Docker Content Trust (DCT) and Notary

Docker Content Trust (DCT) uses The Update Framework (TUF) and Notary to provide image signing built into Docker.

```bash
# Enable Docker Content Trust
export DOCKER_CONTENT_TRUST=1

# Now docker push automatically signs images
docker push myregistry.io/my-app:v1.0
# → Signing and pushing trust metadata

# docker pull verifies signatures
docker pull myregistry.io/my-app:v1.0
# → Pull with content trust enforced

# Unsigned images are rejected
docker pull untrusted-image:latest
# → Error: remote trust data does not exist
```

**DCT limitations:**
- Complex key management (root keys, repository keys, delegation keys)
- No keyless signing option
- Limited ecosystem support
- Notary v1 is effectively deprecated
- Does not support SBOM or provenance attestations

> **Modern alternative:** Most teams are moving from DCT/Notary to **cosign/Sigstore** for its simpler UX, keyless signing, and broader ecosystem support.

### Cosign and Sigstore: Modern Image Signing

Sigstore is an open-source project (backed by the Linux Foundation) that provides free, easy-to-use code signing — including *keyless signing* that ties to your CI identity (GitHub Actions OIDC, GitLab CI, etc.).

**Cosign** is the Sigstore tool for signing container images.

```
┌──────────────────────────────────────────────────────────────────┐
│               Sigstore Architecture                              │
│                                                                  │
│  ┌──────────┐    ┌──────────┐    ┌──────────┐                   │
│  │  Fulcio   │    │  Rekor   │    │  Cosign  │                   │
│  │ (CA for   │    │(Transpar-│    │ (Signing │                   │
│  │  short-   │    │  ency    │    │  Tool)   │                   │
│  │  lived    │    │  Log)    │    │          │                   │
│  │  certs)   │    │          │    │          │                   │
│  └─────┬─────┘    └────┬─────┘    └────┬─────┘                   │
│        │               │               │                         │
│        ▼               ▼               ▼                         │
│  1. Developer/CI proves identity (OIDC token)                    │
│  2. Fulcio issues short-lived certificate                        │
│  3. Cosign signs image with certificate                          │
│  4. Signature + cert logged in Rekor transparency log            │
│  5. Verifier checks signature + Rekor log entry                  │
└──────────────────────────────────────────────────────────────────┘
```

```bash
# Install cosign (macOS)
brew install cosign

# Install cosign (Linux)
curl -sSL https://github.com/sigstore/cosign/releases/latest/download/cosign-linux-amd64 \
  -o /usr/local/bin/cosign && chmod +x /usr/local/bin/cosign
```

#### Key-Pair Signing (Self-Managed Keys)

```bash
# Generate a key pair
cosign generate-key-pair
# → Enter password for private key:
# → Created cosign.key (private) and cosign.pub (public)

# Sign an image
cosign sign --key cosign.key myregistry.io/deployforge-api:v1.0
# → Pushing signature to: myregistry.io/deployforge-api:sha256-abc123.sig

# Verify a signature
cosign verify --key cosign.pub myregistry.io/deployforge-api:v1.0
# → Verified OK

# Verification fails for unsigned/tampered images
cosign verify --key cosign.pub myregistry.io/some-unsigned-image:latest
# → Error: no matching signatures
```

#### Keyless Signing (CI/CD — Recommended)

Keyless signing uses OIDC identity from your CI provider. No keys to manage, rotate, or protect.

```yaml
# .github/workflows/sign-image.yml
name: Build, Sign, and Push

on:
  push:
    tags: ['v*']

permissions:
  contents: read
  packages: write
  id-token: write  # Required for keyless signing

jobs:
  build-sign-push:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Install cosign
        uses: sigstore/cosign-installer@v3

      - name: Log in to GHCR
        uses: docker/login-action@v3
        with:
          registry: ghcr.io
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}

      - name: Build and push
        id: build
        uses: docker/build-push-action@v5
        with:
          push: true
          tags: ghcr.io/${{ github.repository }}:${{ github.ref_name }}

      - name: Sign the image (keyless)
        run: |
          cosign sign --yes \
            ghcr.io/${{ github.repository }}@${{ steps.build.outputs.digest }}
        # --yes skips the interactive prompt
        # Signing uses GitHub Actions OIDC identity — no keys needed
```

```bash
# Verify a keyless-signed image
cosign verify \
  --certificate-identity=https://github.com/deployforge/api/.github/workflows/sign-image.yml@refs/tags/v1.0 \
  --certificate-oidc-issuer=https://token.actions.githubusercontent.com \
  ghcr.io/deployforge/api:v1.0
```

### SBOM Generation

A Software Bill of Materials (SBOM) is a complete inventory of every component in your image — OS packages, language packages, licenses, and versions. SBOMs are critical for:
- Knowing if you're affected by a new CVE (faster than re-scanning)
- License compliance
- Regulatory requirements (US Executive Order 14028)

```bash
# Install Syft (SBOM generator)
brew install syft

# Generate an SBOM for an image
syft myregistry.io/deployforge-api:v1.0 -o spdx-json > sbom.spdx.json

# Other formats
syft myregistry.io/deployforge-api:v1.0 -o cyclonedx-json > sbom.cdx.json
syft myregistry.io/deployforge-api:v1.0 -o syft-json > sbom.syft.json

# Docker's built-in SBOM (uses Syft under the hood)
docker sbom myregistry.io/deployforge-api:v1.0

# Attach SBOM to image as an attestation (with cosign)
cosign attest --predicate sbom.spdx.json \
  --type spdxjson \
  --key cosign.key \
  myregistry.io/deployforge-api:v1.0

# Verify and retrieve the SBOM attestation
cosign verify-attestation \
  --key cosign.pub \
  --type spdxjson \
  myregistry.io/deployforge-api:v1.0 | jq '.payload' | base64 -d | jq .
```

**SBOM output example (abridged):**

```json
{
  "spdxVersion": "SPDX-2.3",
  "name": "deployforge-api:v1.0",
  "packages": [
    {
      "name": "express",
      "versionInfo": "4.18.2",
      "supplier": "Organization: expressjs",
      "licenseDeclared": "MIT"
    },
    {
      "name": "openssl",
      "versionInfo": "3.1.4-r2",
      "supplier": "Organization: Alpine",
      "licenseDeclared": "Apache-2.0"
    }
  ]
}
```

### Provenance Attestation and SLSA

SLSA (Supply-chain Levels for Software Artifacts — pronounced "salsa") is a framework for ensuring the integrity of software artifacts. It defines four levels of increasing assurance.

| Level | Requirement | What It Proves |
|-------|------------|---------------|
| SLSA 1 | Build process documented | "We know how this was built" |
| SLSA 2 | Hosted build, signed provenance | "A specific CI system built this" |
| SLSA 3 | Hardened build, non-forgeable provenance | "The build couldn't be tampered with" |
| SLSA 4 | Two-party review, hermetic build | "Multiple people reviewed, build is fully reproducible" |

```yaml
# GitHub Actions: generate SLSA provenance automatically
# Uses the official SLSA generator

name: Build with Provenance

on:
  push:
    tags: ['v*']

jobs:
  build:
    runs-on: ubuntu-latest
    permissions:
      contents: read
      packages: write
      id-token: write
    outputs:
      digest: ${{ steps.build.outputs.digest }}
    steps:
      - uses: actions/checkout@v4
      - name: Build and push
        id: build
        uses: docker/build-push-action@v5
        with:
          push: true
          tags: ghcr.io/${{ github.repository }}:${{ github.ref_name }}

  provenance:
    needs: build
    permissions:
      actions: read
      id-token: write
      packages: write
    uses: slsa-framework/slsa-github-generator/.github/workflows/generator_container_slsa3.yml@v2.0.0
    with:
      image: ghcr.io/${{ github.repository }}
      digest: ${{ needs.build.outputs.digest }}
    secrets:
      registry-username: ${{ github.actor }}
      registry-password: ${{ secrets.GITHUB_TOKEN }}
```

### Private Registries and Access Control

```
┌──────────────────────────────────────────────────────────────────┐
│              Image Promotion Pipeline                             │
│                                                                  │
│  ┌──────────┐    ┌──────────┐    ┌──────────┐                   │
│  │   Dev    │───▶│ Staging  │───▶│   Prod   │                   │
│  │ Registry │    │ Registry │    │ Registry │                   │
│  │          │    │          │    │          │                   │
│  │ :latest  │    │ :rc-123  │    │ :v1.2.3  │                   │
│  │ :sha-abc │    │ :sha-abc │    │ :sha-abc │                   │
│  └──────────┘    └──────────┘    └──────────┘                   │
│                                                                  │
│  Gates:                                                          │
│  • Dev→Staging:  Trivy scan passes, Hadolint clean              │
│  • Staging→Prod: E2E tests pass, signature verified, manual OK  │
│                                                                  │
│  Each promotion copies the image (same digest) — no rebuild.     │
└──────────────────────────────────────────────────────────────────┘
```

**Best practices for registry access control:**
- Use separate registries (or repositories) for dev, staging, and prod
- Prod registry allows only pulls from deployment service accounts — no human push access
- Image promotion copies by digest (same image, different tag)
- All pushes require authentication; anonymous pulls are disabled
- Registry audit logs are shipped to SIEM

### Dependency Scanning and Automated Updates

Base images and application dependencies go stale. Automated update tools keep you current:

```yaml
# renovate.json — Renovate configuration for Docker digests
{
  "$schema": "https://docs.renovatebot.com/renovate-schema.json",
  "extends": ["config:base"],
  "packageRules": [
    {
      "matchDatasources": ["docker"],
      "matchUpdateTypes": ["digest"],
      "automerge": true,
      "schedule": ["before 6am on Monday"]
    },
    {
      "matchDatasources": ["docker"],
      "matchUpdateTypes": ["major"],
      "automerge": false,
      "labels": ["breaking-change"]
    }
  ],
  "docker-compose": {
    "fileMatch": ["docker-compose[^/]*\\.ya?ml$"]
  }
}
```

```yaml
# .github/dependabot.yml — GitHub Dependabot for Docker
version: 2
updates:
  - package-ecosystem: "docker"
    directory: "/docker"
    schedule:
      interval: "weekly"
    reviewers:
      - "platform-team"
    labels:
      - "dependencies"
      - "docker"
    commit-message:
      prefix: "chore(docker)"
```

---

## Code Examples

### Example 1: Complete Image Signing Script

```bash
#!/usr/bin/env bash
# scripts/sign-image.sh — Sign a Docker image with cosign
set -euo pipefail

IMAGE="${1:?Usage: sign-image.sh <image:tag>}"
KEY="${COSIGN_KEY:-cosign.key}"

echo "=== Signing image: ${IMAGE} ==="

# Get the image digest
DIGEST=$(docker inspect --format='{{index .RepoDigests 0}}' "${IMAGE}" 2>/dev/null || \
         docker inspect --format='{{.Id}}' "${IMAGE}")

echo "Digest: ${DIGEST}"

# Sign with cosign
cosign sign --key "${KEY}" "${DIGEST}"
echo "✅ Image signed."

# Generate and attach SBOM
echo ""
echo "=== Generating SBOM ==="
syft "${IMAGE}" -o spdx-json > sbom.spdx.json
echo "SBOM generated: sbom.spdx.json"

# Attach SBOM as attestation
cosign attest --predicate sbom.spdx.json \
  --type spdxjson \
  --key "${KEY}" \
  "${DIGEST}"
echo "✅ SBOM attestation attached."

# Verify
echo ""
echo "=== Verification ==="
cosign verify --key cosign.pub "${DIGEST}"
echo "✅ Signature verified."

cosign verify-attestation --key cosign.pub --type spdxjson "${DIGEST}" > /dev/null 2>&1
echo "✅ SBOM attestation verified."

echo ""
echo "Image is signed, has an SBOM, and is ready for deployment."
```

### Example 2: Verification in Deployment Script

```bash
#!/usr/bin/env bash
# scripts/verify-before-deploy.sh — Verify image signature before deploying
set -euo pipefail

IMAGE="${1:?Usage: verify-before-deploy.sh <image:tag>}"
PUBLIC_KEY="${COSIGN_PUBLIC_KEY:-cosign.pub}"

echo "=== Pre-deployment verification: ${IMAGE} ==="

# Step 1: Verify signature
echo "[1/3] Verifying image signature..."
if ! cosign verify --key "${PUBLIC_KEY}" "${IMAGE}" 2>/dev/null; then
  echo "❌ FATAL: Image signature verification failed. Aborting deployment."
  echo "   This image was not signed by our CI pipeline."
  exit 1
fi
echo "✅ Signature valid."

# Step 2: Verify SBOM attestation exists
echo "[2/3] Verifying SBOM attestation..."
if ! cosign verify-attestation --key "${PUBLIC_KEY}" --type spdxjson "${IMAGE}" > /dev/null 2>&1; then
  echo "⚠️  WARNING: No SBOM attestation found. Proceeding with caution."
else
  echo "✅ SBOM attestation verified."
fi

# Step 3: Final vulnerability scan (belt and suspenders)
echo "[3/3] Running vulnerability scan..."
if ! trivy image --severity CRITICAL --exit-code 1 --no-progress "${IMAGE}"; then
  echo "❌ FATAL: Critical vulnerabilities found. Aborting deployment."
  exit 1
fi
echo "✅ No critical vulnerabilities."

echo ""
echo "=== All checks passed. Proceeding with deployment. ==="
```

### Example 3: Kubernetes Admission Controller (Preview)

In Module 07, you'll use a Kubernetes admission controller to enforce signature verification. Here's a preview:

```yaml
# Kyverno policy — reject unsigned images
# (Full implementation in Module 07)
apiVersion: kyverno.io/v1
kind: ClusterPolicy
metadata:
  name: verify-image-signature
spec:
  validationFailureAction: Enforce
  rules:
    - name: check-image-signature
      match:
        any:
          - resources:
              kinds:
                - Pod
      verifyImages:
        - imageReferences:
            - "ghcr.io/deployforge/*"
          attestors:
            - entries:
                - keyless:
                    issuer: "https://token.actions.githubusercontent.com"
                    subject: "https://github.com/deployforge/*/.github/workflows/*"
```

---

## Try It Yourself

### Challenge 1: Sign and Verify a Local Image

1. Install cosign
2. Generate a key pair
3. Build a local image, push it to a local registry, sign it, and verify the signature

<details>
<summary>Show solution</summary>

```bash
# Start a local registry
docker run -d -p 5000:5000 --name registry registry:2

# Build and push an image
docker build -t localhost:5000/test-app:v1 .
docker push localhost:5000/test-app:v1

# Generate cosign keys
cosign generate-key-pair
# → Enter password (or leave blank for testing)
# → Creates cosign.key and cosign.pub

# Sign the image
cosign sign --key cosign.key \
  --allow-insecure-registry \
  localhost:5000/test-app:v1
# → Signing with key...
# → Pushing signature...

# Verify the signature
cosign verify --key cosign.pub \
  --allow-insecure-registry \
  localhost:5000/test-app:v1
# → Verification for localhost:5000/test-app:v1 —
# → The following checks were performed:
# → - The cosign claims were validated
# → - The signatures were verified against the specified public key

# Try verifying with a wrong key
cosign generate-key-pair --output-key-prefix wrong
cosign verify --key wrong-cosign.pub \
  --allow-insecure-registry \
  localhost:5000/test-app:v1
# → Error: no matching signatures — GOOD, wrong key rejected

# Clean up
docker stop registry && docker rm registry
```

</details>

### Challenge 2: Generate an SBOM

Generate an SBOM for `node:20-alpine` and answer: How many packages are in the image? What licenses are used?

<details>
<summary>Show solution</summary>

```bash
# Install Syft
brew install syft

# Generate SBOM
syft node:20-alpine -o syft-json > node-sbom.json

# Count packages
cat node-sbom.json | jq '.artifacts | length'
# → ~40-50 packages (alpine packages + node built-in modules)

# List all packages with versions
cat node-sbom.json | jq -r '.artifacts[] | "\(.name) \(.version) [\(.licenses[]?.value // "unknown")]"' | sort

# Count unique licenses
cat node-sbom.json | jq -r '.artifacts[].licenses[]?.value // "unknown"' | sort | uniq -c | sort -rn
# Typical output:
#   15 MIT
#    8 BSD-2-Clause
#    5 ISC
#    3 Apache-2.0
#    2 GPL-2.0-only
#    ...

# Find packages with GPL licenses (might need legal review)
cat node-sbom.json | jq -r '.artifacts[] | select(.licenses[]?.value | test("GPL")) | .name'

# Compare with a larger image
syft node:20 -o syft-json > node-full-sbom.json
cat node-full-sbom.json | jq '.artifacts | length'
# → ~400+ packages — 10x more than alpine
```

</details>

### Challenge 3: Simulate a Supply Chain Attack

Demonstrate what happens when an unsigned or incorrectly signed image is deployed:

<details>
<summary>Show solution</summary>

```bash
# Setup: sign a legitimate image
docker run -d -p 5000:5000 --name registry registry:2
docker pull alpine:3.19
docker tag alpine:3.19 localhost:5000/my-app:v1
docker push localhost:5000/my-app:v1

cosign generate-key-pair
cosign sign --key cosign.key --allow-insecure-registry localhost:5000/my-app:v1

# Scenario 1: Attacker pushes a different image with the same tag
docker pull alpine:3.18  # Different version
docker tag alpine:3.18 localhost:5000/my-app:v1
docker push localhost:5000/my-app:v1  # Overwrites the tag

# Verification catches the swap
cosign verify --key cosign.pub --allow-insecure-registry localhost:5000/my-app:v1
# → Error: no matching signatures
# The signature was for the original digest. The new image has a different
# digest, so the signature doesn't match. Attack detected!

# Scenario 2: Pull by digest instead of tag (even better)
# Get the digest of the original signed image
DIGEST="localhost:5000/my-app@sha256:<original-digest>"
docker pull "${DIGEST}"  # Always gets the original image, regardless of tag

# Lesson: Digest pinning + signature verification = defense in depth
# - Digest pinning prevents tag mutation attacks
# - Signature verification proves who built the image
# - Together, they're nearly impossible to circumvent

# Clean up
docker stop registry && docker rm registry
```

</details>

---

## Capstone Connection

**DeployForge uses cosign to sign images in CI and verifies signatures before deployment in the K8s admission controller (Module 07).** Here's how this topic connects to the capstone:

- **Keyless signing in CI** — GitHub Actions workflows use cosign with OIDC identity. No private keys are stored as secrets — Sigstore's Fulcio issues short-lived certificates tied to the workflow identity. The Rekor transparency log provides a tamper-evident audit trail of every signature.

- **SBOM generation and attestation** — Every DeployForge image has an SBOM (SPDX format) generated by Syft and attached as a cosign attestation. When a new CVE drops, the team can immediately check which images are affected by querying the SBOM — no need to re-scan every image.

- **Verification before deployment** — The `scripts/verify-before-deploy.sh` script runs three checks before any deployment: signature verification, SBOM attestation, and a final Trivy scan for critical CVEs. This script is the last gate before `kubectl apply`.

- **Kubernetes admission controller (Module 07)** — A Kyverno `ClusterPolicy` enforces that only images signed by the DeployForge GitHub Actions workflow can run in the production namespace. Unsigned or incorrectly signed images are rejected at admission time — they never start running.

- **Image promotion pipeline** — Images flow from `dev` → `staging` → `prod` registries. Each promotion copies the image by digest (not tag) and adds a promotion attestation. The prod registry only accepts images that have passed all gates.

- **Automated dependency updates** — Renovate monitors all Dockerfiles and `docker-compose.yml` files for base image digest updates. When a new Node.js patch is released, Renovate opens a PR with the updated digest. The PR triggers the full security pipeline (scan → build → sign), and if everything passes, it merges automatically.

This completes the container security module. You now have a defense-in-depth strategy covering image hardening (3.1), runtime confinement (3.2), and supply chain verification (3.3). In Module 04, you'll apply these patterns in Kubernetes, where security policies are enforced at the cluster level.
