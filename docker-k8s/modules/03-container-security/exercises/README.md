# Module 03 — Exercises

Hands-on practice with image scanning, runtime hardening, supply chain security, and security auditing. These exercises build toward the DeployForge capstone.

> **Prerequisites:** Docker 24+ installed and running. Trivy, Hadolint, and cosign installed. Familiarity with Module 03 concept files (01–03).

---

## Exercise 1: Vulnerability Scan & Fix

**Goal:** Scan a deliberately vulnerable image, identify critical CVEs, rebuild with fixes, and verify a clean scan.

### Setup

Create a project with an intentionally vulnerable Dockerfile:

```bash
mkdir -p vuln-exercise/src
cd vuln-exercise

cat > package.json << 'EOF'
{
  "name": "vuln-exercise",
  "version": "1.0.0",
  "scripts": { "start": "node src/server.js" },
  "dependencies": { "express": "^4.18.2" }
}
EOF

cat > src/server.js << 'EOF'
const express = require("express");
const app = express();
app.get("/health", (req, res) => res.json({ status: "ok" }));
app.listen(3000, () => console.log("Listening on :3000"));
EOF
```

### The Vulnerable Dockerfile

```dockerfile
# Dockerfile.vulnerable — intentionally bad
FROM node:18.0.0
WORKDIR /app
COPY . .
RUN npm install
EXPOSE 3000
CMD npm start
```

> **Why `node:18.0.0`?** This is an old, unpatched Node.js version with known CVEs. It also uses the full Debian image (~1GB) with hundreds of OS-level vulnerabilities.

### Steps

1. Build the vulnerable image:
   ```bash
   docker build -t vuln-exercise:bad -f Dockerfile.vulnerable .
   ```

2. Scan with Trivy and record the output:
   ```bash
   trivy image --severity CRITICAL,HIGH vuln-exercise:bad
   ```

3. Answer these questions:
   - How many CRITICAL CVEs? How many HIGH?
   - What are the top 3 vulnerable packages?
   - Which CVEs have fixes available?

4. Create a `Dockerfile.fixed` that:
   - Uses `node:20-alpine` (patched base)
   - Separates dependency install from code copy (layer caching)
   - Uses `npm ci --only=production`
   - Runs as a non-root user
   - Has a health check

5. Build and scan the fixed image:
   ```bash
   docker build -t vuln-exercise:fixed -f Dockerfile.fixed .
   trivy image --severity CRITICAL,HIGH vuln-exercise:fixed
   ```

6. Compare image sizes and CVE counts.

### Verification

```bash
# The fixed image should have:
# - Zero CRITICAL CVEs (or close to zero)
# - Dramatically fewer HIGH CVEs
# - Image size under 200MB (vs ~1GB)
docker images vuln-exercise --format "table {{.Tag}}\t{{.Size}}"

# Verify it still works
docker run --rm -d -p 3000:3000 --name vuln-test vuln-exercise:fixed
curl http://localhost:3000/health
docker stop vuln-test
```

<details>
<summary>Show solution</summary>

**Dockerfile.fixed:**

```dockerfile
FROM node:20-alpine

WORKDIR /app

RUN addgroup --system --gid 1001 appgroup && \
    adduser --system --uid 1001 --ingroup appgroup appuser

COPY package.json package-lock.json ./
RUN npm ci --only=production && npm cache clean --force

COPY --chown=appuser:appgroup src/ ./src/

USER appuser

EXPOSE 3000

HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
  CMD node -e "fetch('http://localhost:3000/health').then(r=>{if(!r.ok)throw r.status})" || exit 1

CMD ["node", "src/server.js"]
```

**Expected results:**

```bash
docker images vuln-exercise --format "table {{.Tag}}\t{{.Size}}"
# TAG     SIZE
# bad     ~1.1GB
# fixed   ~180MB

trivy image --severity CRITICAL,HIGH vuln-exercise:bad 2>&1 | grep "Total:"
# Total: 50+ (CRITICAL: 5-15, HIGH: 20-40)

trivy image --severity CRITICAL,HIGH vuln-exercise:fixed 2>&1 | grep "Total:"
# Total: 0-3 (CRITICAL: 0, HIGH: 0-3)
```

The alpine base image has ~40 packages vs ~400+ in the Debian base. Combined with a newer Node.js version, this eliminates the vast majority of CVEs.

</details>

---

## Exercise 2: Harden a Running Container

**Goal:** Take a container running with default (insecure) settings and re-configure it with full runtime hardening.

### The Insecure Container

```bash
# Run with all defaults — root, full capabilities, writable rootfs
docker run -d --name insecure-app \
  -p 3000:3000 \
  node:20-alpine \
  sh -c 'node -e "require(\"http\").createServer((q,s)=>{s.end(\"ok\")}).listen(3000)" '

# Audit the insecure container
echo "User: $(docker exec insecure-app whoami)"
echo "Caps: $(docker inspect --format='{{.HostConfig.CapDrop}}' insecure-app)"
echo "ReadOnly: $(docker inspect --format='{{.HostConfig.ReadonlyRootfs}}' insecure-app)"
echo "SecOpt: $(docker inspect --format='{{.HostConfig.SecurityOpt}}' insecure-app)"
```

### Steps

1. Verify the insecure container's security posture (running as root, full capabilities, writable rootfs).

2. Stop and remove the insecure container.

3. Re-run the same application with all of these hardening measures:
   - `--cap-drop=ALL`
   - `--read-only` with tmpfs for `/tmp`
   - `--security-opt no-new-privileges:true`
   - `--user 1001:1001`
   - `--memory=128m` and `--cpus=0.5`
   - `--pids-limit=50`

4. Verify the hardened container still serves traffic.

5. Verify each security setting with `docker inspect`.

### Verification

```bash
# The hardened container should:
curl http://localhost:3000  # → "ok"

# Verify security posture
docker inspect hardened-app --format='
  User:        {{.Config.User}}
  ReadOnly:    {{.HostConfig.ReadonlyRootfs}}
  CapDrop:     {{.HostConfig.CapDrop}}
  Memory:      {{.HostConfig.Memory}}
  PidsLimit:   {{.HostConfig.PidsLimit}}
  SecurityOpt: {{.HostConfig.SecurityOpt}}
'
```

<details>
<summary>Show solution</summary>

```bash
# Stop the insecure container
docker stop insecure-app && docker rm insecure-app

# Run the hardened version
docker run -d --name hardened-app \
  -p 3000:3000 \
  --cap-drop=ALL \
  --read-only \
  --tmpfs /tmp:rw,noexec,nosuid,size=64m \
  --security-opt no-new-privileges:true \
  --user 1001:1001 \
  --memory=128m \
  --cpus=0.5 \
  --pids-limit=50 \
  node:20-alpine \
  sh -c 'node -e "require(\"http\").createServer((q,s)=>{s.end(\"ok\")}).listen(3000)"'

# Verify it works
curl http://localhost:3000
# → ok

# Full security audit
docker inspect hardened-app --format='
  User:        {{.Config.User}}
  ReadOnly:    {{.HostConfig.ReadonlyRootfs}}
  CapDrop:     {{.HostConfig.CapDrop}}
  CapAdd:      {{.HostConfig.CapAdd}}
  Memory:      {{.HostConfig.Memory}}
  PidsLimit:   {{.HostConfig.PidsLimit}}
  SecurityOpt: {{.HostConfig.SecurityOpt}}
  Privileged:  {{.HostConfig.Privileged}}
'
# User:        1001:1001
# ReadOnly:    true
# CapDrop:     [ALL]
# CapAdd:      []
# Memory:      134217728  (128MB)
# PidsLimit:   50
# SecurityOpt: [no-new-privileges:true]
# Privileged:  false

# Verify writes to root filesystem are blocked
docker exec hardened-app touch /test 2>&1
# → touch: /test: Read-only file system ← GOOD

# Verify /tmp is writable (tmpfs)
docker exec hardened-app touch /tmp/test
# → (succeeds)

# Clean up
docker stop hardened-app && docker rm hardened-app
```

</details>

---

## Exercise 3: Image Signing Pipeline

**Goal:** Set up cosign, generate a keypair, sign a local image, verify the signature, and demonstrate what happens when verification fails.

### Steps

1. Install cosign if not already installed:
   ```bash
   brew install cosign   # macOS
   # or
   go install github.com/sigstore/cosign/v2/cmd/cosign@latest
   ```

2. Start a local Docker registry:
   ```bash
   docker run -d -p 5000:5000 --name local-registry registry:2
   ```

3. Build, tag, and push a test image:
   ```bash
   docker pull alpine:3.19
   docker tag alpine:3.19 localhost:5000/test-app:v1
   docker push localhost:5000/test-app:v1
   ```

4. Generate a cosign key pair:
   ```bash
   cosign generate-key-pair
   ```

5. Sign the image:
   ```bash
   cosign sign --key cosign.key --allow-insecure-registry localhost:5000/test-app:v1
   ```

6. Verify the signature:
   ```bash
   cosign verify --key cosign.pub --allow-insecure-registry localhost:5000/test-app:v1
   ```

7. Demonstrate a failed verification:
   - Generate a second key pair
   - Try to verify with the wrong public key
   - Overwrite the tag with a different image and verify again

### Verification

```bash
# Successful verification should output the claims and "Verified OK"
# Failed verification should output "Error: no matching signatures"
```

<details>
<summary>Show solution</summary>

```bash
# Step 1-3: Setup
docker run -d -p 5000:5000 --name local-registry registry:2
docker pull alpine:3.19
docker tag alpine:3.19 localhost:5000/test-app:v1
docker push localhost:5000/test-app:v1

# Step 4: Generate keys
cosign generate-key-pair
# Enter password or leave blank for testing

# Step 5: Sign
cosign sign --key cosign.key \
  --allow-insecure-registry \
  localhost:5000/test-app:v1
# → Pushing signature to: localhost:5000/test-app:sha256-...

# Step 6: Verify — SUCCESS
cosign verify --key cosign.pub \
  --allow-insecure-registry \
  localhost:5000/test-app:v1
# → Verification for localhost:5000/test-app:v1 --
# → The following checks were performed:
# → - The cosign claims were validated
# → - The signatures were verified against the specified public key

# Step 7a: Wrong key — FAIL
COSIGN_PASSWORD="" cosign generate-key-pair --output-key-prefix attacker
cosign verify --key attacker-cosign.pub \
  --allow-insecure-registry \
  localhost:5000/test-app:v1
# → Error: no matching signatures

# Step 7b: Tag overwrite — FAIL
docker pull alpine:3.18
docker tag alpine:3.18 localhost:5000/test-app:v1
docker push localhost:5000/test-app:v1   # Overwrites the tag with a different image

cosign verify --key cosign.pub \
  --allow-insecure-registry \
  localhost:5000/test-app:v1
# → Error: no matching signatures
# The signature was for the ORIGINAL image digest. The new image has
# a different digest, so the signature doesn't match. Attack detected!

# Clean up
docker stop local-registry && docker rm local-registry
rm -f cosign.key cosign.pub attacker-cosign.key attacker-cosign.pub
```

</details>

---

## Exercise 4: Security Audit

**Goal:** Run Docker Bench Security against your local Docker daemon, interpret the results, and fix the top 5 findings.

### Steps

1. Run Docker Bench Security:
   ```bash
   docker run --rm --net host --pid host \
     --userns host --cap-add audit_control \
     -e DOCKER_CONTENT_TRUST=$DOCKER_CONTENT_TRUST \
     -v /etc:/etc:ro \
     -v /var/lib:/var/lib:ro \
     -v /var/run/docker.sock:/var/run/docker.sock:ro \
     docker/docker-bench-security
   ```

2. Review the output — it's organized into sections:
   - **[INFO]** — Informational (no action needed)
   - **[PASS]** — Check passed
   - **[WARN]** — Check failed, needs attention
   - **[NOTE]** — Manual check recommended

3. For each section, count the PASS vs WARN items.

4. Pick the top 5 WARN items from section 4 (Container Runtime) and section 5 (Container Security) and fix them.

5. Re-run Docker Bench and verify the warnings are resolved.

### Common Findings and Fixes

| Finding | Fix |
|---------|-----|
| 4.1 — Container running as root | Add `USER` instruction or `--user` flag |
| 4.5 — Content trust not enabled | `export DOCKER_CONTENT_TRUST=1` |
| 4.6 — HEALTHCHECK not set | Add `HEALTHCHECK` instruction |
| 5.2 — Container with writable rootfs | Use `--read-only` |
| 5.10 — Memory not limited | Use `--memory` flag |
| 5.12 — No CPU limit | Use `--cpus` flag |
| 5.25 — Container not restricted from acquiring additional privileges | Use `--security-opt no-new-privileges:true` |
| 5.28 — PID limit not set | Use `--pids-limit` flag |

<details>
<summary>Show solution</summary>

```bash
# Run the benchmark
docker run --rm --net host --pid host \
  --userns host --cap-add audit_control \
  -e DOCKER_CONTENT_TRUST=$DOCKER_CONTENT_TRUST \
  -v /etc:/etc:ro \
  -v /var/lib:/var/lib:ro \
  -v /var/run/docker.sock:/var/run/docker.sock:ro \
  docker/docker-bench-security 2>&1 | tee bench-results.txt

# Count findings by type
grep -c "\[PASS\]" bench-results.txt   # → Passed checks
grep -c "\[WARN\]" bench-results.txt   # → Failed checks
grep -c "\[INFO\]" bench-results.txt   # → Informational

# Extract all WARN items
grep "\[WARN\]" bench-results.txt

# Fix top 5 findings by running a hardened container:
docker run -d --name bench-fixed \
  -p 3000:3000 \
  --user 1001:1001 \
  --read-only \
  --tmpfs /tmp:rw,noexec,nosuid,size=64m \
  --memory=256m \
  --cpus=1.0 \
  --pids-limit=100 \
  --cap-drop=ALL \
  --security-opt no-new-privileges:true \
  --health-cmd="node -e \"fetch('http://localhost:3000/health').then(r=>{if(!r.ok)throw r.status})\"" \
  --health-interval=30s \
  --health-timeout=5s \
  --health-retries=3 \
  node:20-alpine \
  node -e "require('http').createServer((q,s)=>{s.end('ok')}).listen(3000)"

# Re-run benchmark — the hardened container should have fewer warnings
docker run --rm --net host --pid host \
  --userns host --cap-add audit_control \
  -v /etc:/etc:ro \
  -v /var/lib:/var/lib:ro \
  -v /var/run/docker.sock:/var/run/docker.sock:ro \
  docker/docker-bench-security 2>&1 | grep "\[WARN\]" | wc -l

# Clean up
docker stop bench-fixed && docker rm bench-fixed
rm bench-results.txt
```

**Key takeaway:** Docker Bench Security is an excellent baseline tool, but don't aim for 100% PASS — some warnings are host-level (Docker daemon configuration) and some are not applicable to your environment. Focus on Container Runtime (section 4) and Container Security (section 5) findings first.

</details>

---

## Capstone Checkpoint

Before moving to Module 04, verify your DeployForge containers meet this security baseline:

```bash
# 1. Image scan: zero critical/high CVEs
trivy image --severity CRITICAL,HIGH --exit-code 1 deployforge-api:latest

# 2. Hadolint: zero warnings on Dockerfiles
hadolint docker/api-gateway.Dockerfile

# 3. Non-root execution
docker run --rm deployforge-api:latest whoami
# → appuser (NOT root)

# 4. Capabilities dropped
docker inspect deployforge-api-container --format='{{.HostConfig.CapDrop}}'
# → [ALL]

# 5. Read-only root filesystem
docker inspect deployforge-api-container --format='{{.HostConfig.ReadonlyRootfs}}'
# → true

# 6. No-new-privileges
docker inspect deployforge-api-container --format='{{.HostConfig.SecurityOpt}}'
# → [no-new-privileges:true]
```

If any check fails, revisit the relevant concept file:
- CVE scan fails → [01-image-scanning-and-hardening.md](../01-image-scanning-and-hardening.md)
- Runtime hardening missing → [02-runtime-security.md](../02-runtime-security.md)
- No signing pipeline → [03-supply-chain-security.md](../03-supply-chain-security.md)
