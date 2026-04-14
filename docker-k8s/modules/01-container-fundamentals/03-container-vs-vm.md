# 1.3 — Containers vs VMs: Architecture Deep Dive

## Concept

"Should I use containers or VMs?" is the wrong question. The right question is: "What isolation guarantees do I need, and what am I willing to pay for them?" This topic goes beyond the surface-level comparison to examine the architectural differences, security implications, and hybrid approaches that senior engineers actually reason about.

---

## Deep Dive

### The Architecture Stack

```
┌─────────────── Virtual Machine ───────────────┐   ┌──────────── Container ────────────────┐
│                                                │   │                                       │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐    │   │  ┌──────────┐  ┌──────────┐          │
│  │  App A    │  │  App B    │  │  App C    │    │   │  │  App A    │  │  App B    │          │
│  ├──────────┤  ├──────────┤  ├──────────┤    │   │  ├──────────┤  ├──────────┤          │
│  │  Bins/    │  │  Bins/    │  │  Bins/    │    │   │  │  Bins/    │  │  Bins/    │          │
│  │  Libs     │  │  Libs     │  │  Libs     │    │   │  │  Libs     │  │  Libs     │          │
│  ├──────────┤  ├──────────┤  ├──────────┤    │   │  └──────────┘  └──────────┘          │
│  │ Guest OS  │  │ Guest OS  │  │ Guest OS  │    │   │          (shared libs via layers)    │
│  │ (kernel)  │  │ (kernel)  │  │ (kernel)  │    │   │                                       │
│  └──────────┘  └──────────┘  └──────────┘    │   ├───────────────────────────────────────┤
│                                                │   │         Container Runtime              │
├────────────────────────────────────────────────┤   │      (containerd / CRI-O + runc)       │
│              Hypervisor                         │   ├───────────────────────────────────────┤
│     (KVM / Xen / VMware / Hyper-V)              │   │           Host OS Kernel               │
├────────────────────────────────────────────────┤   │     (namespaces + cgroups + seccomp)    │
│              Host OS / Hardware                  │   ├───────────────────────────────────────┤
└────────────────────────────────────────────────┘   │              Hardware                   │
                                                     └───────────────────────────────────────┘
```

### Hypervisor-Based Isolation

#### Type 1 (Bare-Metal) Hypervisors

Run directly on hardware. Used in production data centers and cloud providers.

| Hypervisor | Used By | Key Feature |
|------------|---------|-------------|
| **KVM** | AWS (Nitro), GCP, Linux hosts | Part of Linux kernel, uses hardware virtualization |
| **Xen** | AWS (original), Citrix | Paravirtualization support |
| **VMware ESXi** | Enterprise data centers | Mature management ecosystem |
| **Hyper-V** | Azure, Windows Server | Tight Windows integration |

#### Type 2 (Hosted) Hypervisors

Run on top of an existing OS. Used for development and testing.

| Hypervisor | Platform | Example Use |
|------------|----------|-------------|
| **VirtualBox** | Cross-platform | Local dev VMs |
| **VMware Fusion/Workstation** | macOS/Windows | Enterprise dev environments |
| **QEMU** | Linux | Emulation, cross-arch testing |
| **Parallels** | macOS | Running Windows on Mac |

### Kernel Sharing: The Fundamental Difference

This is the single most important distinction:

```
VM:        App → Guest Kernel → Hypervisor → Host Kernel → Hardware
Container: App → Host Kernel → Hardware
```

**VMs** get their own kernel. A Linux VM can run on a Windows host. A vulnerable guest kernel doesn't compromise the host (in theory).

**Containers** share the host kernel. A container making a syscall goes directly to the host kernel. This is faster but means:

- Containers must match the host kernel's OS type (Linux containers need a Linux kernel)
- A kernel vulnerability affects ALL containers on that host
- The host kernel is the trust boundary

---

### Security: Attack Surface Comparison

#### Container Escape Vectors

| Vector | Description | Mitigation |
|--------|-------------|------------|
| **Kernel exploits** | Shared kernel means a 0-day affects all containers | Keep kernel patched, use gVisor/Kata |
| **Misconfigured capabilities** | `--privileged` or excessive `CAP_*` | Drop all capabilities, add only needed ones |
| **Mounted host paths** | `-v /:/host` exposes everything | Use read-only mounts, limit volume scope |
| **Container runtime bugs** | Vulnerabilities in runc, containerd | Keep runtime updated, use minimal attack surface |
| **Exposed Docker socket** | `/var/run/docker.sock` mount = root on host | Never mount the socket in production |

#### VM Escape Vectors

| Vector | Description | Mitigation |
|--------|-------------|------------|
| **Hypervisor bugs** | VENOM (2015), vulnerabilities in virtual hardware | Patch hypervisor, use hardware-assisted virt |
| **Shared memory side-channels** | Spectre, Meltdown, MDS | Microcode updates, core isolation |
| **Virtual device emulation** | Bugs in emulated NICs, disks | Paravirtual drivers, reduce emulated devices |

> **Reality check:** VM escapes are rare and require sophisticated exploitation. Container escapes happen more often, usually due to misconfiguration. The isolation boundary is genuinely smaller for containers.

#### Defense in Depth for Containers

```
┌──────────────────────────────────────────────┐
│              Container Security Layers         │
│                                                │
│  ┌─ Seccomp ──────────────────────────────┐   │
│  │  Blocks dangerous syscalls              │   │
│  │  (default Docker profile blocks ~44)    │   │
│  │                                          │   │
│  │  ┌─ AppArmor / SELinux ─────────────┐   │   │
│  │  │  Mandatory Access Control          │   │   │
│  │  │  (limits file, network, cap access)│   │   │
│  │  │                                    │   │   │
│  │  │  ┌─ Capabilities ─────────────┐   │   │   │
│  │  │  │  Limits root powers          │   │   │   │
│  │  │  │  (e.g., no NET_RAW)         │   │   │   │
│  │  │  │                              │   │   │   │
│  │  │  │  ┌─ Namespaces ──────┐     │   │   │   │
│  │  │  │  │  Process isolation  │     │   │   │   │
│  │  │  │  │  ┌─ Cgroups ─┐    │     │   │   │   │
│  │  │  │  │  │ Resource   │    │     │   │   │   │
│  │  │  │  │  │ limits     │    │     │   │   │   │
│  │  │  │  │  └────────────┘    │     │   │   │   │
│  │  │  │  └────────────────────┘     │   │   │   │
│  │  │  └──────────────────────────────┘   │   │   │
│  │  └──────────────────────────────────────┘   │   │
│  └──────────────────────────────────────────────┘   │
└──────────────────────────────────────────────────────┘
```

---

### Performance Comparison

| Metric | VM | Container | Why |
|--------|-----|-----------|-----|
| **Startup time** | 30s – 5min | 50ms – 2s | No kernel boot, no device init |
| **Memory overhead** | 512MB – 2GB per VM | 5MB – 50MB per container | No guest kernel/OS in memory |
| **Disk overhead** | 1GB – 20GB per image | 5MB – 500MB per image | Shared base layers, no OS duplication |
| **CPU overhead** | 2–5% (hardware virt) | <1% (native syscalls) | No hypervisor trap/emulation |
| **I/O performance** | Near-native with virtio | Native | No virtual device translation |
| **Density** | 10–50 VMs per host | 100–1000+ containers per host | Lower overhead per instance |
| **Network latency** | +10–50μs (virtual switch) | +2–5μs (veth + bridge) | Fewer layers to traverse |

> **Startup time matters more than you think.** In a Kubernetes autoscaling scenario, going from 0 to handling traffic in 2 seconds vs 2 minutes is the difference between a great and terrible user experience during load spikes.

---

### When to Use What

#### Use Containers When:

- Workloads share the same OS (Linux)
- Fast startup and scaling are priorities
- You need high density (many services per host)
- You trust the workloads (same organization, same team)
- You're doing microservices, CI/CD, or dev environments

#### Use VMs When:

- Workloads need different operating systems
- You need strong multi-tenant isolation (different customers on same hardware)
- Compliance requires hardware-level isolation (PCI DSS, HIPAA)
- Running legacy applications that need a full OS
- You need kernel-level customization per workload

#### Use Both (the production reality):

Most production environments use VMs _and_ containers:

```
┌──────────────── Cloud Provider ──────────────────┐
│                                                    │
│  ┌─────────── VM (EC2/GCE instance) ───────────┐  │
│  │                                               │  │
│  │  ┌─ Kubernetes Node ────────────────────┐    │  │
│  │  │                                       │    │  │
│  │  │  ┌─────┐  ┌─────┐  ┌─────┐          │    │  │
│  │  │  │Pod A│  │Pod B│  │Pod C│          │    │  │
│  │  │  └─────┘  └─────┘  └─────┘          │    │  │
│  │  │                                       │    │  │
│  │  │  containerd + runc                    │    │  │
│  │  └───────────────────────────────────────┘    │  │
│  │                                               │  │
│  │  Host: Ubuntu 22.04 / Amazon Linux 2023       │  │
│  └───────────────────────────────────────────────┘  │
│                                                      │
│  Hypervisor: KVM (Nitro) / GCP Hypervisor            │
└──────────────────────────────────────────────────────┘

VM provides: hardware isolation between customers
Containers provide: efficient workload packing within a customer's nodes
```

---

### Bridging the Gap: Kata Containers and gVisor

These technologies try to give you container UX with stronger isolation:

#### Kata Containers

- Each container (or pod) runs in a lightweight VM
- Uses a stripped-down kernel (~50MB guest image)
- Startup in ~100–200ms (vs seconds for full VMs)
- Overhead: ~20–40MB memory per pod
- Used when you need VM-level isolation but want the Kubernetes API
- Supported as an OCI runtime (drop-in replacement for runc)

#### gVisor (runsc)

- Implements a user-space kernel that intercepts syscalls
- Container syscalls go to gVisor's Sentry (not the host kernel)
- Supports ~70% of Linux syscalls — enough for most workloads
- Overhead: ~50–100MB per sandbox, 5–10% CPU for syscall-heavy workloads
- Not compatible with all applications (anything needing unsupported syscalls)
- Used by GKE Sandbox for untrusted workloads

```
Standard container:      App → syscall → Host Kernel
Kata Containers:         App → syscall → Guest Kernel → Hypervisor → Host Kernel
gVisor:                  App → syscall → Sentry (user-space) → limited Host syscalls
```

| Feature | runc | gVisor | Kata |
|---------|------|--------|------|
| Isolation | Namespaces + cgroups | User-space kernel | Lightweight VM |
| Startup overhead | ~50ms | ~150ms | ~200ms |
| Memory overhead | ~5MB | ~50–100MB | ~20–40MB |
| Syscall compatibility | 100% | ~70% | 100% |
| Host kernel exposure | Full | Minimal | None (guest kernel) |
| Best for | Trusted workloads | Untrusted code, CI | Multi-tenant, compliance |

---

## Code Examples

### Example 1: Measuring Startup Time

```bash
# Container startup time
time docker run --rm alpine echo "Container started"
# real    0m0.5s (first run with image pull)
# real    0m0.2s (subsequent runs)

# Compare with a VM (if you have multipass or vagrant)
# time multipass launch --name test-vm
# real    0m45s  (typical for a full VM boot)
```

### Example 2: Comparing Memory Overhead

```bash
# Memory used by 10 containers vs 10 VMs

# Containers: run 10 idle alpine containers
for i in $(seq 1 10); do
  docker run -d --name mem-test-$i alpine sleep infinity
done

# Check total memory usage
docker stats --no-stream --format "table {{.Name}}\t{{.MemUsage}}" | grep mem-test
# Each alpine container: ~500KB–2MB
# Total for 10: ~5–20MB

# Cleanup
for i in $(seq 1 10); do docker rm -f mem-test-$i; done

# For comparison, 10 VMs would need at minimum:
# 10 × 256MB = 2.5GB (and that's with tiny VMs)
```

### Example 3: Observing Kernel Sharing

```bash
# Start a container and check its kernel version
docker run --rm alpine uname -r
# → 6.5.0-44-generic (same as host!)

# On the host
uname -r
# → 6.5.0-44-generic (identical — it's the SAME kernel)

# Now check a VM (if available)
# multipass exec test-vm -- uname -r
# → Could be different! The VM has its own kernel.

# This is why a Linux container can't run on a Windows kernel directly
# (Docker Desktop on Mac/Windows uses a Linux VM under the hood)
```

### Example 4: Checking Container Security Profile

```bash
# See what capabilities a default Docker container gets
docker run --rm alpine sh -c 'cat /proc/self/status | grep Cap'
# CapPrm: 00000000a80425fb
# CapEff: 00000000a80425fb

# Decode the capabilities bitmask
docker run --rm alpine sh -c 'apk add --no-cache libcap && capsh --decode=00000000a80425fb'
# Typical defaults: CHOWN, DAC_OVERRIDE, FOWNER, FSETID, KILL, SETGID,
#   SETUID, SETPCAP, NET_BIND_SERVICE, NET_RAW, SYS_CHROOT, MKNOD,
#   AUDIT_WRITE, SETFCAP

# Compare with --privileged (DON'T do this in production)
docker run --rm --privileged alpine sh -c 'cat /proc/self/status | grep Cap'
# CapPrm: 000001ffffffffff  (ALL capabilities!)

# See the default seccomp profile
docker info --format '{{.SecurityOptions}}'
# → [name=seccomp,profile=builtin ...]
```

---

## Try It Yourself

### Challenge 1: Kernel Sharing Proof

Prove that containers share the host kernel by creating a file in `/proc` from inside a container and observing it from the host. (Hint: `/proc/sys/kernel/hostname` in a UTS namespace.)

<details>
<summary>Show solution</summary>

```bash
# Start a container with a custom hostname
docker run -d --name kernel-proof --hostname proof-container alpine sleep infinity

# Check the hostname inside the container
docker exec kernel-proof hostname
# → proof-container

# Get the container's PID on the host
CPID=$(docker inspect --format '{{.State.Pid}}' kernel-proof)

# Read the container's hostname via /proc on the host
sudo cat /proc/$CPID/root/etc/hostname
# → proof-container

# But the HOST hostname is unchanged
hostname
# → your-host-name

# The container sees a different hostname because of UTS namespace
# but it's still running on the same kernel. Proof:
docker exec kernel-proof uname -r
uname -r
# Both output the same kernel version

# Even more proof — the container process is visible on the host
ps aux | grep "sleep infinity"
# → Shows the container's process with a HOST PID

# Cleanup
docker rm -f kernel-proof
```

</details>

### Challenge 2: Density Test

Run as many idle containers as your system allows. What's the limiting factor — memory, PIDs, or something else?

<details>
<summary>Show solution</summary>

```bash
# Run containers in a loop, count how many succeed
COUNT=0
while docker run -d --name density-$COUNT --memory 4m alpine sleep infinity 2>/dev/null; do
  COUNT=$((COUNT + 1))
  if [ $((COUNT % 50)) -eq 0 ]; then
    echo "Running $COUNT containers..."
  fi
done

echo "Max containers: $COUNT"

# Check what limited us
docker logs density-$((COUNT)) 2>&1  # Check last failed container
dmesg | tail -20                      # Check kernel messages
cat /proc/sys/kernel/pid_max          # PID limit
docker info | grep -i memory          # Available memory

# Check system resource usage
docker stats --no-stream --format "{{.MemUsage}}" | head -5

# Cleanup (this may take a while!)
for i in $(seq 0 $((COUNT - 1))); do
  docker rm -f density-$i 2>/dev/null
done

# Typical limiting factors:
# 1. Memory (each container needs at least a few MB for its runtime)
# 2. PIDs (/proc/sys/kernel/pid_max, default 32768 or 4194304)
# 3. File descriptors (ulimit -n)
# 4. Docker daemon overhead (tracking container state)
```

</details>

---

## Capstone Connection

**DeployForge** uses containers for its microservices but understanding VM-level isolation informs critical security decisions:

- **Multi-tenancy decisions:** If DeployForge ever serves multiple customers on shared infrastructure, you'll need to decide whether namespace isolation is sufficient or whether VM-level boundaries (Kata Containers, Firecracker) are required. This decision comes up in Module 03 (Container Security).
- **Cloud architecture:** DeployForge runs on cloud VMs (EC2/GCE) with Kubernetes scheduling containers inside them. Understanding both layers helps you reason about the security boundary diagram during Module 04 (Kubernetes Architecture).
- **Performance budgeting:** When you set resource requests and limits in Module 05, knowing that container overhead is near-zero (vs VM overhead) helps you allocate resources more tightly — packing more pods per node without wasting memory on guest kernels.
- **Startup time guarantees:** DeployForge's HPA (Horizontal Pod Autoscaler) in Module 12 depends on containers starting in seconds. If you needed VM-level isolation, you'd need to pre-warm instances — a fundamentally different scaling strategy.
