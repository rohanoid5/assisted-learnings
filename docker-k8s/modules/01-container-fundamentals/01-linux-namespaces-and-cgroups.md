# 1.1 — Linux Namespaces & Cgroups

## Concept

Every time you run `docker run`, Docker doesn't start a virtual machine. It asks the Linux kernel to create **isolated views** of system resources for a process. Two kernel features make this possible:

- **Namespaces** — what a process can _see_ (isolation)
- **Cgroups** — what a process can _use_ (resource limits)

Together they form the foundation of every container runtime. Understanding them is the difference between "I use Docker" and "I understand containers."

---

## Deep Dive

### Linux Namespaces

A namespace wraps a global system resource in an abstraction that makes it appear to the processes inside the namespace that they have their own isolated instance of that resource. Changes inside the namespace are invisible outside it.

#### The 8 Namespace Types

| Namespace | Flag | Isolates | Kernel Version |
|-----------|------|----------|----------------|
| **PID** | `CLONE_NEWPID` | Process IDs — container sees its init as PID 1 | 2.6.24 |
| **NET** | `CLONE_NEWNET` | Network stack — own interfaces, routing tables, iptables | 2.6.29 |
| **MNT** | `CLONE_NEWNS` | Mount points — own filesystem view | 2.4.19 |
| **UTS** | `CLONE_NEWUTS` | Hostname and domain name | 2.6.19 |
| **IPC** | `CLONE_NEWIPC` | System V IPC, POSIX message queues | 2.6.19 |
| **USER** | `CLONE_NEWUSER` | User/group IDs — root inside ≠ root outside | 3.8 |
| **Cgroup** | `CLONE_NEWCGROUP` | Cgroup root directory — hides host cgroup hierarchy | 4.6 |
| **Time** | `CLONE_NEWTIME` | `CLOCK_MONOTONIC` and `CLOCK_BOOTTIME` | 5.6 |

```
┌──────────────────────────────────────────────────────────┐
│                     HOST KERNEL                          │
│                                                          │
│  ┌─────────────────────┐   ┌─────────────────────┐      │
│  │   Container A        │   │   Container B        │      │
│  │                       │   │                       │      │
│  │  PID namespace        │   │  PID namespace        │      │
│  │  ┌───┐ ┌───┐ ┌───┐  │   │  ┌───┐ ┌───┐        │      │
│  │  │ 1 │ │ 2 │ │ 3 │  │   │  │ 1 │ │ 2 │        │      │
│  │  └───┘ └───┘ └───┘  │   │  └───┘ └───┘        │      │
│  │                       │   │                       │      │
│  │  NET namespace        │   │  NET namespace        │      │
│  │  eth0: 172.17.0.2    │   │  eth0: 172.17.0.3    │      │
│  │                       │   │                       │      │
│  │  MNT namespace        │   │  MNT namespace        │      │
│  │  / → overlay rootfs  │   │  / → overlay rootfs  │      │
│  └─────────────────────┘   └─────────────────────┘      │
│                                                          │
│  Host PIDs: 4521, 4522, 4523    Host PIDs: 4530, 4531   │
│  Host eth0: 192.168.1.10                                 │
└──────────────────────────────────────────────────────────┘
```

> **Key insight:** Container PID 1 is just a regular process on the host with a different PID. The namespace changes what the process _sees_, not what the kernel _does_.

#### How Namespaces Are Created

Three system calls manage namespaces:

| Syscall | Purpose |
|---------|---------|
| `clone()` | Create a new process in new namespaces |
| `unshare()` | Move the _current_ process into new namespaces |
| `setns()` | Join an _existing_ namespace (this is what `nsenter` uses) |

Every process's namespaces are visible in `/proc/<pid>/ns/`:

```bash
$ ls -la /proc/self/ns/
lrwxrwxrwx 1 root root 0 Jan 15 10:00 cgroup -> 'cgroup:[4026531835]'
lrwxrwxrwx 1 root root 0 Jan 15 10:00 ipc -> 'ipc:[4026531839]'
lrwxrwxrwx 1 root root 0 Jan 15 10:00 mnt -> 'mnt:[4026531841]'
lrwxrwxrwx 1 root root 0 Jan 15 10:00 net -> 'net:[4026531840]'
lrwxrwxrwx 1 root root 0 Jan 15 10:00 pid -> 'pid:[4026531836]'
lrwxrwxrwx 1 root root 0 Jan 15 10:00 user -> 'user:[4026531837]'
lrwxrwxrwx 1 root root 0 Jan 15 10:00 uts -> 'uts:[4026531838]'
```

The inode numbers (e.g., `4026531836`) identify the namespace. Two processes with the same inode share the same namespace.

---

### Cgroups (Control Groups)

While namespaces control **visibility**, cgroups control **resource usage**. A cgroup is a hierarchy of processes bound to a set of resource limits.

#### Cgroups v1 vs v2

| Feature | Cgroups v1 | Cgroups v2 |
|---------|-----------|-----------|
| Hierarchy | Multiple trees (one per controller) | Single unified tree |
| Mount point | `/sys/fs/cgroup/<controller>/` | `/sys/fs/cgroup/` |
| Controller assignment | Per-hierarchy | Per-cgroup via `cgroup.subtree_control` |
| Delegation | Complex, error-prone | Clean delegation model |
| Pressure Stall Info (PSI) | ❌ | ✅ |
| Default in modern distros | Legacy | Ubuntu 22+, Fedora 31+, Debian 11+ |

> **Production note:** Kubernetes 1.25+ defaults to cgroups v2. If you're on a modern system, you're almost certainly using v2 already. Check with `stat -fc %T /sys/fs/cgroup/` — `cgroup2fs` means v2.

#### Key Controllers

| Controller | What It Limits | Key Files |
|------------|---------------|-----------|
| **cpu** | CPU time allocation | `cpu.max`, `cpu.weight` |
| **memory** | RAM usage + swap | `memory.max`, `memory.current`, `memory.swap.max` |
| **io** | Block I/O bandwidth | `io.max`, `io.weight` |
| **pids** | Number of processes | `pids.max` |
| **cpuset** | CPU/memory node pinning | `cpuset.cpus`, `cpuset.mems` |

#### Cgroup v2 Unified Hierarchy

```
/sys/fs/cgroup/                          ← root cgroup
├── cgroup.controllers                    ← available controllers
├── cgroup.subtree_control                ← controllers delegated to children
├── system.slice/                         ← systemd system services
│   └── docker.service/                   ← Docker daemon
├── user.slice/                           ← user sessions
└── docker/                               ← Docker containers (if using cgroupfs driver)
    ├── <container-id-1>/
    │   ├── cgroup.procs                  ← PIDs in this cgroup
    │   ├── memory.max                    ← memory limit (e.g., 536870912 = 512MB)
    │   ├── memory.current                ← current memory usage
    │   ├── cpu.max                       ← CPU quota (e.g., "50000 100000" = 50%)
    │   └── pids.max                      ← max number of processes
    └── <container-id-2>/
        └── ...
```

#### How Docker Uses Namespaces + Cgroups Together

When you run `docker run -m 512m --cpus 1.5 -p 8080:80 nginx`:

1. **`clone()` with namespace flags** — creates new PID, NET, MNT, UTS, IPC namespaces
2. **Set up networking** — creates a veth pair, connects one end to `docker0` bridge
3. **Prepare rootfs** — mounts overlay filesystem with image layers
4. **Create cgroup** — writes memory limit (512MB) and CPU quota (150000/100000) to cgroup files
5. **`pivot_root`** — changes the process root to the overlay mount
6. **`exec`** — replaces the setup process with `nginx`

---

## Code Examples

### Example 1: Creating an Isolated Process with `unshare`

```bash
# Create a new PID and UTS namespace
# --fork is required for PID namespace to take effect
# --mount-proc remounts /proc so ps shows only our processes
sudo unshare --pid --uts --mount-proc --fork /bin/bash

# Inside the new namespace:
hostname isolated-container
hostname
# → isolated-container

ps aux
# → Only bash and ps are visible (PID 1 is our bash)

echo $$
# → 1 (we are PID 1 in this namespace!)

# From another terminal on the host:
# ps aux | grep unshare  → shows the real PID
```

### Example 2: Inspecting a Container's Namespaces

```bash
# Start a container
docker run -d --name ns-demo nginx:alpine

# Get the container's PID on the host
CPID=$(docker inspect --format '{{.State.Pid}}' ns-demo)
echo "Container PID on host: $CPID"

# List the container's namespaces
ls -la /proc/$CPID/ns/

# Compare with host namespaces
ls -la /proc/1/ns/

# Enter the container's network namespace
sudo nsenter --target $CPID --net ip addr show

# Enter all namespaces (this is basically what docker exec does)
sudo nsenter --target $CPID --all -- /bin/sh

# Cleanup
docker rm -f ns-demo
```

### Example 3: Reading Cgroup Information

```bash
# Start a container with resource limits
docker run -d --name cgroup-demo --memory 256m --cpus 0.5 nginx:alpine

# Get the container's cgroup path
CONTAINER_ID=$(docker inspect --format '{{.Id}}' cgroup-demo)

# On cgroups v2 systems:
CGROUP_PATH="/sys/fs/cgroup/docker/$CONTAINER_ID"
# Or via systemd: /sys/fs/cgroup/system.slice/docker-$CONTAINER_ID.scope

# Read the limits
cat $CGROUP_PATH/memory.max        # 268435456 (256 * 1024 * 1024)
cat $CGROUP_PATH/memory.current    # Current usage in bytes
cat $CGROUP_PATH/cpu.max           # "50000 100000" (50% of one CPU)
cat $CGROUP_PATH/pids.current      # Number of processes

# Watch memory usage in real time
watch -n 1 cat $CGROUP_PATH/memory.current

# Cleanup
docker rm -f cgroup-demo
```

### Example 4: Setting Memory Limits via Cgroup Filesystem Directly

```bash
# Create a new cgroup (cgroups v2)
sudo mkdir /sys/fs/cgroup/my-container-demo

# Enable memory controller
echo "+memory" | sudo tee /sys/fs/cgroup/cgroup.subtree_control

# Set a 100MB memory limit
echo $((100 * 1024 * 1024)) | sudo tee /sys/fs/cgroup/my-container-demo/memory.max

# Run a process inside this cgroup
echo $$ | sudo tee /sys/fs/cgroup/my-container-demo/cgroup.procs

# Verify the limit
cat /sys/fs/cgroup/my-container-demo/memory.max
# → 104857600

# Check current usage
cat /sys/fs/cgroup/my-container-demo/memory.current

# Cleanup: move process back and remove cgroup
echo $$ | sudo tee /sys/fs/cgroup/cgroup.procs
sudo rmdir /sys/fs/cgroup/my-container-demo
```

---

## Try It Yourself

### Challenge 1: Namespace Detective

Start two Docker containers. For each one, find the PID on the host and compare the namespace inode numbers in `/proc/<pid>/ns/`. Which namespaces are shared? Which are unique?

<details>
<summary>Show solution</summary>

```bash
# Start two containers
docker run -d --name detective-a nginx:alpine
docker run -d --name detective-b nginx:alpine

# Get host PIDs
PID_A=$(docker inspect --format '{{.State.Pid}}' detective-a)
PID_B=$(docker inspect --format '{{.State.Pid}}' detective-b)

echo "Container A PID: $PID_A"
echo "Container B PID: $PID_B"

# Compare namespace inodes
echo "=== Container A ==="
ls -la /proc/$PID_A/ns/

echo "=== Container B ==="
ls -la /proc/$PID_B/ns/

echo "=== Host (PID 1) ==="
ls -la /proc/1/ns/

# Each container has UNIQUE: pid, net, mnt, uts, ipc, cgroup
# They SHARE: user (unless using user namespaces)
# The host has DIFFERENT inodes for all namespaces

# Cleanup
docker rm -f detective-a detective-b
```

</details>

### Challenge 2: OOM Kill Observer

Create a container with a 50MB memory limit, then try to allocate 100MB inside it. Observe how the kernel OOM-kills the process.

<details>
<summary>Show solution</summary>

```bash
# Run a container with 50MB memory limit
docker run -d --name oom-demo --memory 50m ubuntu:22.04 sleep infinity

# Try to allocate more memory than allowed
docker exec oom-demo bash -c '
  # Allocate 100MB by writing to a variable
  python3 -c "
data = bytearray(100 * 1024 * 1024)
print(\"Allocated 100MB successfully\")
  "
'
# → Killed (OOM)

# Check docker events
docker events --filter container=oom-demo --since 1m --until now
# → You'll see an OOM event

# Check kernel logs
dmesg | grep -i oom | tail -5

# Cleanup
docker rm -f oom-demo
```

</details>

---

## Capstone Connection

**DeployForge** containers rely on namespaces and cgroups for every aspect of their isolation:

- **PID namespaces** keep DeployForge's API Gateway process tree separate from the Worker Service. When you debug a stuck container in Module 08 (Observability), you'll trace PID namespaces to find the right process.
- **NET namespaces** give each DeployForge service its own network stack. Module 06 (Networking & Services) builds on this when configuring Kubernetes pod networking.
- **Cgroup memory limits** prevent the Worker Service from consuming all available RAM when processing large batch jobs. When you define resource requests/limits in Module 05, those translate directly to cgroup writes.
- **Understanding these primitives** helps you debug OOM kills, network connectivity issues, and filesystem permission problems — the three most common production container issues you'll encounter with DeployForge.
