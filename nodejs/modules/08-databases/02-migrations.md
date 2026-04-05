# 8.2 — Database Migrations

## Concept

Migrations are versioned, incremental changes to your database schema. Prisma Migrate generates SQL migration files from your schema diff and applies them in order — giving you a reproducible schema history you can commit to version control.

---

## Deep Dive

### Prisma Migrate Workflow

```bash
# 1. Edit schema.prisma — add a new field
# 2. Create a migration (generates SQL + applies it to dev DB)
npx prisma migrate dev --name add_pipeline_description

# 3. Apply migrations in production (no interactive prompts)
npx prisma migrate deploy

# 4. Check migration status
npx prisma migrate status

# 5. Reset dev DB (drops all data + re-applies all migrations)
npx prisma migrate reset
```

### A Migration File

When you run `migrate dev`, Prisma creates:
```
prisma/
  migrations/
    20240115_initial/
      migration.sql
    20240210_add_pipeline_description/
      migration.sql
```

The SQL is generated from the diff between your current schema and the previous migration.

### Schema Evolution Examples

```prisma
// Adding a nullable column (non-breaking):
model Pipeline {
  description String? // Add this — no data migration needed
}

// Adding a required column (breaking — needs a default or data migration):
model Pipeline {
  version Int @default(1) // @default handles existing rows
}

// Renaming a field — Prisma asks if it's a rename or drop+add:
// Run: prisma migrate dev --name rename_config_to_settings
// Then manually edit the migration SQL if Prisma guessed wrong
```

### Seeding

```typescript
// prisma/seed.ts
import { db } from '../src/db/client.js';

async function main() {
  await db.user.upsert({
    where: { email: 'admin@pipeforge.io' },
    create: {
      email: 'admin@pipeforge.io',
      name: 'Admin',
      role: 'ADMIN',
      passwordHash: await bcrypt.hash('changeme', 12),
    },
    update: {},
  });
  console.log('Seeded admin user');
}

main().then(() => db.$disconnect()).catch((e) => { console.error(e); db.$disconnect(); process.exit(1); });
```

```json
// package.json
{
  "prisma": { "seed": "tsx prisma/seed.ts" }
}
```

```bash
npx prisma db seed
```

---

## Try It Yourself

**Exercise:** Write a migration to add a `webhook_url` column to the `Pipeline` table (nullable).

1. Add `webhookUrl String?` to `Pipeline` in `schema.prisma`
2. Run `npx prisma migrate dev --name add_pipeline_webhook_url`
3. Verify the migration SQL was created in `prisma/migrations/`

---

## Capstone Connection

PipeForge's initial migration in `prisma/migrations/` creates the full schema from Topic 1's `schema.prisma`. Each module exercise that adds a column (e.g., `webhookUrl`) adds a new migration file — building a real migration history.
