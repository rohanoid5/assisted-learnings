# Installing PostgreSQL: Docker, Homebrew, and Linux Packages

## Concept

Before you can run SQL, you need a running PostgreSQL server. PostgreSQL can be installed several ways. This lesson covers the three most common approaches for developers, with Docker as the recommended method — it keeps your host clean, makes version switching trivial, and mirrors how PostgreSQL typically runs in production.

---

## Method 1: Docker (Recommended)

Docker runs PostgreSQL in an isolated container. You get a consistent, reproducible environment with no system-level installation.

### Prerequisites
Install [Docker Desktop](https://www.docker.com/products/docker-desktop/) for your OS.

### Pull and run PostgreSQL 16

```bash
docker run -d \
  --name storeforge-postgres \
  -e POSTGRES_USER=storeforge \
  -e POSTGRES_PASSWORD=secret \
  -e POSTGRES_DB=storeforge_dev \
  -p 5432:5432 \
  -v storeforge_pgdata:/var/lib/postgresql/data \
  postgres:16-alpine
```

**What each flag means:**

| Flag | Purpose |
|------|---------|
| `-d` | Run in background (detached mode) |
| `--name` | Container name for easy reference |
| `-e POSTGRES_USER` | Creates this role as the superuser |
| `-e POSTGRES_PASSWORD` | Password for the superuser |
| `-e POSTGRES_DB` | Creates this database on first start |
| `-p 5432:5432` | Maps host port 5432 → container port 5432 |
| `-v storeforge_pgdata:...` | Named volume persists data between container restarts |
| `postgres:16-alpine` | Official PostgreSQL image; Alpine is smaller |

### Verify the container is running

```bash
docker ps
# CONTAINER ID   IMAGE              STATUS         PORTS                    NAMES
# abc123def456   postgres:16-alpine Up 2 minutes   0.0.0.0:5432->5432/tcp   storeforge-postgres

docker logs storeforge-postgres
# Should end with: database system is ready to accept connections
```

### Connect via psql inside the container

```bash
docker exec -it storeforge-postgres psql -U storeforge -d storeforge_dev
# psql (16.x)
# Type "help" for help.
# storeforge_dev=#
```

### Stop and start the container

```bash
docker stop storeforge-postgres   # stops the container (data persists in volume)
docker start storeforge-postgres  # restarts it
docker rm storeforge-postgres     # deletes the container (data still in volume)
```

### Using Docker Compose (for multi-service projects)

For StoreForge, create a `docker-compose.yml` at the project root:

```yaml
version: '3.8'
services:
  postgres:
    image: postgres:16-alpine
    container_name: storeforge-postgres
    environment:
      POSTGRES_USER: storeforge
      POSTGRES_PASSWORD: secret
      POSTGRES_DB: storeforge_dev
    ports:
      - "5432:5432"
    volumes:
      - pgdata:/var/lib/postgresql/data
      - ./capstone/storeforge:/docker-entrypoint-initdb.d  # auto-runs SQL files on first start

volumes:
  pgdata:
```

```bash
docker compose up -d    # start in background
docker compose down     # stop containers (data persists)
docker compose down -v  # stop AND delete volumes (⚠️ destroys data)
```

---

## Method 2: Homebrew (macOS)

```bash
# Install
brew install postgresql@16

# Start the service (auto-restarts on login)
brew services start postgresql@16

# Connect (default: current user, no password, postgres database)
psql postgres

# Add to PATH (add to ~/.zshrc or ~/.bash_profile)
export PATH="/opt/homebrew/opt/postgresql@16/bin:$PATH"
```

**Homebrew data directory:** `/opt/homebrew/var/postgresql@16/`

**Downside:** Only one PostgreSQL version active at a time without additional tooling (`pgenv` or `asdf`).

---

## Method 3: Linux Packages (Ubuntu/Debian)

```bash
# Add PostgreSQL apt repository
sudo sh -c 'echo "deb https://apt.postgresql.org/pub/repos/apt \
  $(lsb_release -cs)-pgdg main" > /etc/apt/sources.list.d/pgdg.list'

wget --quiet -O - https://www.postgresql.org/media/keys/ACCC4CF8.asc | sudo apt-key add -
sudo apt-get update

# Install PostgreSQL 16
sudo apt-get install -y postgresql-16 postgresql-client-16

# Start service
sudo systemctl start postgresql

# Connect as the postgres system user
sudo -u postgres psql
```

**systemd service unit:** `postgresql@16-main.service` on Debian/Ubuntu

---

## Method 4: Native macOS / Windows Installer

- Download from [postgresql.org/download](https://www.postgresql.org/download)
- Includes pgAdmin, Stack Builder, and command-line tools
- Use for a full graphical installation if you prefer not to use Docker

---

## Verify Your Installation

Regardless of method, verify PostgreSQL is running and connectable:

```bash
# From your host machine (if using Docker or native install):
psql -h localhost -U storeforge -d storeforge_dev
# Password: secret

# Check version:
SELECT version();
-- PostgreSQL 16.x on ... compiled by gcc ...

# List databases:
\l

# Quit psql:
\q
```

---

## Configuration File Locations

| Method | `postgresql.conf` location |
|--------|---------------------------|
| Docker | `/var/lib/postgresql/data/postgresql.conf` |
| Homebrew | `/opt/homebrew/var/postgresql@16/postgresql.conf` |
| Ubuntu/Debian | `/etc/postgresql/16/main/postgresql.conf` |
| Windows installer | `C:\Program Files\PostgreSQL\16\data\postgresql.conf` |

For Docker, you can edit the config by mounting a custom `postgresql.conf`:

```bash
docker exec -it storeforge-postgres bash
cat /var/lib/postgresql/data/postgresql.conf
```

---

## Try It Yourself

1. Start PostgreSQL using your preferred method
2. Connect via psql
3. Run these verification queries:

```sql
-- Check PostgreSQL version:
SELECT version();

-- See current database:
SELECT current_database();

-- See current user:
SELECT current_user;

-- List all databases:
\l

-- Create the StoreForge development database (if not already created by Docker ENV):
CREATE DATABASE storeforge_dev;

-- Connect to it:
\c storeforge_dev

-- Confirm:
SELECT current_database();
```

<details>
<summary>Expected output</summary>

```
version
------------------------------------------------------------
PostgreSQL 16.x on aarch64-unknown-linux-musl, compiled by gcc ...

current_database
-----------------
storeforge_dev

current_user
--------------
storeforge

storeforge_dev=#
```

If you see `psql: error: connection refused`, the PostgreSQL server is not running. For Docker: `docker start storeforge-postgres`. For Homebrew: `brew services start postgresql@16`.

</details>

---

## Capstone Connection

The Docker Compose setup above mounts `./capstone/storeforge/` to `/docker-entrypoint-initdb.d/`. Any `.sql` files in that directory run automatically on the container's first start. This means in Module 10, you'll initialize the full StoreForge schema + seed data with a single `docker compose up`.
