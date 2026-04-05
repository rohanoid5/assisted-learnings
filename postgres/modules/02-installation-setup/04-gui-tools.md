# GUI Tools: DBeaver and pgAdmin

## Concept

While `psql` is the most powerful tool for experienced users, GUI database clients reduce friction for visual schema exploration, query writing with autocomplete, and result browsing. This lesson covers DBeaver (cross-platform, free) and pgAdmin 4 (official PostgreSQL tool).

Both tools connect to your PostgreSQL server over the same port (5432) as psql — no additional server setup needed.

---

## Tool Comparison

| Feature | DBeaver | pgAdmin 4 |
|---------|---------|-----------|
| **License** | Community edition (free), Pro (paid) | Open-source (PostgreSQL License) |
| **Platform** | Windows, macOS, Linux | Windows, macOS, Linux, Docker |
| **Installation** | Desktop app | Desktop app or web server |
| **Database support** | 80+ databases (MySQL, SQLite, MongoDB…) | PostgreSQL-only |
| **ERD generator** | ✅ (Community) | ✅ |
| **Query editor** | Excellent autocomplete | Good |
| **Data editor** | ✅ | ✅ |
| **Import/Export** | CSV, JSON, Excel | CSV, JSON |
| **Explain visualizer** | ✅ Graphical plan | ✅ Graphical plan |
| **Best for** | Working with multiple database types | Deep PostgreSQL administration |

---

## DBeaver

### Installation

Download the DBeaver Community Edition from [dbeaver.io](https://dbeaver.io/download/).

On macOS with Homebrew:
```bash
brew install --cask dbeaver-community
```

### Connecting to StoreForge

1. Open DBeaver → **Database** → **New Database Connection**
2. Select **PostgreSQL**
3. Fill in connection details:

| Field | Value |
|-------|-------|
| Host | `localhost` |
| Port | `5432` |
| Database | `storeforge_dev` |
| Username | `storeforge` |
| Password | `secret` |

4. Click **Test Connection** — DBeaver will download the JDBC driver automatically if needed
5. Click **Finish**

### Key features for daily use

**Schema browser (left panel):**
```
storeforge_dev
  └── Databases
       └── storeforge_dev
            └── Schemas
                 └── public
                      ├── Tables
                      │    ├── customer
                      │    ├── product
                      │    ├── order
                      │    └── ...
                      ├── Views
                      ├── Sequences
                      └── Functions
```

**ER Diagram:** Right-click a schema → **View Diagram** — DBeaver generates an ERD showing all tables and their foreign key relationships visually.

**Query editor:**
- `Ctrl+Space` — autocomplete (table names, column names, keywords)
- `Ctrl+Enter` — execute current query
- `Ctrl+Shift+Enter` — execute all queries
- `Ctrl+/` — comment/uncomment selection

**Data editor:** Double-click any table → **Data** tab → edit rows inline (with caution — changes commit immediately unless you turn on manual commit mode)

**Export data:**
Right-click any table or query result → **Export Data** → choose CSV, JSON, SQL INSERT, etc.

**Explain plan:**
Run a query → click the **Explain Plan** button (lightning bolt) → see a visual tree of the execution plan

---

## pgAdmin 4

### Installation

Download from [pgadmin.org](https://www.pgadmin.org/download/) or run as a Docker container:

```bash
docker run -d \
  --name pgadmin \
  -e PGADMIN_DEFAULT_EMAIL=admin@storeforge.local \
  -e PGADMIN_DEFAULT_PASSWORD=admin \
  -p 8080:80 \
  --link storeforge-postgres:postgres \
  dpage/pgadmin4
```

Then open `http://localhost:8080` in your browser.

### Connecting to StoreForge

1. Open pgAdmin → right-click **Servers** → **Register** → **Server**
2. **General** tab → Name: `StoreForge Dev`
3. **Connection** tab:

| Field | Value |
|-------|-------|
| Host name/address | `localhost` (or `postgres` if in same Docker network) |
| Port | `5432` |
| Maintenance database | `storeforge_dev` |
| Username | `storeforge` |
| Password | `secret` |

4. Click **Save**

### Key pgAdmin features

**Object browser:**
Expand: Servers → StoreForge Dev → Databases → storeforge_dev → Schemas → public → Tables

**Query tool:**
- Tools → Query Tool (or press F5 with a database selected)
- Run SQL with `F5` or the play button
- View graphical EXPLAIN plan

**Dashboard:**
- Click your server → **Dashboard** tab
- See active connections, transaction rate, block I/O in real time
- Useful for monitoring during load tests

**Table statistics:**
Right-click any table → **Statistics** → see live row counts, sequential scans, index scans, dead tuples

---

## Recommended Workflow

For StoreForge development:

```
Daily schema exploration  →  DBeaver (faster, better UI)
Writing complex queries   →  psql or DBeaver query editor
Monitoring the server     →  pgAdmin Dashboard
Schema migrations         →  psql (run .sql files with \i or -f)
Viewing EXPLAIN plans     →  Either tool's graphical plan viewer
Production admin          →  psql over SSH (no GUI on server)
```

---

## Try It Yourself

1. Install DBeaver Community
2. Create a connection to your StoreForge database
3. Complete these tasks in the DBeaver UI:

```
□ Connect and browse the schema tree
□ If schema.sql has been run: generate an ER Diagram for the 'public' schema
□ Open the Query Editor and run:
    SELECT table_name, table_type
    FROM information_schema.tables
    WHERE table_schema = 'public'
    ORDER BY table_name;

□ Use Ctrl+Space autocomplete while typing:
    SELECT * FROM cus[CTRL+SPACE]   ← should autocomplete to 'customer'

□ Try the Explain Plan button on this query:
    EXPLAIN SELECT * FROM product WHERE price < 50;
```

<details>
<summary>information_schema.tables expected output</summary>

If you haven't run `schema.sql` yet, you'll see an empty result. After running it:

```
  table_name  | table_type
--------------+------------
 address      | BASE TABLE
 audit_log    | BASE TABLE
 category     | BASE TABLE
 customer     | BASE TABLE
 order        | BASE TABLE
 order_item   | BASE TABLE
 product      | BASE TABLE
 review       | BASE TABLE
```

The `EXPLAIN` result on a small table will show a **Seq Scan** (sequential scan) — PostgreSQL doesn't use an index when the table is small enough that scanning every row is faster.

</details>

---

## Capstone Connection

In Module 08 (Performance Tuning), you'll use DBeaver's graphical EXPLAIN plan to visualize how indexes change query execution — from Seq Scan to Index Scan to Bitmap Index Scan — without having to parse `EXPLAIN` text output manually.
