# 1.2 — OCI Images & Container Runtimes

## Concept

"Docker images" are actually **OCI images** — an open standard that any compliant tool can build, push, pull, and run. Understanding the OCI specifications lets you work with containers beyond Docker: building with Buildah, running with Podman, storing in any registry, and orchestrating with Kubernetes.

There are two specs:
- **OCI Image Spec** — defines how container images are structured (layers, manifests, config)
- **OCI Runtime Spec** — defines how a container is created and run (lifecycle, hooks, filesystem bundle)

---

## Deep Dive

### OCI Image Specification

An OCI image is a content-addressable, layered filesystem bundle with metadata. It consists of three parts:

#### 1. Image Manifest

The manifest is the entry point — it ties everything together:

```json
{
  "schemaVersion": 2,
  "mediaType": "application/vnd.oci.image.manifest.v1+json",
  "config": {
    "mediaType": "application/vnd.oci.image.config.v1+json",
    "digest": "sha256:a1b2c3...",
    "size": 7023
  },
  "layers": [
    {
      "mediaType": "application/vnd.oci.image.layer.v1.tar+gzip",
      "digest": "sha256:d4e5f6...",
      "size": 32654947
    },
    {
      "mediaType": "application/vnd.oci.image.layer.v1.tar+gzip",
      "digest": "sha256:g7h8i9...",
      "size": 16724
    }
  ]
}
```

#### 2. Image Configuration

The config blob defines runtime defaults and layer history:

```json
{
  "architecture": "amd64",
  "os": "linux",
  "config": {
    "Env": ["PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin"],
    "Cmd": ["/bin/sh"],
    "WorkingDir": "/",
    "ExposedPorts": { "80/tcp": {} }
  },
  "rootfs": {
    "type": "layers",
    "diff_ids": [
      "sha256:abc123...",
      "sha256:def456..."
    ]
  },
  "history": [
    { "created": "2024-01-15T10:00:00Z", "created_by": "ADD file:... in /" },
    { "created": "2024-01-15T10:00:01Z", "created_by": "CMD [\"/bin/sh\"]", "empty_layer": true }
  ]
}
```

#### 3. Image Layers

Each layer is a tar archive of filesystem changes (added, modified, or deleted files). Layers stack bottom-up using a **union filesystem**:

```
┌──────────────────────────────────────────────┐
│  Layer 3 (top):  COPY app.js /app/           │  ← Your application code
├──────────────────────────────────────────────┤
│  Layer 2:        RUN apt-get install nodejs   │  ← Dependencies
├──────────────────────────────────────────────┤
│  Layer 1 (base): Ubuntu 22.04 rootfs          │  ← Base OS files
└──────────────────────────────────────────────┘

Union mount presents a single merged view:
/
├── app/
│   └── app.js          (from Layer 3)
├── usr/
│   └── bin/
│       └── node        (from Layer 2)
├── bin/
│   └── bash            (from Layer 1)
└── etc/                (from Layer 1)
```

#### Content-Addressable Storage

Every blob (layer, config, manifest) is identified by its SHA256 digest. This enables:

- **Deduplication** — if two images share the same base layer, it's stored once
- **Integrity verification** — tampered content has a different hash
- **Cache efficiency** — only download layers you don't already have

```bash
# Two images sharing a base layer:
# Image A: [base-layer:sha256:abc] + [app-a:sha256:111]
# Image B: [base-layer:sha256:abc] + [app-b:sha256:222]
# Storage: base-layer stored ONCE, referenced by both manifests
```

#### Image Index (Multi-Platform Images)

A single image tag can point to different images for different architectures:

```json
{
  "schemaVersion": 2,
  "mediaType": "application/vnd.oci.image.index.v1+json",
  "manifests": [
    {
      "mediaType": "application/vnd.oci.image.manifest.v1+json",
      "digest": "sha256:amd64manifest...",
      "platform": { "architecture": "amd64", "os": "linux" }
    },
    {
      "mediaType": "application/vnd.oci.image.manifest.v1+json",
      "digest": "sha256:arm64manifest...",
      "platform": { "architecture": "arm64", "os": "linux" }
    }
  ]
}
```

---

### How `docker pull` Actually Works

```
┌──────────┐     ┌───────────────┐     ┌─────────────────┐
│  Client   │────▶│   Registry    │     │  Local Storage   │
│ (docker)  │     │ (Docker Hub)  │     │ (overlay2 store) │
└──────────┘     └───────────────┘     └─────────────────┘

Step 1: Resolve tag → manifest digest
  GET /v2/library/nginx/manifests/latest
  → Returns manifest (or image index for multi-arch)

Step 2: Download config blob
  GET /v2/library/nginx/blobs/sha256:a1b2c3...
  → Returns image config JSON

Step 3: Download layers (parallel, skip existing)
  GET /v2/library/nginx/blobs/sha256:d4e5f6...  ← Layer 1
  GET /v2/library/nginx/blobs/sha256:g7h8i9...  ← Layer 2
  → Only downloads layers not in local store

Step 4: Verify digests and store
  → Each blob verified against its SHA256 digest
  → Stored in content-addressable storage
```

---

### OCI Runtime Specification

The runtime spec defines how to **run** a container from an OCI image. It specifies:

1. **Filesystem bundle** — a directory with a `config.json` and a `rootfs/`
2. **Container lifecycle** — create → start → (running) → kill → delete
3. **Runtime hooks** — prestart, createRuntime, createContainer, startContainer, poststart, poststop

#### Container Lifecycle

```
         create            start                    kill
  ○ ──────────▶ Created ──────────▶ Running ──────────────▶ Stopped
                                      │                        │
                                      │ (process exits)        │ delete
                                      ▼                        ▼
                                   Stopped ──────────────▶ Removed
```

#### Runtime Bundle (`config.json`)

The runtime bundle is what `runc` (or any OCI runtime) actually executes:

```json
{
  "ociVersion": "1.1.0",
  "process": {
    "terminal": false,
    "user": { "uid": 0, "gid": 0 },
    "args": ["nginx", "-g", "daemon off;"],
    "env": ["PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin"],
    "cwd": "/"
  },
  "root": {
    "path": "rootfs",
    "readonly": false
  },
  "linux": {
    "namespaces": [
      { "type": "pid" },
      { "type": "network" },
      { "type": "mount" },
      { "type": "ipc" },
      { "type": "uts" }
    ],
    "resources": {
      "memory": { "limit": 536870912 },
      "cpu": { "quota": 50000, "period": 100000 }
    }
  }
}
```

---

### Container Runtime Hierarchy

Not all runtimes are the same. They operate at different levels:

```
┌─────────────────────────────────────────────────────────────┐
│                        Kubernetes                            │
│                     (via CRI interface)                       │
├───────────────────┬─────────────────────────────────────────┤
│                   │                                          │
│  High-Level       │  ┌──────────┐        ┌──────────┐       │
│  Runtimes         │  │containerd│        │  CRI-O   │       │
│  (CRI-compliant)  │  │          │        │          │       │
│                   │  └────┬─────┘        └────┬─────┘       │
│                   │       │                    │              │
├───────────────────┤───────┼────────────────────┼─────────────┤
│                   │       ▼                    ▼              │
│  Low-Level        │  ┌──────────┐        ┌──────────┐       │
│  Runtimes         │  │   runc   │        │   crun   │       │
│  (OCI-compliant)  │  │          │        │ (faster) │       │
│                   │  └──────────┘        └──────────┘       │
│                   │                                          │
├───────────────────┤──────────────────────────────────────────┤
│                   │                                          │
│  Sandboxed        │  ┌──────────┐        ┌──────────┐       │
│  Runtimes         │  │  gVisor  │        │   Kata   │       │
│                   │  │ (runsc)  │        │Containers│       │
│                   │  └──────────┘        └──────────┘       │
└───────────────────┴──────────────────────────────────────────┘
```

| Runtime | Type | Key Feature | Used By |
|---------|------|-------------|---------|
| **runc** | Low-level | Reference OCI implementation, written in Go | Docker default, containerd |
| **crun** | Low-level | Written in C, faster startup | Podman default |
| **containerd** | High-level | Full lifecycle management, image pull, snapshots | Docker, K8s, cloud providers |
| **CRI-O** | High-level | Built specifically for Kubernetes CRI | OpenShift, some K8s distros |
| **gVisor (runsc)** | Sandboxed | User-space kernel, syscall interception | GKE Sandbox |
| **Kata Containers** | Sandboxed | Lightweight VM per container | Multi-tenant, high-security |

---

## Code Examples

### Example 1: Inspecting Image Layers with `crane`

```bash
# Install crane (part of go-containerregistry)
go install github.com/google/go-containerregistry/cmd/crane@latest
# Or: brew install crane

# View the manifest for an image
crane manifest nginx:alpine | jq .

# List layers with sizes
crane manifest nginx:alpine | jq '.layers[] | {digest: .digest, size: (.size / 1024 / 1024 * 100 | round / 100 | tostring + " MB")}'

# View the image config
crane config nginx:alpine | jq .

# See layer history (which Dockerfile instruction created each layer)
crane config nginx:alpine | jq '.history[] | {created_by, empty_layer}'

# Export and inspect a specific layer
crane blob nginx:alpine@sha256:<layer-digest> | tar -tzf - | head -20
```

### Example 2: Inspecting Images with `skopeo`

```bash
# Install skopeo
# brew install skopeo (macOS) or apt install skopeo (Debian/Ubuntu)

# Inspect an image without pulling it
skopeo inspect docker://docker.io/library/nginx:alpine

# Copy an image to a local OCI layout directory
skopeo copy docker://nginx:alpine oci:nginx-oci:latest

# Explore the OCI layout
tree nginx-oci/
# nginx-oci/
# ├── blobs/
# │   └── sha256/
# │       ├── <manifest-digest>    ← manifest JSON
# │       ├── <config-digest>      ← config JSON
# │       ├── <layer1-digest>      ← layer tar.gz
# │       └── <layer2-digest>      ← layer tar.gz
# ├── index.json                   ← entry point
# └── oci-layout                   ← version file

# Read the manifest
cat nginx-oci/index.json | jq .
MANIFEST_DIGEST=$(cat nginx-oci/index.json | jq -r '.manifests[0].digest' | cut -d: -f2)
cat nginx-oci/blobs/sha256/$MANIFEST_DIGEST | jq .
```

### Example 3: Running a Container with `runc` Directly

```bash
# Create a rootfs from an image
mkdir -p my-container/rootfs
docker export $(docker create alpine:latest) | tar -C my-container/rootfs -xf -

# Generate a default OCI runtime spec
cd my-container
runc spec

# Edit config.json: change "terminal" to false and set args
# (or use jq to modify programmatically)
jq '.process.terminal = false | .process.args = ["echo", "Hello from runc!"]' config.json > config_tmp.json
mv config_tmp.json config.json

# Run the container with runc
sudo runc run my-first-container
# → Hello from runc!

# List containers managed by runc
sudo runc list

# Cleanup
cd ..
rm -rf my-container
```

### Example 4: Comparing Layer Sharing Between Images

```bash
# Pull two images that share a base
docker pull node:20-alpine
docker pull node:18-alpine

# Compare their layers
echo "=== node:20-alpine layers ==="
docker inspect node:20-alpine | jq '.[0].RootFS.Layers'

echo "=== node:18-alpine layers ==="
docker inspect node:18-alpine | jq '.[0].RootFS.Layers'

# Layers with the same sha256 digest are stored only once on disk
# Check actual disk usage
docker system df -v | head -20
```

---

## Try It Yourself

### Challenge 1: Layer Archaeology

Pull the `python:3.12-slim` image and answer: How many layers does it have? What Dockerfile instruction created the largest layer? What's the total uncompressed size?

<details>
<summary>Show solution</summary>

```bash
# Inspect the image config for layer history
crane config python:3.12-slim | jq '{
  num_layers: (.rootfs.diff_ids | length),
  history: [.history[] | select(.empty_layer != true) | .created_by]
}'

# Get layer sizes from the manifest
crane manifest python:3.12-slim | jq '.layers | to_entries[] | {
  layer_num: (.key + 1),
  size_mb: (.value.size / 1024 / 1024 * 100 | round / 100)
}'

# The base Debian layer is typically the largest
# Total size:
crane manifest python:3.12-slim | jq '[.layers[].size] | add / 1024 / 1024 | round | tostring + " MB total (compressed)"'
```

</details>

### Challenge 2: Build and Inspect Your Own Image

Create a simple Dockerfile, build it, and then use `crane` or `skopeo` to inspect the resulting image's manifest, config, and layers. How many layers are "empty" (metadata-only)?

<details>
<summary>Show solution</summary>

```bash
# Create a simple Dockerfile
mkdir inspect-demo && cd inspect-demo
cat > Dockerfile << 'EOF'
FROM alpine:3.19
LABEL maintainer="you@example.com"
ENV APP_ENV=production
RUN apk add --no-cache curl
COPY <<-'SCRIPT' /hello.sh
#!/bin/sh
echo "Hello from OCI!"
SCRIPT
RUN chmod +x /hello.sh
CMD ["/hello.sh"]
EOF

# Build and tag locally
docker build -t inspect-demo:latest .

# Save as OCI tarball and examine
docker save inspect-demo:latest | tar -xf - -C inspect-output/

# Or use crane with the local Docker daemon
crane config inspect-demo:latest | jq '{
  total_layers: (.rootfs.diff_ids | length),
  empty_layers: [.history[] | select(.empty_layer == true)] | length,
  non_empty_layers: [.history[] | select(.empty_layer != true)] | length,
  instructions: [.history[].created_by]
}'

# LABEL, ENV, CMD create empty layers (metadata only, no filesystem changes)
# FROM, RUN, COPY create actual filesystem layers

# Cleanup
cd ..
rm -rf inspect-demo inspect-output
docker rmi inspect-demo:latest
```

</details>

---

## Capstone Connection

**DeployForge** images are OCI-compliant — they work with Docker, Podman, containerd, and any OCI-compatible runtime. Here's why this module matters for the capstone:

- **Layer optimization** is critical for DeployForge. In Module 02 (Docker Mastery), you'll write multi-stage Dockerfiles that minimize layer count and maximize cache hits. Understanding how layers work lets you reason about _why_ ordering Dockerfile instructions matters.
- **Content-addressable storage** is how registries (Docker Hub, ECR, GCR) avoid re-uploading unchanged layers. When DeployForge's CI pipeline pushes images in Module 10, only changed layers transfer — understanding this helps you design efficient pipelines.
- **Runtime selection** affects security and performance. In Module 03 (Container Security), you'll evaluate whether DeployForge's multi-tenant workloads need gVisor or Kata Containers instead of runc.
- **The runtime stack** (containerd → runc) is exactly what runs DeployForge pods in Kubernetes. When you troubleshoot CrashLoopBackOff in Module 05, knowing this stack helps you read `crictl` output.
