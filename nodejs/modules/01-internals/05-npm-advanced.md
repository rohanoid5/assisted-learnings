# 1.5 — npm: Advanced Usage

## Concept

npm is more than a package installer. Understanding semantic versioning deeply, lockfile mechanics, workspaces, and package authoring makes you a more effective Node.js developer — and prevents the "it works on my machine" class of bugs.

---

## Deep Dive

### Semantic Versioning

Every npm package version is `MAJOR.MINOR.PATCH`:

| Part | When to increment | Example |
|------|------------------|---------|
| `MAJOR` | Breaking change — public API changed | `1.2.3 → 2.0.0` |
| `MINOR` | New feature — backward-compatible | `1.2.3 → 1.3.0` |
| `PATCH` | Bug fix — backward-compatible | `1.2.3 → 1.2.4` |

Version ranges in `package.json`:

```json
{
  "dependencies": {
    "express": "^4.18.3",  // ≥4.18.3, <5.0.0 (caret: allow minor + patch)
    "zod":     "~3.22.4",  // ≥3.22.4, <3.23.0 (tilde: allow patch only)
    "prisma":  "5.10.0",   // exactly 5.10.0 (no range)
    "chalk":   "*"          // any version (dangerous — never use in production)
  }
}
```

### Lockfiles: The Source of Truth

`package-lock.json` (npm) pins the **exact** installed version of every dependency and transitive dependency. This ensures reproducible installs across machines and time.

```bash
# Installs using the lockfile (CI/production)
npm ci          # Fails if package-lock.json is missing or out of sync

# Installs and updates the lockfile (development)
npm install     # May update lockfile if newer patch/minor versions are available

# Check for outdated packages
npm outdated

# Update a single package
npm update express
```

**Never commit `node_modules/`. Always commit `package-lock.json`.**

### npm Workspaces (Monorepo)

```json
// root package.json
{
  "name": "pipeforge-monorepo",
  "private": true,
  "workspaces": [
    "packages/*",
    "apps/*"
  ]
}
```

```bash
# Installs all workspace dependencies (hoisted to root node_modules)
npm install

# Run a script in a specific workspace
npm run build --workspace=packages/core

# Run a script across all workspaces
npm run test --workspaces

# Add a dependency to a specific workspace
npm install zod --workspace=apps/api
```

### npx: Running Without Installing

```bash
npx ts-node src/script.ts          # Run without global install
npx prisma generate                # Run local bin
npx --yes create-next-app@latest   # Download, run, delete (--yes skips prompt)

# Under the hood: npx looks in ./node_modules/.bin/ first,
# then falls back to downloading and running the package
```

### Creating a Package

```json
{
  "name": "@pipeforge/core",
  "version": "1.0.0",
  "description": "PipeForge core pipeline engine",
  "type": "module",
  "main": "./dist/index.js",
  "types": "./dist/index.d.ts",
  "exports": {
    ".": {
      "import": "./dist/index.js",
      "types": "./dist/index.d.ts"
    },
    "./plugins": {
      "import": "./dist/plugins/index.js",
      "types": "./dist/plugins/index.d.ts"
    }
  },
  "files": ["dist/"],
  "scripts": {
    "build": "tsc",
    "prepublishOnly": "npm run build"
  }
}
```

The `exports` field is the modern way to define package entry points. It supports conditional exports (CommonJS vs ESM) and prevents consumers from importing internal files.

### Useful npm Commands

```bash
npm ls                    # List installed packages and their versions
npm ls --depth=0          # Top-level only
npm why express           # Why is this package installed?
npm pack                  # Create a .tgz tarball (test before publishing)
npm link                  # Link a local package globally for testing
npm audit                 # Check for security vulnerabilities
npm audit fix             # Auto-fix vulnerabilities where possible
```

---

## Code Examples

### Package Scripts Pattern

```json
{
  "scripts": {
    "dev":        "node --watch --loader ts-node/esm src/api/server.ts",
    "build":      "tsc && npm run build:clean",
    "build:clean":"rimraf dist && tsc",
    "start":      "node dist/api/server.js",
    "test":       "node --test 'tests/**/*.test.ts'",
    "lint":       "eslint . --ext .ts",
    "typecheck":  "tsc --noEmit",
    "precommit":  "npm run typecheck && npm run lint",
    "prepare":    "npm run build"
  }
}
```

**Script lifecycle hooks:** `pre<script>` and `post<script>` run automatically:

```bash
npm run build
# Runs: prebuild → build → postbuild
```

---

## Try It Yourself

**Exercise:** Explore your PipeForge `package.json`.

1. Run `npm outdated` in `capstone/pipeforge/` — are any packages behind?
2. Run `npm ls --depth=0` — how many direct dependencies do you have?
3. Run `npm why zod` — what other packages in the tree depend on zod?
4. Run `npm pack --dry-run` — what files would be published if this were a package?

<details>
<summary>Key things to notice</summary>

- `npm outdated` shows the current, wanted (per semver range), and latest version
- `npm why` traces the dependency graph — useful for understanding why a transitive dep exists
- `npm pack --dry-run` respects the `files` field and `.npmignore` — your `src/` might be excluded

</details>

---

## Capstone Connection

PipeForge's `package.json` demonstrates several of these patterns:
- **`"type": "module"`** — ESM throughout the project
- **`exports` field** (if splitting into workspace packages) — controls what consumers can import
- **`scripts`** — `dev`, `build`, `test`, `db:*` lifecycle scripts
- **`engines` field** — documents the minimum Node.js version requirement

In Module 07 (Plugin Architecture), the plugin system uses `npm link` during development to test locally-developed plugins against PipeForge without publishing them to the registry.
