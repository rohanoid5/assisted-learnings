# 3.2 — Runtime Security: Capabilities, Seccomp & AppArmor

## Concept

A hardened image is only half the battle. Even a minimal, non-root container has more privileges than it needs by default. Docker grants containers a set of Linux capabilities, allows thousands of syscalls, and gives the root filesystem read-write access — all by default.

Runtime security is about reducing the blast radius: if an attacker compromises your application code, what can they actually *do* inside the container? With proper runtime restrictions, the answer should be "almost nothing useful."

This topic covers the three layers of runtime confinement — Linux capabilities, seccomp (syscall filtering), and AppArmor/SELinux (mandatory access control) — plus practical patterns for locking down production containers.

---

## Deep Dive

### Linux Capabilities

Traditional Unix has two privilege levels: root (UID 0, can do everything) and non-root (can't do privileged things). Linux capabilities break root's power into ~40 discrete privileges that can be granted or revoked independently.

```
┌───────────────────────────────────────────────────────────────┐
│               Linux Capabilities Model                         │
│                                                                │
│  Traditional:   root ──── ALL PRIVILEGES                       │
│                 user ──── NO PRIVILEGES                        │
│                                                                │
│  Capabilities:  root ──── CAP_NET_ADMIN                        │
│                          CAP_SYS_ADMIN                         │
│                          CAP_NET_BIND_SERVICE                  │
│                          CAP_CHOWN                             │
│                          CAP_SETUID                            │
│                          ... (~40 total)                       │
│                                                                │
│  You can: drop ALL, then add back ONLY what you need.          │
└───────────────────────────────────────────────────────────────┘
```

**Docker's default capability set** (granted to containers by default):

| Capability | What It Allows | Usually Needed? |
|-----------|---------------|-----------------|
| `CAP_CHOWN` | Change file ownership | Rarely |
| `CAP_DAC_OVERRIDE` | Bypass file permission checks | Rarely |
| `CAP_FSETID` | Set SUID/SGID bits | No |
| `CAP_FOWNER` | Bypass permission checks for file owner | Rarely |
| `CAP_MKNOD` | Create special files | No |
| `CAP_NET_RAW` | Use RAW/PACKET sockets (ping) | Rarely |
| `CAP_SETGID` | Set group ID | No |
| `CAP_SETUID` | Set user ID | No |
| `CAP_SETFCAP` | Set file capabilities | No |
| `CAP_SETPCAP` | Transfer capabilities | No |
| `CAP_NET_BIND_SERVICE` | Bind to ports below 1024 | Sometimes |
| `CAP_SYS_CHROOT` | Use chroot | No |
| `CAP_KILL` | Send signals to any process | Rarely |
| `CAP_AUDIT_WRITE` | Write to audit log | Rarely |

> **Key insight:** Docker grants 14 capabilities by default. A typical Node.js web service needs **zero or one** (NET_BIND_SERVICE if binding to port 80/443). That's 13+ unnecessary attack vectors.

**Capabilities NOT granted by default (but sometimes requested):**

| Capability | What It Allows | Risk |
|-----------|---------------|------|
| `CAP_SYS_ADMIN` | Mount filesystems, trace processes, many more | **Extremely dangerous** — nearly equivalent to full root |
| `CAP_NET_ADMIN` | Network configuration, iptables | High — can sniff traffic |
| `CAP_SYS_PTRACE` | Trace/debug other processes | High — can read process memory |
| `CAP_SYS_MODULE` | Load kernel modules | Critical — full kernel access |

### Dropping All Capabilities

The principle of least privilege: drop everything, add back only what you need.

```bash
# Drop ALL capabilities — container can still run most applications
docker run --rm --cap-drop=ALL my-app:latest

# Drop all, add back only NET_BIND_SERVICE (for port 80)
docker run --rm \
  --cap-drop=ALL \
  --cap-add=NET_BIND_SERVICE \
  my-app:latest

# Verify: list capabilities inside the container
docker run --rm --cap-drop=ALL alpine cat /proc/1/status | grep Cap
# CapPrm: 0000000000000000   ← no capabilities
# CapEff: 0000000000000000   ← no effective capabilities

# Compare with default capabilities
docker run --rm alpine cat /proc/1/status | grep Cap
# CapPrm: 00000000a80425fb   ← 14 capabilities
# CapEff: 00000000a80425fb
```

```bash
# Decode capability bitmask (useful for debugging)
# Install capsh if needed: apk add libcap
docker run --rm alpine sh -c 'apk add -q libcap && capsh --decode=00000000a80425fb'
# 0x00000000a80425fb=cap_chown,cap_dac_override,cap_fowner,cap_fsetid,
# cap_kill,cap_setgid,cap_setuid,cap_setpcap,cap_net_bind_service,
# cap_net_raw,cap_sys_chroot,cap_mknod,cap_audit_write,cap_setfcap
```

### Seccomp Profiles

Seccomp (Secure Computing) filters which Linux syscalls a container can make. Docker's default seccomp profile blocks ~44 of the ~300+ syscalls (including dangerous ones like `reboot`, `mount`, `kexec_load`), but you can go further with a custom profile.

```
┌───────────────────────────────────────────────────────────────┐
│                  Seccomp Filtering                              │
│                                                                │
│   Application                                                  │
│       │                                                        │
│       ▼                                                        │
│   ┌──────────┐                                                 │
│   │  libc    │  read(), write(), open(), socket(), ...         │
│   └────┬─────┘                                                 │
│        │ syscall                                               │
│        ▼                                                       │
│   ┌──────────────────────────┐                                 │
│   │    Seccomp Filter (BPF)  │                                 │
│   │                          │                                 │
│   │  read()     → ALLOW      │                                 │
│   │  write()    → ALLOW      │                                 │
│   │  mount()    → DENY ✗     │                                 │
│   │  reboot()   → DENY ✗     │                                 │
│   │  ptrace()   → DENY ✗     │                                 │
│   └──────────────────────────┘                                 │
│        │                                                       │
│        ▼                                                       │
│   Linux Kernel                                                 │
└───────────────────────────────────────────────────────────────┘
```

**Docker's default seccomp profile blocks these syscall categories:**

| Blocked Syscalls | Reason |
|-----------------|--------|
| `mount`, `umount2` | Prevent filesystem mounting |
| `reboot` | Prevent host reboot |
| `kexec_load` | Prevent kernel replacement |
| `create_module`, `init_module` | Prevent kernel module loading |
| `clock_settime` | Prevent time manipulation |
| `acct` | Prevent process accounting |
| `pivot_root` | Prevent changing root filesystem |
| `swapon`, `swapoff` | Prevent swap manipulation |

### Custom Seccomp Profile

For maximum security, create a custom profile that only allows the syscalls your application actually needs:

```json
{
  "defaultAction": "SCMP_ACT_ERRNO",
  "comment": "DeployForge API Gateway — allow only required syscalls",
  "architectures": [
    "SCMP_ARCH_X86_64",
    "SCMP_ARCH_AARCH64"
  ],
  "syscalls": [
    {
      "names": [
        "accept", "accept4",
        "bind",
        "brk",
        "clock_getres", "clock_gettime", "clock_nanosleep",
        "clone", "clone3",
        "close",
        "connect",
        "dup", "dup2", "dup3",
        "epoll_create1", "epoll_ctl", "epoll_pwait", "epoll_wait",
        "eventfd2",
        "execve",
        "exit", "exit_group",
        "faccessat2",
        "fchmodat",
        "fchownat",
        "fcntl",
        "fstat", "fstatfs",
        "futex",
        "getcwd",
        "getdents64",
        "getegid", "geteuid", "getgid", "getgroups",
        "getpeername", "getpid", "getppid",
        "getrandom",
        "getsockname", "getsockopt",
        "getuid",
        "ioctl",
        "listen",
        "lseek",
        "madvise",
        "memfd_create",
        "mmap", "mprotect", "mremap", "munmap",
        "nanosleep",
        "newfstatat",
        "openat",
        "pipe2",
        "pread64", "pwrite64",
        "read", "readlink", "readlinkat", "readv",
        "recvfrom", "recvmsg",
        "rt_sigaction", "rt_sigprocmask", "rt_sigreturn",
        "sched_getaffinity", "sched_yield",
        "sendmsg", "sendto",
        "set_robust_list", "set_tid_address",
        "setsockopt",
        "shutdown",
        "sigaltstack",
        "socket",
        "stat", "statx",
        "tgkill",
        "umask",
        "uname",
        "unlinkat",
        "wait4",
        "write", "writev"
      ],
      "action": "SCMP_ACT_ALLOW"
    }
  ]
}
```

```bash
# Run with a custom seccomp profile
docker run --rm \
  --security-opt seccomp=docker/seccomp-default.json \
  my-app:latest

# Run with NO seccomp profile (dangerous — never do this in prod)
docker run --rm --security-opt seccomp=unconfined my-app:latest

# Generate a profile by tracing syscalls (development only)
# Use strace to discover which syscalls your app actually makes
docker run --rm --security-opt seccomp=unconfined \
  strace -f -c node dist/server.js 2>&1 | tail -30
```

> **Practical approach:** Start with Docker's default seccomp profile. Only create a custom profile if you need tighter restrictions (e.g., PCI compliance) or if the default blocks something your app needs.

### AppArmor Profiles

AppArmor is a Linux Security Module (LSM) that restricts what files, network, and capabilities a process can access. Docker loads a default AppArmor profile (`docker-default`) for containers.

```bash
# Check if AppArmor is active (Linux only — not applicable on macOS Docker Desktop)
cat /sys/module/apparmor/parameters/enabled
# → Y

# See the default Docker AppArmor profile
docker run --rm alpine cat /proc/1/attr/current
# → docker-default (enforce)

# Run with no AppArmor profile (dangerous)
docker run --rm --security-opt apparmor=unconfined alpine
```

**Custom AppArmor profile example:**

```
# /etc/apparmor.d/docker-deployforge
#include <tunables/global>

profile docker-deployforge flags=(attach_disconnected,mediate_deleted) {
  #include <abstractions/base>

  # Allow network access
  network inet tcp,
  network inet udp,
  network inet6 tcp,
  network inet6 udp,

  # Allow reading app files
  /app/** r,
  /app/dist/** ix,

  # Allow Node.js runtime
  /usr/local/bin/node ix,
  /usr/local/lib/node_modules/** r,

  # Deny write to everything except /tmp and /app/logs
  deny /etc/** w,
  deny /usr/** w,
  /tmp/** rw,
  /app/logs/** rw,

  # Deny dangerous operations
  deny mount,
  deny ptrace,
  deny signal (send) peer=unconfined,
}
```

```bash
# Load the profile
sudo apparmor_parser -r /etc/apparmor.d/docker-deployforge

# Run with the custom profile
docker run --rm \
  --security-opt apparmor=docker-deployforge \
  my-app:latest
```

### Read-Only Root Filesystem

If your application doesn't need to write to the root filesystem (and most shouldn't), make it read-only. This prevents attackers from modifying binaries, planting backdoors, or altering configuration.

```bash
# Run with read-only root filesystem
docker run --rm \
  --read-only \
  my-app:latest
# → Error if the app tries to write to /tmp, /var, etc.

# Read-only with tmpfs for directories that need to be writable
docker run --rm \
  --read-only \
  --tmpfs /tmp:rw,noexec,nosuid,size=64m \
  --tmpfs /var/run:rw,noexec,nosuid,size=1m \
  my-app:latest
```

```yaml
# docker-compose.security.yml — security-hardened overrides
services:
  api-gateway:
    read_only: true
    tmpfs:
      - /tmp:rw,noexec,nosuid,size=64m
    security_opt:
      - no-new-privileges:true
    cap_drop:
      - ALL
    cap_add:
      - NET_BIND_SERVICE
```

> **Common writable directories:** `/tmp` (temp files), `/var/run` (PID files), `/app/logs` (if not using stdout). Mount these as tmpfs or named volumes.

### No-New-Privileges Flag

The `no-new-privileges` flag prevents processes inside the container from gaining additional privileges through SUID/SGID binaries or other mechanisms.

```bash
# Run with no-new-privileges
docker run --rm \
  --security-opt no-new-privileges:true \
  my-app:latest

# What this blocks:
# - SUID/SGID binaries (e.g., su, sudo, passwd)
# - Capability escalation via execve
# - Any mechanism that grants more privileges than the parent process
```

Without `no-new-privileges`, an attacker who finds a writable SUID binary can escalate to root. With it, even SUID binaries run with the same privileges as the calling process.

### Docker Socket Security

The Docker socket (`/var/run/docker.sock`) gives full control over the Docker daemon — equivalent to root on the host.

```bash
# ❌ NEVER DO THIS IN PRODUCTION
docker run -v /var/run/docker.sock:/var/run/docker.sock my-app

# Why? An attacker inside this container can:
# 1. Create a new privileged container
# 2. Mount the host filesystem
# 3. Escape to the host with full root access
docker run -v /var/run/docker.sock:/var/run/docker.sock alpine \
  sh -c 'apk add docker-cli && docker run --privileged -v /:/host alpine chroot /host'
# → Full host access. Game over.
```

**If you absolutely need Docker-in-Docker (CI/CD):**
- Use Docker-in-Docker (`dind`) with TLS
- Use rootless Docker
- Use Kaniko for image building (no daemon needed)
- Use Buildah for OCI builds (daemonless)

### Runtime Security Monitoring: Falco

Falco watches container behavior in real time and alerts on suspicious activity.

```yaml
# Example Falco rule: alert on shell spawned in container
- rule: Terminal shell in container
  desc: A shell was spawned in a container
  condition: >
    spawned_process and container
    and shell_procs
    and proc.tty != 0
  output: >
    Shell spawned in container
    (user=%user.name container=%container.name
    shell=%proc.name parent=%proc.pname)
  priority: WARNING

- rule: Write below binary dir
  desc: Detect writes to /bin, /sbin, /usr/bin, etc.
  condition: >
    write and container
    and bin_dir
  output: >
    File written below binary directory
    (file=%fd.name container=%container.name)
  priority: ERROR
```

```bash
# Run Falco as a container (for testing)
docker run --rm -i -t \
  --privileged \
  -v /var/run/docker.sock:/host/var/run/docker.sock \
  -v /proc:/host/proc:ro \
  falcosecurity/falco:latest
```

---

## Code Examples

### Example 1: Fully Locked-Down Container

```bash
#!/usr/bin/env bash
# Run a container with all security hardening enabled

docker run -d \
  --name deployforge-api \
  \
  # Drop all capabilities, add only NET_BIND_SERVICE
  --cap-drop=ALL \
  --cap-add=NET_BIND_SERVICE \
  \
  # Read-only root filesystem
  --read-only \
  \
  # Writable tmpfs for temp files
  --tmpfs /tmp:rw,noexec,nosuid,size=64m \
  \
  # Prevent privilege escalation
  --security-opt no-new-privileges:true \
  \
  # Custom seccomp profile
  --security-opt seccomp=docker/seccomp-default.json \
  \
  # Resource limits
  --memory=512m \
  --cpus=1.0 \
  --pids-limit=100 \
  \
  # Run as non-root user
  --user 1001:1001 \
  \
  # Network restrictions
  --network=deployforge-net \
  \
  deployforge-api:latest
```

### Example 2: Docker Compose Security Overrides

```yaml
# docker-compose.security.yml
# Usage: docker compose -f docker-compose.yml -f docker-compose.security.yml up

services:
  api-gateway:
    read_only: true
    tmpfs:
      - /tmp:rw,noexec,nosuid,size=64m
    security_opt:
      - no-new-privileges:true
      - seccomp=docker/seccomp-default.json
    cap_drop:
      - ALL
    cap_add:
      - NET_BIND_SERVICE
    deploy:
      resources:
        limits:
          memory: 512M
          cpus: '1.0'
          pids: 100

  worker:
    read_only: true
    tmpfs:
      - /tmp:rw,noexec,nosuid,size=64m
    security_opt:
      - no-new-privileges:true
    cap_drop:
      - ALL
    deploy:
      resources:
        limits:
          memory: 256M
          cpus: '0.5'
          pids: 50

  postgres:
    read_only: true
    tmpfs:
      - /tmp:rw,size=256m
      - /run/postgresql:rw,size=1m
    security_opt:
      - no-new-privileges:true
    cap_drop:
      - ALL
    cap_add:
      - CHOWN
      - DAC_OVERRIDE
      - FOWNER
      - SETGID
      - SETUID
```

### Example 3: Auditing Container Security Posture

```bash
#!/usr/bin/env bash
# scripts/audit-container.sh — Audit a running container's security posture

CONTAINER="${1:?Usage: audit-container.sh <container-name>}"

echo "=== Security Audit: ${CONTAINER} ==="
echo ""

# Check if running as root
UID=$(docker exec "${CONTAINER}" id -u 2>/dev/null)
if [ "${UID}" = "0" ]; then
  echo "❌ FAIL: Running as root (UID 0)"
else
  echo "✅ PASS: Running as non-root (UID ${UID})"
fi

# Check capabilities
CAPS=$(docker inspect --format='{{.HostConfig.CapDrop}}' "${CONTAINER}")
if echo "${CAPS}" | grep -q "ALL\|all"; then
  echo "✅ PASS: All capabilities dropped"
else
  echo "❌ FAIL: Capabilities not fully dropped (CapDrop: ${CAPS})"
fi

# Check read-only root filesystem
READONLY=$(docker inspect --format='{{.HostConfig.ReadonlyRootfs}}' "${CONTAINER}")
if [ "${READONLY}" = "true" ]; then
  echo "✅ PASS: Read-only root filesystem"
else
  echo "❌ FAIL: Root filesystem is writable"
fi

# Check no-new-privileges
SECOPT=$(docker inspect --format='{{.HostConfig.SecurityOpt}}' "${CONTAINER}")
if echo "${SECOPT}" | grep -q "no-new-privileges"; then
  echo "✅ PASS: no-new-privileges enabled"
else
  echo "❌ FAIL: no-new-privileges not set"
fi

# Check resource limits
MEM=$(docker inspect --format='{{.HostConfig.Memory}}' "${CONTAINER}")
if [ "${MEM}" != "0" ]; then
  echo "✅ PASS: Memory limit set (${MEM} bytes)"
else
  echo "❌ FAIL: No memory limit"
fi

echo ""
echo "=== Full security configuration ==="
docker inspect --format='
  User:           {{.Config.User}}
  ReadOnly:       {{.HostConfig.ReadonlyRootfs}}
  CapAdd:         {{.HostConfig.CapAdd}}
  CapDrop:        {{.HostConfig.CapDrop}}
  SecurityOpt:    {{.HostConfig.SecurityOpt}}
  Memory:         {{.HostConfig.Memory}}
  PidsLimit:      {{.HostConfig.PidsLimit}}
  Privileged:     {{.HostConfig.Privileged}}
' "${CONTAINER}"
```

---

## Try It Yourself

### Challenge 1: Drop and Test Capabilities

Run a container with default capabilities and try `ping`. Then drop all capabilities and try again. Finally, add back only `NET_RAW` and verify ping works.

<details>
<summary>Show solution</summary>

```bash
# Default capabilities — ping works
docker run --rm alpine ping -c 1 8.8.8.8
# → PING 8.8.8.8: 64 bytes from 8.8.8.8 ...

# Drop ALL capabilities — ping fails
docker run --rm --cap-drop=ALL alpine ping -c 1 8.8.8.8
# → ping: permission denied (no CAP_NET_RAW)

# Drop all, add back only NET_RAW — ping works again
docker run --rm --cap-drop=ALL --cap-add=NET_RAW alpine ping -c 1 8.8.8.8
# → PING 8.8.8.8: 64 bytes from 8.8.8.8 ...

# Verify capabilities inside the container
docker run --rm --cap-drop=ALL --cap-add=NET_RAW alpine \
  sh -c 'apk add -q libcap && capsh --print' 2>/dev/null | grep "Current:"
# → Current: cap_net_raw=eip
```

</details>

### Challenge 2: Read-Only Root Filesystem

Run a Node.js container with `--read-only` and try to:
1. Write a file to `/tmp`
2. Write a file to `/app`
3. Fix the issue with tmpfs mounts

<details>
<summary>Show solution</summary>

```bash
# Read-only — /tmp write fails
docker run --rm --read-only alpine touch /tmp/test
# → touch: /tmp/test: Read-only file system

# Read-only — /app write fails
docker run --rm --read-only alpine touch /app/test
# → touch: /app/test: Read-only file system

# Fix: tmpfs mounts for writable directories
docker run --rm \
  --read-only \
  --tmpfs /tmp:rw,noexec,nosuid,size=64m \
  alpine sh -c 'echo "hello" > /tmp/test && cat /tmp/test'
# → hello

# /app is still read-only (which is what we want)
docker run --rm \
  --read-only \
  --tmpfs /tmp:rw,noexec,nosuid,size=64m \
  alpine touch /app/test
# → touch: /app/test: Read-only file system ← GOOD
```

**Key insight:** Use `noexec` on tmpfs mounts to prevent executing uploaded scripts from `/tmp`. Use `nosuid` to prevent SUID escalation. Set a `size` limit to prevent tmpfs from eating all memory.

</details>

### Challenge 3: Investigate Default Seccomp

Prove that Docker's default seccomp profile blocks `mount()`:

<details>
<summary>Show solution</summary>

```bash
# Try to mount inside a container (default seccomp)
docker run --rm alpine mount -t tmpfs tmpfs /mnt
# → mount: permission denied (seccomp blocks mount syscall)

# Now with seccomp=unconfined (NEVER do this in production)
docker run --rm --security-opt seccomp=unconfined --cap-add SYS_ADMIN alpine mount -t tmpfs tmpfs /mnt
# → (succeeds — no seccomp filtering)

# See which syscalls seccomp blocks by default
# Docker's default profile is at:
# https://github.com/moby/moby/blob/master/profiles/seccomp/default.json
# Blocked syscalls include: mount, reboot, kexec_load, init_module, etc.
```

</details>

---

## Capstone Connection

**DeployForge containers run with `--cap-drop=ALL --cap-add=NET_BIND_SERVICE`, read-only root filesystem, and a custom seccomp profile.** Here's how this topic connects to the capstone:

- **Capability restrictions** — Every DeployForge service runs with `cap_drop: ALL`. The API Gateway adds back `NET_BIND_SERVICE` (binds to port 443 in production). The worker service adds back nothing — it doesn't need any capabilities. PostgreSQL adds back `CHOWN`, `DAC_OVERRIDE`, `FOWNER`, `SETGID`, and `SETUID` (required for its initialization script).

- **Read-only root filesystem** — All DeployForge services use `read_only: true` in the Compose security override. Writable directories (`/tmp`, `/var/run`) are mounted as tmpfs with `noexec,nosuid` flags and size limits. Application logs go to stdout (Docker logging driver), not files.

- **No-new-privileges** — Every container sets `security_opt: no-new-privileges:true`. This prevents SUID escalation even if an attacker manages to upload a binary.

- **Custom seccomp profile** — The `docker/seccomp-default.json` profile allows ~80 syscalls (vs Docker's default ~260). It was generated by running the application under `strace`, identifying needed syscalls, and adding a safety margin. The profile is tested in CI to ensure the app still functions correctly.

- **Security audit script** — The `scripts/audit-container.sh` script runs in CI after `docker compose up` to verify all running containers meet the security baseline. Any container running as root, with unconstrained capabilities, or without a read-only rootfs fails the pipeline.

- **Kubernetes alignment** — In Module 05 (Kubernetes), these Docker runtime settings map directly to Pod `securityContext`: `runAsNonRoot`, `readOnlyRootFilesystem`, `allowPrivilegeEscalation: false`, and `capabilities.drop: ["ALL"]`. Getting them right in Docker makes the Kubernetes migration seamless.

In the next topic (3.3), we'll secure the supply chain — ensuring the images we deploy are the exact images we built and signed in CI.
