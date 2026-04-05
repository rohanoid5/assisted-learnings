# psql: The PostgreSQL Interactive Terminal

## Concept

`psql` is PostgreSQL's command-line client. It comes with every PostgreSQL installation and lets you run SQL queries, explore the database structure, run script files, and manage sessions. Proficiency with `psql` is a force multiplier — it's faster than any GUI for most operations and works over SSH on remote servers.

---

## Connecting to PostgreSQL

```bash
# Full connection syntax:
psql -h <host> -p <port> -U <user> -d <database>

# Docker setup:
psql -h localhost -p 5432 -U storeforge -d storeforge_dev
# Password prompt appears

# Using a connection URL (useful in scripts):
psql "postgresql://storeforge:secret@localhost:5432/storeforge_dev"

# Environment variables (avoids typing repeatedly):
export PGHOST=localhost
export PGPORT=5432
export PGUSER=storeforge
export PGDATABASE=storeforge_dev
export PGPASSWORD=secret   # ⚠️ only for development — never in production scripts
psql                        # uses env vars automatically
```

### Storing credentials securely: `.pgpass`

```bash
# Create ~/.pgpass with format: host:port:database:user:password
echo "localhost:5432:storeforge_dev:storeforge:secret" >> ~/.pgpass
chmod 600 ~/.pgpass   # MUST be owner-readable only
psql                  # no password prompt
```

---

## The psql Prompt

```
storeforge_dev=#     ← connected as superuser (# means superuser)
storeforge_dev=>     ← connected as regular user (> means non-superuser)
storeforge_dev-#     ← continuation prompt (waiting for ; to end a statement)
storeforge_dev'#     ← inside a string literal (waiting for closing ')
```

If you get stuck in a continuation prompt, type `\q` or `Ctrl+C` to cancel.

---

## Essential Meta-Commands

Meta-commands start with `\` and are processed by psql (not sent to the server):

### Navigation and information

```
\l              — list all databases
\l+             — list databases with more detail (owner, size, encoding)
\c dbname       — connect to a different database
\c - username   — switch user (if you have permission)

\dn             — list schemas in current database
\dn+            — list schemas with owners and privileges

\dt             — list all tables in current schema (public)
\dt *.*         — list all tables in all schemas
\dt schema.*    — list tables in a specific schema

\d tablename    — describe table structure (columns, types, constraints)
\d+ tablename   — describe with storage, statistics, and more

\di             — list indexes
\dv             — list views
\dm             — list materialized views
\df             — list functions
\ds             — list sequences
\dy             — list event triggers
\dp tablename   — show table privileges
```

### Practical examples

```sql
-- Connect and explore:
\c storeforge_dev
\dt

-- Describe the product table:
\d product
--                        Table "public.product"
--     Column        |            Type             | 
-- ------------------+-----------------------------+
--  id               | bigint                      | not null
--  name             | character varying(200)      | not null
--  price            | numeric(10,2)               | not null
-- ...
-- Indexes:
--     "product_pkey" PRIMARY KEY, btree (id)
--     "product_slug_key" UNIQUE CONSTRAINT, btree (slug)

-- Find tables with 'order' in the name:
\dt *order*
```

### Running queries and files

```
\i filename.sql    — execute a SQL file (path relative to current directory)
\ir filename.sql   — execute relative to the script's location (useful in includes)
\e                 — open last query in your $EDITOR (vim, nano, etc.)
\ef funcname       — open a function definition in $EDITOR

\o filename        — redirect output to a file
\o                 — stop redirecting output
\s                 — show command history
\s filename        — save history to file
```

```bash
# Run a SQL file from psql:
\i /path/to/capstone/storeforge/schema.sql

# Or from the shell (without entering psql interactive mode):
psql -h localhost -U storeforge -d storeforge_dev -f schema.sql
psql -h localhost -U storeforge -d storeforge_dev -c "SELECT count(*) FROM product;"
```

### Output formatting

```
\x              — toggle expanded display (one attribute per line, great for wide tables)
\x auto         — auto-switch based on terminal width (recommended)
\pset null '∅'  — display NULL as ∅ instead of empty (easier to spot)
\timing on      — show execution time after each query
\timing off     — stop showing timing

\pset format    — change output format: aligned (default), unaligned, csv, html, asciidoc
\pset border 2  — draw table borders
```

```sql
-- Expanded mode is great for examining a single row:
\x on
SELECT * FROM customer WHERE id = 1;
--
-- -[ RECORD 1 ]-------------------------
-- id         | 1
-- name       | Alice Johnson
-- email      | alice@example.com
-- created_at | 2024-01-15 09:23:11+00
\x off
```

### Transaction control

```
\set AUTOCOMMIT off   — require explicit COMMIT/ROLLBACK
\set AUTOCOMMIT on    — (default) each statement auto-commits
```

### Variables and psql scripting

```sql
-- Set a psql variable:
\set customer_id 42

-- Use it in a query (no quotes for numbers):
SELECT * FROM customer WHERE id = :customer_id;

-- Use with quotes for strings:
\set search_term 'headphone'
SELECT * FROM product WHERE name ILIKE '%' || :'search_term' || '%';

-- gset: store query results into variables
SELECT id AS order_id FROM "order" WHERE customer_id = 1 LIMIT 1 \gset
SELECT * FROM order_item WHERE order_id = :order_id;
```

---

## Shell vs. Interactive Mode

```bash
# Interactive mode (most common for exploration):
psql -h localhost -U storeforge -d storeforge_dev

# Non-interactive (for scripts and CI):
psql -h localhost -U storeforge -d storeforge_dev \
  -c "SELECT count(*) FROM product;" \
  --no-psqlrc \
  --tuples-only          # only data, no headers
  
# Run a file and capture output:
psql ... -f schema.sql > output.log 2>&1

# Stop on first error (useful in migration scripts):
psql ... -v ON_ERROR_STOP=1 -f migration.sql
```

---

## Try It Yourself

Connect to your StoreForge database and explore:

```sql
-- 1. Check your connection:
\conninfo

-- 2. List all schemas:
\dn

-- 3. Enable timing:
\timing on

-- 4. List all tables (should be empty if schema not yet created):
\dt

-- 5. Create a quick test table and describe it:
CREATE TABLE test_psql (
    id      SERIAL PRIMARY KEY,
    label   TEXT NOT NULL,
    value   NUMERIC(8,2) DEFAULT 0.00
);

\d test_psql

-- 6. Enable expanded mode and insert/query:
\x on
INSERT INTO test_psql (label, value) VALUES ('alpha', 3.14) RETURNING *;
\x off

-- 7. Drop the test table:
DROP TABLE test_psql;

-- 8. See query history:
\s
```

<details>
<summary>Expected \d test_psql output</summary>

```
                              Table "public.test_psql"
 Column |         Type          | Collation | Nullable |           Default            
--------+-----------------------+-----------+----------+------------------------------
 id     | integer               |           | not null | nextval('test_psql_id_seq'::regclass)
 label  | text                  |           | not null | 
 value  | numeric(8,2)          |           |          | 0.00
Indexes:
    "test_psql_pkey" PRIMARY KEY, btree (id)
```

Note that `SERIAL` creates a sequence (`test_psql_id_seq`) and sets the default to `nextval()`. This is the older way to do auto-increment; `GENERATED ALWAYS AS IDENTITY` (used by StoreForge) is the SQL-standard alternative.

</details>

---

## Capstone Connection

In Module 10, you'll use psql to:
- Run `schema.sql`, `seed.sql`, `functions.sql`, `security.sql`, and `indexes.sql` with `\i`
- Use `\timing on` to benchmark query performance before and after indexing
- Use `\x auto` to inspect individual order or customer records
- Use `\o report.csv` to export query results for analysis
