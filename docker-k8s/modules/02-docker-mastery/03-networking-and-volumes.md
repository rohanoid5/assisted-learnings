# 2.3 — Docker Networking & Volumes

## Concept

Containers are ephemeral by design — when a container is removed, its filesystem is gone. And containers run in isolated network namespaces — they can't talk to each other or the host without explicit configuration. Docker networking and volumes solve these two fundamental problems: **how containers communicate** and **how data persists**.

---

## Deep Dive

### Docker Networking Drivers

Docker supports multiple networking drivers. Each creates a different network topology:

```
┌────────────────────────────────────────────────────────────────┐
│                     Docker Networking Drivers                   │
│                                                                 │
│  ┌─────────┐  ┌─────────┐  ┌─────────┐  ┌──────┐  ┌───────┐  │
│  │ bridge  │  │  host   │  │ overlay │  │ none │  │macvlan│  │
│  │         │  │         │  │         │  │      │  │       │  │
│  │ Default │  │ Share   │  │ Multi-  │  │ No   │  │ Real  │  │
│  │ Docker  │  │ host    │  │ host    │  │ net  │  │ MAC   │  │
│  │ bridge  │  │ network │  │ Swarm   │  │      │  │ addr  │  │
│  └─────────┘  └─────────┘  └─────────┘  └──────┘  └───────┘  │
│  Most common   Dev/perf     Swarm only   Security   Legacy/    │
│  for local     testing      (or K8s)     isolation  bare-metal │
└────────────────────────────────────────────────────────────────┘
```

| Driver | Description | When to Use |
|--------|-------------|-------------|
| **bridge** | Virtual bridge network (default). Containers get private IPs. NAT for outbound. | Single-host, most local development |
| **host** | Container shares the host's network namespace. No isolation. | Maximum network performance, avoid port mapping |
| **overlay** | Spans multiple Docker hosts via VXLAN tunneling. | Docker Swarm multi-host communication |
| **none** | No networking at all. Container is completely isolated. | Security-sensitive workloads, batch processing |
| **macvlan** | Assigns a real MAC address to containers. Appears as a physical device on the network. | Legacy apps that need to appear on the LAN |

### User-Defined Bridge Networks vs Default Bridge

Docker creates a `bridge` network (named `bridge`) by default. **Never use it for multi-container apps.** Create your own:

| Feature | Default `bridge` | User-defined bridge |
|---------|-----------------|-------------------|
| DNS resolution | ❌ (IP only) | ✅ (container name → IP) |
| Automatic linking | ❌ (deprecated `--link`) | ✅ (all containers on network) |
| Isolation | Shares with all default containers | Isolated per network |
| Connect/disconnect live | ❌ | ✅ (`docker network connect`) |
| Network-scoped aliases | ❌ | ✅ |

```bash
# Create a user-defined bridge network
docker network create --driver bridge deployforge-net

# Start containers on the network
docker run -d --name postgres --network deployforge-net postgres:15
docker run -d --name redis --network deployforge-net redis:7-alpine
docker run -d --name api --network deployforge-net deployforge-api

# The API can reach postgres and redis BY NAME
docker exec api ping postgres    # resolves to 172.18.0.2
docker exec api ping redis       # resolves to 172.18.0.3
```

### DNS Resolution Between Containers

Docker runs an embedded DNS server at `127.0.0.11` for user-defined networks. Containers resolve each other by name:

```
┌──────────────────────────────────────────────────────────┐
│                deployforge-net (172.18.0.0/16)            │
│                                                           │
│  ┌──────────┐    DNS: "postgres"     ┌──────────────┐    │
│  │   api    │ ─────────────────────▶ │  postgres    │    │
│  │ 172.18.  │    → 172.18.0.3        │  172.18.0.3  │    │
│  │  0.2     │                        │  :5432       │    │
│  └──────────┘                        └──────────────┘    │
│       │                                                   │
│       │          DNS: "redis"        ┌──────────────┐    │
│       └────────────────────────────▶ │    redis     │    │
│                  → 172.18.0.4        │  172.18.0.4  │    │
│                                      │  :6379       │    │
│  Docker embedded DNS: 127.0.0.11     └──────────────┘    │
└──────────────────────────────────────────────────────────┘
```

```bash
# Inspect DNS resolution inside a container
docker exec api cat /etc/resolv.conf
# nameserver 127.0.0.11

# Verify DNS lookup
docker exec api nslookup postgres
# Server:    127.0.0.11
# Name:      postgres
# Address 1: 172.18.0.3

# Network aliases let one container respond to multiple names
docker run -d --name postgres-primary \
  --network deployforge-net \
  --network-alias db \
  --network-alias postgres \
  postgres:15
# Both "db" and "postgres" resolve to this container
```

### Port Mapping and Publishing

Containers with private IPs need port mapping to be reachable from the host:

```bash
# Map host:container ports
docker run -d -p 8080:3000 api          # host:8080 → container:3000
docker run -d -p 5432:5432 postgres      # same port mapping
docker run -d -p 127.0.0.1:6379:6379 redis  # bind to localhost only

# Random host port (useful for testing)
docker run -d -p 3000 api               # Docker picks a free host port
docker port api                          # → 0.0.0.0:49153->3000/tcp

# Publish all exposed ports
docker run -d -P api                     # maps all EXPOSE ports to random host ports
```

> **Security tip:** `-p 6379:6379` binds to `0.0.0.0` — the port is accessible from anywhere. Use `-p 127.0.0.1:6379:6379` to restrict to localhost. This matters for databases and caches that shouldn't be exposed to the network.

---

### Docker Volumes

Volumes are Docker's mechanism for persisting data beyond a container's lifecycle:

```
┌─────────────────────────────────────────────────────────────┐
│                    Volume Types                              │
│                                                              │
│  ┌─────────────────┐  ┌──────────────┐  ┌───────────────┐  │
│  │  Named Volume   │  │  Bind Mount  │  │    tmpfs      │  │
│  │                 │  │              │  │               │  │
│  │ Docker-managed  │  │ Host path    │  │ RAM-only      │  │
│  │ /var/lib/docker │  │ mapped into  │  │ Never touches │  │
│  │ /volumes/       │  │ container    │  │ disk          │  │
│  │                 │  │              │  │               │  │
│  │ ✅ Portable     │  │ ✅ Dev live  │  │ ✅ Secrets    │  │
│  │ ✅ Backupable   │  │    reload    │  │ ✅ Scratch    │  │
│  │ ✅ DB storage   │  │ ⚠️ Host-     │  │ ❌ Not        │  │
│  │                 │  │   dependent  │  │   persistent  │  │
│  └─────────────────┘  └──────────────┘  └───────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

| Type | Syntax | Managed By | Use Case |
|------|--------|-----------|----------|
| **Named volume** | `-v pgdata:/var/lib/postgresql/data` | Docker | Database storage, persistent state |
| **Bind mount** | `-v ./src:/app/src` | Host filesystem | Development hot-reload, config files |
| **tmpfs** | `--tmpfs /tmp` or `--mount type=tmpfs,target=/tmp` | Kernel (RAM) | Sensitive data, scratch space |

### Named Volumes

Docker manages the storage location (usually `/var/lib/docker/volumes/`):

```bash
# Create a named volume
docker volume create pgdata

# Use it with a container
docker run -d --name postgres \
  -v pgdata:/var/lib/postgresql/data \
  -e POSTGRES_PASSWORD=secret \
  postgres:15

# Data persists across container restarts and removals
docker rm -f postgres
docker run -d --name postgres-new \
  -v pgdata:/var/lib/postgresql/data \
  -e POSTGRES_PASSWORD=secret \
  postgres:15
# → All your data is still there

# Inspect volume details
docker volume inspect pgdata
# → Shows mount point, driver, creation date
```

### Bind Mounts

Map a host directory directly into the container. Essential for development:

```bash
# Mount source code for live reloading
docker run -d --name api-dev \
  -v $(pwd)/src:/app/src:ro \
  -v $(pwd)/package.json:/app/package.json:ro \
  -p 3000:3000 \
  deployforge-api:dev

# Flags:
# :ro  — read-only (container can't modify host files)
# :rw  — read-write (default)
# :cached  — (macOS) optimized for host reads
# :delegated — (macOS) optimized for container reads
```

> **macOS/Windows performance:** Bind mounts on Docker Desktop are slow because files are synced between the host and the Linux VM. For `node_modules`, use a named volume instead of bind-mounting from the host — this avoids syncing thousands of small files.

### tmpfs Mounts

RAM-based storage that never touches disk:

```bash
# Sensitive data that shouldn't be written to disk
docker run -d --name api \
  --tmpfs /tmp:rw,noexec,nosuid,size=100m \
  deployforge-api

# Or with --mount syntax (more explicit)
docker run -d --name api \
  --mount type=tmpfs,target=/tmp,tmpfs-size=104857600 \
  deployforge-api
```

### Data Persistence Patterns for Databases

```bash
# PostgreSQL — named volume for data directory
docker run -d --name postgres \
  -v pgdata:/var/lib/postgresql/data \
  -v ./init.sql:/docker-entrypoint-initdb.d/init.sql:ro \
  -e POSTGRES_USER=deployforge \
  -e POSTGRES_PASSWORD=secret \
  -e POSTGRES_DB=deployforge_dev \
  postgres:15

# Redis — named volume for append-only file
docker run -d --name redis \
  -v redisdata:/data \
  redis:7-alpine redis-server --appendonly yes
```

### Volume Lifecycle

```bash
# List all volumes
docker volume ls

# Inspect a volume
docker volume inspect pgdata

# Remove unused volumes (careful in production!)
docker volume prune

# Remove a specific volume
docker volume rm pgdata
# → Error if a container is using it. Stop/remove the container first.
```

### Backup and Restore Strategies

```bash
# Backup a volume to a tar archive
docker run --rm \
  -v pgdata:/data:ro \
  -v $(pwd):/backup \
  alpine tar czf /backup/pgdata-backup.tar.gz -C /data .

# Restore from backup
docker volume create pgdata-restored
docker run --rm \
  -v pgdata-restored:/data \
  -v $(pwd):/backup:ro \
  alpine tar xzf /backup/pgdata-backup.tar.gz -C /data

# PostgreSQL-specific backup (better for DBs)
docker exec postgres pg_dump -U deployforge deployforge_dev > backup.sql

# PostgreSQL restore
docker exec -i postgres psql -U deployforge deployforge_dev < backup.sql
```

---

## Code Examples

### Example 1: Creating and Inspecting Networks

```bash
# Create a user-defined network
docker network create \
  --driver bridge \
  --subnet 172.20.0.0/16 \
  --gateway 172.20.0.1 \
  deployforge-net

# Inspect the network
docker network inspect deployforge-net
# → Shows subnet, gateway, connected containers

# Start services on the network
docker run -d --name postgres \
  --network deployforge-net \
  -e POSTGRES_PASSWORD=secret \
  postgres:15

docker run -d --name redis \
  --network deployforge-net \
  redis:7-alpine

# Verify DNS resolution
docker run --rm --network deployforge-net alpine nslookup postgres
# Name:      postgres
# Address 1: 172.20.0.2

docker run --rm --network deployforge-net alpine nslookup redis
# Name:      redis
# Address 1: 172.20.0.3

# List all networks
docker network ls

# See which containers are on a network
docker network inspect deployforge-net --format '{{range .Containers}}{{.Name}} {{.IPv4Address}}{{"\n"}}{{end}}'
# postgres 172.20.0.2/16
# redis 172.20.0.3/16

# Clean up
docker rm -f postgres redis
docker network rm deployforge-net
```

### Example 2: Volume Lifecycle Management

```bash
# Create named volumes for DeployForge
docker volume create deployforge-pgdata
docker volume create deployforge-redisdata

# Start PostgreSQL with persistent storage
docker run -d --name postgres \
  -v deployforge-pgdata:/var/lib/postgresql/data \
  -e POSTGRES_USER=deployforge \
  -e POSTGRES_PASSWORD=secret \
  -e POSTGRES_DB=deployforge_dev \
  postgres:15

# Insert some data
docker exec -i postgres psql -U deployforge deployforge_dev << 'SQL'
CREATE TABLE deployments (id SERIAL PRIMARY KEY, name TEXT, status TEXT);
INSERT INTO deployments (name, status) VALUES ('v1.0', 'success'), ('v1.1', 'pending');
SELECT * FROM deployments;
SQL

# Remove the container (data lives in the volume)
docker rm -f postgres

# Start a NEW container with the SAME volume
docker run -d --name postgres-new \
  -v deployforge-pgdata:/var/lib/postgresql/data \
  -e POSTGRES_USER=deployforge \
  -e POSTGRES_PASSWORD=secret \
  -e POSTGRES_DB=deployforge_dev \
  postgres:15

# Data survived!
docker exec postgres-new psql -U deployforge deployforge_dev -c "SELECT * FROM deployments;"
#  id | name | status
# ----+------+---------
#   1 | v1.0 | success
#   2 | v1.1 | pending

# Clean up
docker rm -f postgres-new
docker volume rm deployforge-pgdata deployforge-redisdata
```

### Example 3: Debugging Network Connectivity

```bash
# Start two containers
docker network create debug-net
docker run -d --name server --network debug-net nginx:alpine
docker run -d --name client --network debug-net alpine sleep infinity

# Test connectivity
docker exec client ping -c 3 server
docker exec client wget -qO- http://server:80

# Install and use network tools
docker exec client apk add --no-cache curl bind-tools tcpdump

# DNS lookup
docker exec client dig server
docker exec client nslookup server

# HTTP request with headers
docker exec client curl -v http://server:80

# Packet capture (needs --cap-add NET_RAW or --privileged)
docker run -d --name sniffer \
  --network debug-net \
  --cap-add NET_RAW \
  alpine sh -c "apk add --no-cache tcpdump && tcpdump -i eth0 -c 20"

# View container network interfaces
docker exec client ip addr show
docker exec client ip route show

# Clean up
docker rm -f server client sniffer
docker network rm debug-net
```

---

## Try It Yourself

### Challenge 1: Multi-Service Networking

Set up three containers on a custom bridge network:
1. A PostgreSQL database
2. A Redis cache
3. An Alpine "client" container

From the client, verify you can:
- Resolve both services by name
- Connect to PostgreSQL on port 5432
- Connect to Redis on port 6379

<details>
<summary>Show solution</summary>

```bash
# Create the network
docker network create challenge-net

# Start services
docker run -d --name pg \
  --network challenge-net \
  -e POSTGRES_PASSWORD=secret \
  postgres:15

docker run -d --name cache \
  --network challenge-net \
  redis:7-alpine

# Start client with tools
docker run -d --name client \
  --network challenge-net \
  alpine sleep infinity

# Install tools
docker exec client apk add --no-cache postgresql-client redis

# Test DNS resolution
docker exec client nslookup pg
# → Should resolve to 172.x.x.x

docker exec client nslookup cache
# → Should resolve to 172.x.x.x

# Test PostgreSQL connectivity
docker exec client pg_isready -h pg -p 5432
# → pg:5432 - accepting connections

# Test Redis connectivity
docker exec client redis-cli -h cache ping
# → PONG

# Verify from network inspect
docker network inspect challenge-net --format \
  '{{range .Containers}}{{.Name}}: {{.IPv4Address}}{{"\n"}}{{end}}'

# Clean up
docker rm -f pg cache client
docker network rm challenge-net
```

</details>

### Challenge 2: Volume Persistence Proof

Demonstrate that data survives container removal:
1. Create a named volume
2. Start a PostgreSQL container using the volume
3. Create a table and insert data
4. Remove the container
5. Start a new PostgreSQL container with the same volume
6. Verify the data is still there

<details>
<summary>Show solution</summary>

```bash
# Step 1: Create volume
docker volume create proof-pgdata

# Step 2: Start PostgreSQL
docker run -d --name pg-proof \
  -v proof-pgdata:/var/lib/postgresql/data \
  -e POSTGRES_USER=test \
  -e POSTGRES_PASSWORD=secret \
  -e POSTGRES_DB=proof_db \
  postgres:15

# Wait for startup
sleep 5

# Step 3: Create table and insert data
docker exec pg-proof psql -U test proof_db -c "
  CREATE TABLE evidence (id SERIAL, msg TEXT, ts TIMESTAMPTZ DEFAULT now());
  INSERT INTO evidence (msg) VALUES ('Data created by container 1');
  INSERT INTO evidence (msg) VALUES ('This must survive container removal');
  SELECT * FROM evidence;
"

# Step 4: Remove the container (not the volume!)
docker rm -f pg-proof

# Verify volume still exists
docker volume ls | grep proof-pgdata
# → local     proof-pgdata

# Step 5: New container, same volume
docker run -d --name pg-proof-2 \
  -v proof-pgdata:/var/lib/postgresql/data \
  -e POSTGRES_USER=test \
  -e POSTGRES_PASSWORD=secret \
  -e POSTGRES_DB=proof_db \
  postgres:15

sleep 5

# Step 6: Verify data
docker exec pg-proof-2 psql -U test proof_db -c "SELECT * FROM evidence;"
#  id |                 msg                  |             ts
# ----+--------------------------------------+----------------------------
#   1 | Data created by container 1          | 2024-01-15 10:00:00+00
#   2 | This must survive container removal  | 2024-01-15 10:00:00+00

echo "✅ Data persisted across container removal!"

# Clean up
docker rm -f pg-proof-2
docker volume rm proof-pgdata
```

</details>

---

## Capstone Connection

**DeployForge** uses networking and volumes extensively:

- **User-defined bridge network** — All DeployForge services communicate over a `deployforge-net` bridge network. The API Gateway connects to PostgreSQL at `postgres:5432` and Redis at `redis:6379` — no hardcoded IPs, just DNS names. When Compose creates this network, it automatically adds DNS for each service name.
- **Named volumes for databases** — `deployforge-pgdata` stores PostgreSQL data and `deployforge-redisdata` stores the Redis append-only file. These volumes persist across `docker compose down` and `docker compose up` cycles, so your development data survives restarts.
- **Bind mounts for development** — In the development Compose override, source code is bind-mounted into containers (`./src:/app/src`) for hot-reloading. `node_modules` uses a named volume (not a bind mount) to avoid macOS/Windows performance issues.
- **Port mapping** — Only Nginx (`:80`) and optionally the API Gateway (`:3000`) are exposed to the host. PostgreSQL and Redis are only accessible within the Docker network — not from the host — following the principle of least exposure.
- **Backup strategy** — The exercises introduce `pg_dump` and volume-based backup patterns. In Module 07 (Storage & Configuration), we'll extend this to Kubernetes PersistentVolumes with automated backup CronJobs.

In the next topic (2.4), we'll tie networking and volumes together with Docker Compose to orchestrate the entire DeployForge stack with a single command.
