# Module 01 — Exercises

Hands-on practice with Linux namespaces, cgroups, OCI images, and container internals. Complete these exercises on a Linux system (native, VM, or WSL2).

> **⚠️ Prerequisite:** These exercises require root access and a Linux kernel. macOS Docker Desktop won't work for the namespace/cgroup exercises (1, 3, 4). Exercise 2 works on any system with Docker.

---

## Exercise 1: Build a Container from Scratch

**Goal:** Create a minimal container using only Linux primitives — no Docker, no containerd. This is the most important exercise in the module.

### Steps

1. **Download a minimal rootfs:**

```bash
mkdir -p ~/container-from-scratch/rootfs
cd ~/container-from-scratch

# Export an alpine rootfs
docker export $(docker create alpine:latest) | tar -C rootfs -xf -
```

2. **Create an isolated process with namespaces:**

```bash
# Create new PID, UTS, mount, and IPC namespaces
sudo unshare --pid --uts --mount --ipc --fork /bin/bash
```

3. **Set up the filesystem:**

```bash
# Inside the unshared shell:

# Mount proc for the new PID namespace
mount -t proc proc ~/container-from-scratch/rootfs/proc

# Change the root filesystem
cd ~/container-from-scratch
pivot_root rootfs rootfs/.old_root

# Mount essential filesystems
mount -t sysfs sys /sys
mount -t tmpfs tmp /tmp

# Clean up the old root
umount -l /.old_root
rmdir /.old_root
```

4. **Set the hostname:**

```bash
hostname my-container
exec /bin/sh  # Get a clean shell
```

5. **Apply a cgroup memory limit (from a second terminal):**

```bash
# Find the PID of the unshared process on the host
CPID=$(pgrep -f "unshare.*--pid")

# Create a cgroup and set a 50MB memory limit
sudo mkdir -p /sys/fs/cgroup/my-scratch-container
echo "+memory" | sudo tee /sys/fs/cgroup/cgroup.subtree_control
echo $((50 * 1024 * 1024)) | sudo tee /sys/fs/cgroup/my-scratch-container/memory.max

# Add the process to the cgroup
echo $CPID | sudo tee /sys/fs/cgroup/my-scratch-container/cgroup.procs
```

### Verification

Inside the container, confirm:

```bash
# PID 1 is your shell
echo $$    # Should print 1

# Hostname is isolated
hostname   # Should print "my-container"

# Only your processes are visible
ps aux     # Should show only sh and ps

# Check the memory limit from inside
cat /proc/self/cgroup
# → Shows your cgroup path
```

From the host, confirm:

```bash
# The container process is visible with a different PID
ps aux | grep "bin/sh"

# The cgroup has the correct limit
cat /sys/fs/cgroup/my-scratch-container/memory.max
# → 52428800
```

<details>
<summary>Show solution</summary>

Full script that does everything end-to-end:

```bash
#!/bin/bash
set -euo pipefail

ROOTFS_DIR="$HOME/container-from-scratch/rootfs"
CGROUP_NAME="scratch-container-$$"
CGROUP_PATH="/sys/fs/cgroup/$CGROUP_NAME"

echo "=== Step 1: Create rootfs ==="
mkdir -p "$ROOTFS_DIR"
docker export $(docker create alpine:latest) | tar -C "$ROOTFS_DIR" -xf -

echo "=== Step 2: Create cgroup with 50MB memory limit ==="
sudo mkdir -p "$CGROUP_PATH"
echo "+memory +pids" | sudo tee /sys/fs/cgroup/cgroup.subtree_control > /dev/null
echo $((50 * 1024 * 1024)) | sudo tee "$CGROUP_PATH/memory.max" > /dev/null
echo 100 | sudo tee "$CGROUP_PATH/pids.max" > /dev/null

echo "=== Step 3: Launch isolated process ==="
# This script runs INSIDE the new namespaces
cat > "$HOME/container-from-scratch/init.sh" << 'INIT'
#!/bin/sh
mount -t proc proc /proc
mount -t sysfs sys /sys
mount -t tmpfs tmp /tmp

hostname scratch-container

echo "=== Container Info ==="
echo "PID: $$"
echo "Hostname: $(hostname)"
echo "Processes:"
ps aux
echo "Memory limit:"
cat /sys/fs/cgroup/memory.max 2>/dev/null || echo "(not visible from inside)"
echo ""
echo "You're inside a container built from scratch!"
echo "Type 'exit' to leave."

exec /bin/sh
INIT
chmod +x "$HOME/container-from-scratch/init.sh"

# Launch with all the isolation
sudo unshare \
  --pid \
  --uts \
  --mount \
  --ipc \
  --fork \
  --cgroup \
  chroot "$ROOTFS_DIR" /bin/sh -c '
    mount -t proc proc /proc
    mount -t sysfs sys /sys
    hostname scratch-container
    echo "PID: $$"
    echo "Hostname: $(hostname)"
    ps aux
    exec /bin/sh
  '

echo "=== Cleanup ==="
sudo rmdir "$CGROUP_PATH" 2>/dev/null || true
rm -rf "$HOME/container-from-scratch"
echo "Done."
```

</details>

---

## Exercise 2: Inspect OCI Image Layers

**Goal:** Understand the internal structure of an OCI image by pulling, extracting, and inspecting its components.

### Steps

1. **Install `skopeo` (if not already available):**

```bash
# macOS
brew install skopeo

# Ubuntu/Debian
sudo apt install skopeo

# Or use crane: go install github.com/google/go-containerregistry/cmd/crane@latest
```

2. **Copy an image to a local OCI layout:**

```bash
mkdir -p ~/oci-inspect
skopeo copy docker://nginx:1.25-alpine oci:$HOME/oci-inspect/nginx-oci:latest
```

3. **Explore the OCI layout structure:**

```bash
find ~/oci-inspect/nginx-oci -type f | head -20
cat ~/oci-inspect/nginx-oci/oci-layout
cat ~/oci-inspect/nginx-oci/index.json | jq .
```

4. **Read the manifest and config:**

```bash
# Get manifest digest from index.json
MANIFEST_DIGEST=$(cat ~/oci-inspect/nginx-oci/index.json | jq -r '.manifests[0].digest' | cut -d: -f2)

# Read the manifest
cat ~/oci-inspect/nginx-oci/blobs/sha256/$MANIFEST_DIGEST | jq .

# Get config digest from manifest
CONFIG_DIGEST=$(cat ~/oci-inspect/nginx-oci/blobs/sha256/$MANIFEST_DIGEST | jq -r '.config.digest' | cut -d: -f2)

# Read the config (contains Dockerfile history, env vars, cmd, etc.)
cat ~/oci-inspect/nginx-oci/blobs/sha256/$CONFIG_DIGEST | jq .
```

5. **Extract and inspect a layer:**

```bash
# Get the first layer digest
LAYER_DIGEST=$(cat ~/oci-inspect/nginx-oci/blobs/sha256/$MANIFEST_DIGEST | jq -r '.layers[0].digest' | cut -d: -f2)

# List files in the layer (it's a tar.gz)
tar -tzf ~/oci-inspect/nginx-oci/blobs/sha256/$LAYER_DIGEST | head -30
```

### Verification

Answer these questions:
- How many layers does `nginx:1.25-alpine` have?
- What's the total compressed size of all layers?
- What command created the largest layer?
- What environment variables are set in the config?
- What is the default CMD?

<details>
<summary>Show solution</summary>

```bash
cd ~/oci-inspect/nginx-oci

# Get manifest digest
MANIFEST_DIGEST=$(cat index.json | jq -r '.manifests[0].digest' | cut -d: -f2)
MANIFEST="blobs/sha256/$MANIFEST_DIGEST"

echo "=== Number of layers ==="
cat $MANIFEST | jq '.layers | length'
# → Typically 7-8 layers

echo "=== Total compressed size ==="
cat $MANIFEST | jq '[.layers[].size] | add / 1024 / 1024 | round | tostring + " MB"'

echo "=== Layer sizes ==="
cat $MANIFEST | jq '.layers | to_entries[] | {
  layer: (.key + 1),
  size_mb: (.value.size / 1024 / 1024 * 100 | round / 100)
}'

# Get config
CONFIG_DIGEST=$(cat $MANIFEST | jq -r '.config.digest' | cut -d: -f2)
CONFIG="blobs/sha256/$CONFIG_DIGEST"

echo "=== Dockerfile history (what created each layer) ==="
cat $CONFIG | jq '[.history[] | {
  created_by: .created_by,
  empty: (.empty_layer // false)
}]'

echo "=== Environment variables ==="
cat $CONFIG | jq '.config.Env'

echo "=== Default CMD ==="
cat $CONFIG | jq '.config.Cmd'

echo "=== Entrypoint ==="
cat $CONFIG | jq '.config.Entrypoint'

# Cleanup
rm -rf ~/oci-inspect
```

</details>

---

## Exercise 3: Namespace Exploration

**Goal:** Write a script that comprehensively explores the namespaces of a running Docker container.

### Steps

1. **Start a container to inspect:**

```bash
docker run -d --name ns-explore --hostname my-app nginx:alpine
```

2. **Write a namespace exploration script:**

Create `explore-namespaces.sh`:

```bash
#!/bin/bash
set -euo pipefail

CONTAINER=${1:?"Usage: $0 <container-name>"}

# Get the container's PID on the host
CPID=$(docker inspect --format '{{.State.Pid}}' "$CONTAINER")
echo "Container: $CONTAINER"
echo "Host PID: $CPID"
echo ""

# List all namespaces
echo "=== Namespace Inodes ==="
echo "Container namespaces:"
ls -la /proc/$CPID/ns/ 2>/dev/null
echo ""
echo "Host namespaces (PID 1):"
ls -la /proc/1/ns/ 2>/dev/null
echo ""

# Enter each namespace and report
echo "=== PID Namespace ==="
sudo nsenter --target $CPID --pid --mount -- ps aux

echo ""
echo "=== Network Namespace ==="
sudo nsenter --target $CPID --net -- ip addr show
echo ""
sudo nsenter --target $CPID --net -- ip route show
echo ""
sudo nsenter --target $CPID --net -- cat /etc/resolv.conf

echo ""
echo "=== UTS Namespace ==="
sudo nsenter --target $CPID --uts -- hostname

echo ""
echo "=== Mount Namespace ==="
sudo nsenter --target $CPID --mount -- mount | head -20

echo ""
echo "=== User Info ==="
sudo nsenter --target $CPID --pid --mount -- id
```

3. **Run it:**

```bash
chmod +x explore-namespaces.sh
sudo ./explore-namespaces.sh ns-explore
```

### Verification

Your script should show:
- Container processes (PID 1 = nginx master)
- Container network (eth0 with a 172.17.x.x address)
- Container hostname (`my-app`)
- Container mounts (overlay filesystem)

<details>
<summary>Show solution</summary>

```bash
#!/bin/bash
set -euo pipefail

CONTAINER=${1:?"Usage: $0 <container-name>"}

CPID=$(docker inspect --format '{{.State.Pid}}' "$CONTAINER")
echo "============================================"
echo " Namespace Explorer"
echo " Container: $CONTAINER"
echo " Host PID:  $CPID"
echo "============================================"
echo ""

# Compare namespace inodes
echo "--- Namespace Comparison ---"
printf "%-10s %-25s %-25s %-10s\n" "NS" "Container" "Host" "Shared?"
for ns in /proc/$CPID/ns/*; do
  NS_NAME=$(basename "$ns")
  CONTAINER_INODE=$(readlink "$ns" 2>/dev/null || echo "N/A")
  HOST_INODE=$(readlink "/proc/1/ns/$NS_NAME" 2>/dev/null || echo "N/A")
  if [ "$CONTAINER_INODE" = "$HOST_INODE" ]; then
    SHARED="YES"
  else
    SHARED="no"
  fi
  printf "%-10s %-25s %-25s %-10s\n" "$NS_NAME" "$CONTAINER_INODE" "$HOST_INODE" "$SHARED"
done
echo ""

echo "--- PID Namespace ---"
sudo nsenter --target "$CPID" --pid --mount -- ps aux 2>/dev/null || echo "  (could not enter PID namespace)"
echo ""

echo "--- Network Namespace ---"
echo "Interfaces:"
sudo nsenter --target "$CPID" --net -- ip -br addr show 2>/dev/null || echo "  (could not enter NET namespace)"
echo "Routes:"
sudo nsenter --target "$CPID" --net -- ip route show 2>/dev/null
echo "DNS:"
sudo nsenter --target "$CPID" --net -- cat /etc/resolv.conf 2>/dev/null
echo ""

echo "--- UTS Namespace ---"
echo "Hostname: $(sudo nsenter --target "$CPID" --uts -- hostname 2>/dev/null)"
echo ""

echo "--- Mount Namespace (first 15 mounts) ---"
sudo nsenter --target "$CPID" --mount -- mount 2>/dev/null | head -15
echo ""

echo "--- Cgroup Info ---"
cat /proc/$CPID/cgroup 2>/dev/null
echo ""

echo "============================================"
echo " Exploration complete"
echo "============================================"

# Cleanup reminder
echo ""
echo "Don't forget: docker rm -f $CONTAINER"
```

</details>

---

## Exercise 4: Cgroup Resource Limits

**Goal:** Create a cgroup, assign a process to it, set CPU and memory limits, and observe throttling and OOM behavior.

### Steps

1. **Create a cgroup and set limits:**

```bash
# Create a test cgroup
CGROUP_NAME="exercise-04-$$"
sudo mkdir -p /sys/fs/cgroup/$CGROUP_NAME

# Enable controllers
echo "+cpu +memory +pids" | sudo tee /sys/fs/cgroup/cgroup.subtree_control

# Set limits
echo $((100 * 1024 * 1024)) | sudo tee /sys/fs/cgroup/$CGROUP_NAME/memory.max  # 100MB
echo "50000 100000" | sudo tee /sys/fs/cgroup/$CGROUP_NAME/cpu.max             # 50% of 1 CPU
echo 50 | sudo tee /sys/fs/cgroup/$CGROUP_NAME/pids.max                         # max 50 processes
```

2. **Observe CPU throttling:**

```bash
# Run a CPU-intensive process in the cgroup
sudo sh -c "echo $$ > /sys/fs/cgroup/$CGROUP_NAME/cgroup.procs && \
  dd if=/dev/urandom of=/dev/null bs=1M &"

# Monitor CPU usage — it should be capped at 50%
top -p $(cat /sys/fs/cgroup/$CGROUP_NAME/cgroup.procs | head -1)

# Check throttling stats
cat /sys/fs/cgroup/$CGROUP_NAME/cpu.stat
# Look for "throttled_usec" — time spent throttled
```

3. **Observe memory limits:**

```bash
# Try to allocate more than the limit
sudo sh -c "echo $$ > /sys/fs/cgroup/$CGROUP_NAME/cgroup.procs && \
  python3 -c '
import sys
blocks = []
try:
    while True:
        blocks.append(bytearray(10 * 1024 * 1024))  # 10MB chunks
        print(f\"Allocated {len(blocks) * 10}MB\", file=sys.stderr)
except MemoryError:
    print(f\"OOM at {len(blocks) * 10}MB\", file=sys.stderr)
'"

# Watch memory events
cat /sys/fs/cgroup/$CGROUP_NAME/memory.events
# Look for: oom, oom_kill, oom_group_kill
```

4. **Observe PID limits:**

```bash
# Try to fork more than 50 processes
sudo sh -c "echo $$ > /sys/fs/cgroup/$CGROUP_NAME/cgroup.procs && \
  for i in \$(seq 1 60); do sleep 999 & done"
# → Should fail after 50 processes with "Resource temporarily unavailable"
```

### Verification

```bash
# Confirm limits are set
echo "Memory limit: $(cat /sys/fs/cgroup/$CGROUP_NAME/memory.max) bytes"
echo "CPU limit: $(cat /sys/fs/cgroup/$CGROUP_NAME/cpu.max)"
echo "PID limit: $(cat /sys/fs/cgroup/$CGROUP_NAME/pids.max)"
echo "Current PIDs: $(cat /sys/fs/cgroup/$CGROUP_NAME/pids.current)"
echo "Current memory: $(cat /sys/fs/cgroup/$CGROUP_NAME/memory.current) bytes"
echo "CPU throttle stats:"
cat /sys/fs/cgroup/$CGROUP_NAME/cpu.stat
echo "Memory events:"
cat /sys/fs/cgroup/$CGROUP_NAME/memory.events
```

<details>
<summary>Show solution</summary>

```bash
#!/bin/bash
set -euo pipefail

CGROUP_NAME="exercise-04-$$"
CGROUP_PATH="/sys/fs/cgroup/$CGROUP_NAME"

cleanup() {
  echo "Cleaning up..."
  # Kill all processes in the cgroup
  if [ -f "$CGROUP_PATH/cgroup.procs" ]; then
    while read pid; do
      kill -9 "$pid" 2>/dev/null || true
    done < "$CGROUP_PATH/cgroup.procs"
  fi
  sleep 1
  sudo rmdir "$CGROUP_PATH" 2>/dev/null || true
  echo "Done."
}
trap cleanup EXIT

echo "=== Creating cgroup: $CGROUP_NAME ==="
sudo mkdir -p "$CGROUP_PATH"
echo "+cpu +memory +pids" | sudo tee /sys/fs/cgroup/cgroup.subtree_control > /dev/null

echo "=== Setting limits ==="
echo $((100 * 1024 * 1024)) | sudo tee "$CGROUP_PATH/memory.max" > /dev/null
echo "50000 100000" | sudo tee "$CGROUP_PATH/cpu.max" > /dev/null
echo 50 | sudo tee "$CGROUP_PATH/pids.max" > /dev/null

echo "Memory limit: $(cat $CGROUP_PATH/memory.max) bytes (100MB)"
echo "CPU limit: $(cat $CGROUP_PATH/cpu.max) (50%)"
echo "PID limit: $(cat $CGROUP_PATH/pids.max)"

echo ""
echo "=== Test 1: CPU Throttling ==="
# Run CPU stress in the cgroup for 5 seconds
sudo sh -c "echo \$\$ > $CGROUP_PATH/cgroup.procs && timeout 5 dd if=/dev/urandom of=/dev/null bs=1M 2>/dev/null" &
sleep 6
echo "CPU stats after throttling:"
cat "$CGROUP_PATH/cpu.stat"

echo ""
echo "=== Test 2: Memory Limit ==="
sudo sh -c "echo \$\$ > $CGROUP_PATH/cgroup.procs && python3 -c '
import sys
blocks = []
try:
    while True:
        blocks.append(bytearray(10 * 1024 * 1024))
        print(f\"Allocated {len(blocks) * 10}MB\", file=sys.stderr)
except (MemoryError, OSError) as e:
    print(f\"Hit limit at {len(blocks) * 10}MB: {e}\", file=sys.stderr)
'" 2>&1 || true
echo "Memory events:"
cat "$CGROUP_PATH/memory.events"

echo ""
echo "=== Test 3: PID Limit ==="
sudo sh -c "echo \$\$ > $CGROUP_PATH/cgroup.procs && (
  COUNT=0
  for i in \$(seq 1 60); do
    if sleep 999 & then
      COUNT=\$((COUNT + 1))
    else
      echo \"Failed to fork at process \$COUNT\"
      break
    fi
  done 2>&1
  echo \"Successfully forked \$COUNT processes\"
  kill \$(jobs -p) 2>/dev/null
)" 2>&1 || true
echo "PID count: $(cat $CGROUP_PATH/pids.current)"

echo ""
echo "=== Summary ==="
echo "All three resource limits demonstrated successfully."
echo "- CPU was throttled (check throttled_usec in cpu.stat)"
echo "- Memory allocation was killed/limited at ~100MB"
echo "- PID creation failed after 50 processes"
```

</details>

---

## Capstone Checkpoint

Before moving to Module 02, make sure you can answer these questions:

### Namespaces
1. What are the 8 Linux namespace types and what does each isolate?
2. What's the difference between `unshare` and `nsenter`?
3. How can you tell which namespace a process belongs to by looking at `/proc`?
4. When you `docker exec` into a container, which syscall is used to join the container's namespaces?

### Cgroups
5. What's the difference between cgroups v1 and v2?
6. How does Docker set a memory limit of 512MB for a container?
7. What happens when a container exceeds its memory cgroup limit?
8. How would you diagnose a container being CPU-throttled?

### OCI Spec
9. What are the three components of an OCI image?
10. How does content-addressable storage enable layer deduplication?
11. What's the difference between a high-level runtime (containerd) and a low-level runtime (runc)?
12. What does an OCI runtime bundle's `config.json` contain?

### Containers vs VMs
13. Why can't a Linux container run a Windows binary?
14. What security advantage do VMs have over containers?
15. When would you choose Kata Containers or gVisor over standard runc?
16. Why does container startup time matter for Kubernetes autoscaling?
