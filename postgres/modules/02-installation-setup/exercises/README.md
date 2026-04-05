# Module 02 Exercises: Installation and Setup

## Overview

By the end of these exercises, you will have a working PostgreSQL environment, a `storeforge_dev` database, and confidence with both psql and a GUI client.

---

## Exercise 1 — Launch PostgreSQL

Start PostgreSQL using your chosen method and verify connectivity:

```bash
# Docker:
docker run -d \
  --name storeforge-postgres \
  -e POSTGRES_USER=storeforge \
  -e POSTGRES_PASSWORD=secret \
  -e POSTGRES_DB=storeforge_dev \
  -p 5432:5432 \
  -v storeforge_pgdata:/var/lib/postgresql/data \
  postgres:16-alpine

# Verify it's running:
docker ps | grep storeforge-postgres

# Check PostgreSQL is accepting connections:
docker exec storeforge-postgres pg_isready -U storeforge
```

Expected output from `pg_isready`:
```
/var/run/postgresql:5432 - accepting connections
```

If you see `no response` or `rejecting connections`, wait 5 seconds and try again — the container may still be initializing.

<details>
<summary>Troubleshooting</summary>

**Port already in use:** Another PostgreSQL instance is running on port 5432. Either stop it or map to a different host port: `-p 5433:5432` and then `psql -p 5433`.

**Container exits immediately:** Check logs: `docker logs storeforge-postgres`. Common cause: wrong `POSTGRES_PASSWORD` format or volume permissions issue.

**psql: command not found:** Install PostgreSQL client tools:
- macOS: `brew install libpq` then add to PATH
- Ubuntu: `sudo apt install postgresql-client-16`
- Or run psql inside Docker: `docker exec -it storeforge-postgres psql -U storeforge`

</details>

---

## Exercise 2 — Explore with psql

Connect to your database and answer each question using psql meta-commands:

```bash
psql -h localhost -p 5432 -U storeforge -d storeforge_dev
```

```sql
-- 1. What version of PostgreSQL are you running?
SELECT version();

-- 2. What is the current database and user?
SELECT current_database(), current_user;

-- 3. What databases exist on this server?
\l

-- 4. What schemas exist in storeforge_dev?
\dn

-- 5. Enable timing and run a trivial query — how long does it take?
\timing on
SELECT 1 + 1;

-- 6. What is the path to the pg_hba.conf file?
SHOW hba_file;

-- 7. What is the current value of max_connections?
SHOW max_connections;

-- 8. Find all settings whose name contains 'memory':
SELECT name, setting, unit, short_desc
FROM pg_settings
WHERE name LIKE '%memory%' OR name LIKE '%mem%';
```

Record your answers. You'll reference this environment setup in later modules.

<details>
<summary>Reference answers for Docker setup</summary>

```
version() → PostgreSQL 16.x on aarch64-unknown-linux-musl...
current_database() → storeforge_dev
current_user → storeforge
\l → shows storeforge_dev, postgres, template0, template1
\dn → public schema (owner: storeforge)
timing → typically 0.100 ms to 1 ms for SELECT 1+1
hba_file → /var/lib/postgresql/data/pg_hba.conf
max_connections → 100
memory settings → work_mem (4MB), maintenance_work_mem (64MB), shared_buffers (128MB)
```

</details>

---

## Exercise 3 — Create the StoreForge Database Structure

Create a minimal version of the StoreForge schema to verify your setup:

```sql
-- Connect to storeforge_dev first:
\c storeforge_dev

-- Create the category table:
CREATE TABLE category (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    parent_id   BIGINT REFERENCES category(id),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Create the customer table:
CREATE TABLE customer (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name        TEXT NOT NULL,
    email       VARCHAR(255) NOT NULL UNIQUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Verify they were created:
\dt

-- Describe each table:
\d category
\d customer

-- Insert test data:
INSERT INTO category (name) VALUES ('Electronics'), ('Clothing'), ('Books');
INSERT INTO customer (name, email) VALUES 
    ('Alice Johnson', 'alice@example.com'),
    ('Bob Smith', 'bob@example.com');

-- Query:
SELECT * FROM category;
SELECT * FROM customer;

-- Clean up (we'll re-create properly with schema.sql later):
DROP TABLE customer;
DROP TABLE category;
```

<details>
<summary>Expected output</summary>

```sql
-- \dt output:
         List of relations
 Schema |   Name   | Type  |    Owner    
--------+----------+-------+-------------
 public | category | table | storeforge
 public | customer | table | storeforge

-- SELECT * FROM category:
 id |    name     | parent_id |          created_at          
----+-------------+-----------+-----------------------------
  1 | Electronics |           | 2024-01-15 10:00:00+00
  2 | Clothing    |           | 2024-01-15 10:00:00+00
  3 | Books       |           | 2024-01-15 10:00:00+00

-- Note: parent_id shows as empty (NULL) because \pset null is not set
-- Run: \pset null '∅' to see it display as ∅
```

</details>

---

## Exercise 4 — Configure psql for Daily Use

Create a `.psqlrc` file to set your preferred psql defaults:

```bash
# Create/edit ~/.psqlrc:
cat > ~/.psqlrc << 'EOF'
-- Display NULLs visibly
\pset null '∅'

-- Auto-expand wide results
\x auto

-- Show timing
\timing on

-- Nicer prompt showing user@host/database
\set PROMPT1 '%n@%m/%/ %# '

-- Show errors verbosely
\set VERBOSITY verbose

-- Don't type 'yes' to confirm destructive operations
\set AUTOCOMMIT on
EOF
```

Restart psql and verify your settings take effect:

```bash
psql -h localhost -U storeforge -d storeforge_dev
# Prompt should now show: storeforge@localhost/storeforge_dev #
```

<details>
<summary>Tip: psqlrc location</summary>

`~/.psqlrc` is loaded automatically every time you start psql. For a shared project, you can override with `PSQLRC=/path/to/project.psqlrc psql ...`.

Useful additions to `.psqlrc`:
```
\set HISTFILE ~/.psql_history- :DBNAME  -- separate history per database
\set HISTCONTROL ignoredups            -- deduplicate history
\set COMP_KEYWORD_CASE upper           -- autocomplete keywords in UPPERCASE
```

</details>

---

## Exercise 5 — GUI Client Setup Verification

Complete these tasks in DBeaver (or pgAdmin):

```
□ Create a new connection to localhost:5432 / storeforge_dev / storeforge
□ Test the connection successfully
□ Browse the schema tree — find the 'public' schema
□ Open the Query Editor
□ Run: SELECT version();
□ Run: SELECT current_timestamp;
□ Use autocomplete to build: SELECT * FROM pg_se[TAB] → pg_settings
□ Run the pg_settings query and browse results in the data grid
□ View the EXPLAIN plan for: SELECT * FROM pg_settings WHERE name LIKE '%mem%'
```

No code output to verify here — the task is confirmed by completing it in the GUI.

---

## Capstone Checkpoint ✅

Before moving to Module 03, confirm:

- [ ] PostgreSQL is running and you can connect with psql
- [ ] You know how to start/stop the server (Docker, Homebrew, or systemd)
- [ ] You have a DBeaver or pgAdmin connection to `storeforge_dev`
- [ ] You understand the difference between `postgresql.conf` (settings) and `pg_hba.conf` (authentication)
- [ ] You know at least 8 psql meta-commands by heart (`\l`, `\c`, `\dt`, `\d`, `\dn`, `\i`, `\x`, `\timing`)
- [ ] You know how to run a `.sql` file both from psql (`\i`) and from the shell (`psql -f`)
