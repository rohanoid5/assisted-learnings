# Managing PostgreSQL: pg_ctl, systemd, and Configuration Files

## Concept

Once PostgreSQL is installed, you need to understand how to start, stop, reload, and configure it. The tools differ by installation method, but the concepts are the same: PostgreSQL runs as a background service with a data directory, a configuration file, and a process manager.

---

## The PostgreSQL Data Directory

Everything about a PostgreSQL cluster is stored in the **data directory** (`$PGDATA`):

```
$PGDATA/
├── postgresql.conf      — main configuration
├── pg_hba.conf          — client authentication rules
├── pg_ident.conf        — OS username → PostgreSQL username mappings
├── PG_VERSION           — PostgreSQL version number
├── base/                — database files (one subdirectory per database)
├── global/              — cluster-wide tables (pg_database, pg_roles)
├── pg_wal/              — Write-Ahead Log files
├── pg_log/ (or log/)    — server logs
└── postmaster.pid       — PID file (present when server is running)
```

| Method | `$PGDATA` location |
|--------|-------------------|
| Docker | `/var/lib/postgresql/data` |
| Homebrew | `/opt/homebrew/var/postgresql@16` |
| Ubuntu/Debian | `/var/lib/postgresql/16/main` |
| Windows | `C:\Program Files\PostgreSQL\16\data` |

---

## pg_ctl: The Server Control Tool

`pg_ctl` manages a PostgreSQL server process directly.

```bash
# Start the server:
pg_ctl -D /path/to/pgdata start

# Stop the server:
pg_ctl -D /path/to/pgdata stop          # smart: waits for connections to close
pg_ctl -D /path/to/pgdata stop -m fast  # fast: closes connections, rolls back txns
pg_ctl -D /path/to/pgdata stop -m immediate  # emergency only (like SIGKILL)

# Restart (applies most config changes):
pg_ctl -D /path/to/pgdata restart

# Reload (applies HBA and some postgresql.conf changes without full restart):
pg_ctl -D /path/to/pgdata reload

# Status:
pg_ctl -D /path/to/pgdata status

# Set PGDATA to avoid typing it every time:
export PGDATA=/opt/homebrew/var/postgresql@16
pg_ctl status
```

### Homebrew shortcut

```bash
brew services start postgresql@16   # starts and enables on login
brew services stop postgresql@16    # stops service
brew services restart postgresql@16 # restart
brew services list                  # see all Homebrew services and their status
```

---

## systemd (Linux)

On Ubuntu/Debian, PostgreSQL is managed as a systemd service:

```bash
# Start:
sudo systemctl start postgresql@16-main

# Stop:
sudo systemctl stop postgresql@16-main

# Restart:
sudo systemctl restart postgresql@16-main

# Reload (HBA and some conf changes):
sudo systemctl reload postgresql@16-main

# Check status:
sudo systemctl status postgresql@16-main

# Enable on boot:
sudo systemctl enable postgresql@16-main

# Disable:
sudo systemctl disable postgresql@16-main
```

---

## Docker Commands

```bash
# Start/stop the container:
docker start storeforge-postgres
docker stop storeforge-postgres

# View PostgreSQL logs:
docker logs storeforge-postgres
docker logs -f storeforge-postgres      # follow (stream live)
docker logs --tail 50 storeforge-postgres  # last 50 lines

# Get a shell inside the container:
docker exec -it storeforge-postgres bash

# Get a psql shell directly:
docker exec -it storeforge-postgres psql -U storeforge -d storeforge_dev

# Check if PostgreSQL is ready (useful in CI):
docker exec storeforge-postgres pg_isready -U storeforge
# /var/run/postgresql:5432 - accepting connections
```

---

## postgresql.conf: Key Settings

`postgresql.conf` is the main configuration file. Changes to most settings require a restart; some can be reloaded.

### Connection settings

```ini
listen_addresses = '*'        # listen on all interfaces (default: localhost only)
port = 5432                   # default PostgreSQL port
max_connections = 100         # max simultaneous client connections
```

### Memory settings

```ini
shared_buffers = 256MB        # PostgreSQL's internal cache; set to 25% of RAM
work_mem = 4MB                # per-sort, per-hash-join memory; set cautiously
maintenance_work_mem = 64MB   # for VACUUM, CREATE INDEX, ALTER TABLE
```

### WAL settings

```ini
wal_level = replica          # minimal | replica | logical
archive_mode = on            # enable WAL archiving for backups
max_wal_senders = 3          # max replication connections
```

### Logging settings

```ini
log_destination = 'stderr'
logging_collector = on
log_directory = 'log'
log_filename = 'postgresql-%a.log'
log_min_duration_statement = 1000  # log queries slower than 1000ms
log_line_prefix = '%t [%p]: [%l-1] user=%u,db=%d '
```

### Checking current settings

```sql
-- Show all settings:
SHOW ALL;

-- Show a specific setting:
SHOW shared_buffers;

-- See which settings require restart vs. reload:
SELECT name, setting, unit, context
FROM pg_settings
WHERE context IN ('postmaster', 'sighup')  -- postmaster = restart, sighup = reload
ORDER BY context, name;
```

### Changing settings (without editing the file)

```sql
-- Change per-session:
SET work_mem = '64MB';

-- Change for all future sessions (requires superuser, persists across restarts):
ALTER SYSTEM SET shared_buffers = '512MB';
-- Creates/updates postgresql.auto.conf — takes precedence over postgresql.conf
-- Requires restart for most settings

-- After ALTER SYSTEM, reload or restart:
SELECT pg_reload_conf();         -- reload (for sighup context settings)
-- or restart the server for postmaster settings
```

---

## pg_hba.conf: Client Authentication

`pg_hba.conf` controls **who** can connect, **from where**, and **how** they authenticate.

```
# TYPE  DATABASE        USER            ADDRESS                 METHOD
local   all             postgres                                peer
local   all             all                                     md5
host    all             all             127.0.0.1/32            scram-sha-256
host    all             all             ::1/128                 scram-sha-256
host    storeforge_dev  storeforge      0.0.0.0/0               scram-sha-256
```

**Column meanings:**

| Column | Values |
|--------|--------|
| TYPE | `local` (Unix socket), `host` (TCP), `hostssl` (TCP + SSL) |
| DATABASE | `all`, database name, or comma-separated list |
| USER | `all`, username, or `+role` (members of role) |
| ADDRESS | IP/CIDR (for host connections) |
| METHOD | `trust`, `peer`, `md5`, `scram-sha-256`, `reject` |

**Authentication methods:**

| Method | Description |
|--------|-------------|
| `trust` | No password required (⚠️ local dev only) |
| `peer` | OS username must match PostgreSQL username (local sockets) |
| `md5` | Password hashed with MD5 (deprecated; use scram-sha-256) |
| `scram-sha-256` | Secure password challenge-response (recommended) |
| `reject` | Always reject (blacklist specific IPs) |

After editing `pg_hba.conf`:

```bash
# Reload to apply changes (no restart needed):
pg_ctl reload
# or:
sudo systemctl reload postgresql@16-main
# or in psql:
SELECT pg_reload_conf();
```

---

## Try It Yourself

```bash
# 1. If using Docker — check the data directory layout:
docker exec -it storeforge-postgres bash
ls -la /var/lib/postgresql/data/
cat /var/lib/postgresql/data/postgresql.conf | grep shared_buffers

# 2. Check current PostgreSQL settings from psql:
SHOW max_connections;
SHOW work_mem;
SHOW shared_buffers;

-- See all settings that were changed from defaults:
SELECT name, setting, boot_val, reset_val, source
FROM pg_settings
WHERE source != 'default'
ORDER BY name;

-- 3. Check your pg_hba.conf:
SHOW hba_file;
-- Then: \! cat /path/shown/above/pg_hba.conf
-- Or in Docker: docker exec storeforge-postgres cat /var/lib/postgresql/data/pg_hba.conf
```

<details>
<summary>Expected pg_settings query output (Docker)</summary>

Most Docker PostgreSQL settings are left at defaults. You'll see entries like:

```
          name            | setting | source
--------------------------+---------+--------
 listen_addresses         | *       | configuration file
 max_connections          | 100     | configuration file
 log_line_prefix          | ...     | default
```

The `source` column shows whether a setting came from the config file, an environment variable, the command line, or the compiled default.

</details>

---

## Capstone Connection

In Module 09 (Infrastructure), you'll edit `postgresql.conf` to:
- Enable streaming replication (`wal_level = replica`, `max_wal_senders = 3`)
- Enable archiving for backups (`archive_mode = on`)
- Tune memory settings for production workloads

You'll edit `pg_hba.conf` to:
- Allow the standby server's IP to connect for replication
- Lock down application access to use `scram-sha-256` authentication only
