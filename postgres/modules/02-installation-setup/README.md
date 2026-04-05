# Module 02 — Installation & Setup

## Overview

This module gets PostgreSQL running on your machine and teaches you the tools you'll use throughout this tutorial. The primary interface is `psql` — PostgreSQL's powerful command-line client — and you'll become comfortable with its meta-commands before reaching for a GUI.

By the end, you'll have the `storeforge` database created and ready for the schema you'll build in Module 03.

---

## Learning Objectives

By the end of this module, you should be able to:

- [ ] Run a PostgreSQL instance using Docker with correct environment variables
- [ ] Connect to PostgreSQL using `psql` and navigate with meta-commands
- [ ] Create databases, list objects, and run SQL files from `psql`
- [ ] Start, stop, and check the status of PostgreSQL using `pg_ctl` or `systemd`
- [ ] Use a GUI tool (DBeaver or pgAdmin) as a complement to `psql`
- [ ] Set up the `storeforge_dev` database with the correct user and password

---

## Topics

| # | File | Concept |
|---|------|---------|
| 1 | [01-installation.md](01-installation.md) | Using Docker, package managers (Homebrew, apt), cloud deployment |
| 2 | [02-psql-basics.md](02-psql-basics.md) | Connecting with `psql`, essential meta-commands, running SQL files |
| 3 | [03-managing-postgres.md](03-managing-postgres.md) | Using systemd, pg_ctl, pg_ctlcluster — start/stop/status/reload |
| 4 | [04-gui-tools.md](04-gui-tools.md) | pgAdmin and DBeaver — visual schema browsing and query running |

---

## Estimated Time

**1–2 hours** (including exercises)

---

## Prerequisites

- Module 01 completed
- Docker installed (or willingness to install PostgreSQL via package manager)

---

## Capstone Milestone

By the end of this module you should have:

1. PostgreSQL 15+ running (Docker container named `storeforge-db`)
2. Connected to it with `psql` successfully
3. Created the `storeforge_dev` database
4. Created a `storeforge` user with password
5. Confirmed the connection string: `postgresql://storeforge:password@localhost:5432/storeforge_dev`
