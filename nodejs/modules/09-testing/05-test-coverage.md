# 9.5 — Test Coverage

## Concept

Coverage measures which lines, branches, functions, and statements were exercised by tests. 100% coverage doesn't mean bug-free; meaningful coverage thresholds (70–80%) push you to test edge cases and error paths that accidentally-missed branches often hide.

---

## Deep Dive

### Coverage Reports

```bash
# Run with V8 coverage
npx vitest run --coverage

# Output:
# -------------------------|---------|----------|---------|---------|
# File                     | % Stmts | % Branch | % Funcs | % Lines |
# -------------------------|---------|----------|---------|---------|
# src/lib/cursor.ts        |   100   |   100    |   100   |   100   |
# src/domain/circuit-brkr  |    85   |    78    |   100   |    85   |
# src/plugins/registry.ts  |    60   |    50    |    75   |    60   |  ← below threshold!
```

### Vitest Coverage Config

```typescript
// vitest.config.ts
export default defineConfig({
  test: {
    coverage: {
      provider: 'v8',           // or 'istanbul'
      reporter: ['text', 'lcov', 'html'],  // html = browsable report in coverage/
      reportsDirectory: './coverage',
      thresholds: {
        lines: 70,
        branches: 70,
        functions: 70,
        statements: 70,
        // Per-file thresholds also supported:
        // 'src/domain/**': { lines: 90 },
      },
      include: ['src/**'],
      exclude: [
        'src/**/*.test.ts',
        'src/test/**',
        'prisma/**',
        'src/db/client.ts',     // generated Prisma client
      ],
    },
  },
});
```

### Reading an HTML Report

```bash
npx vitest run --coverage
open coverage/index.html
# Click on a file → see exactly which lines are red (not covered)
# Red lines are usually: error handlers, edge cases, else branches
```

### Coverage in CI

```yaml
# .github/workflows/ci.yml  (reference, not a PipeForge file)
- name: Test with coverage
  run: npx vitest run --coverage --reporter=verbose --reporter=junit --outputFile=test-results.xml

- name: Fail if below threshold
  run: |
    # Vitest exits with code 1 if thresholds not met — CI job fails automatically
    echo "Coverage thresholds enforced by Vitest config"

- name: Upload coverage
  uses: codecov/codecov-action@v4
  with:
    files: ./coverage/lcov.info
```

### What to Cover vs What to Skip

```
✅ Cover:
  - Business logic (engine, plugins, validators)
  - Error handling branches
  - Data transformation functions
  - Auth middleware

⬜ Skip with intent (add istanbul ignore):
  - Unreachable defensive checks
  - Generated code (Prisma client)
  - Configuration bootstrapping
  - Type-only files
```

```typescript
/* istanbul ignore next */
function neverHappens() { process.exit(1); }
```

---

## Try It Yourself

**Exercise:** Look at your coverage report and find one uncovered branch. Write a test to cover it, then re-run coverage to verify the increase.

```bash
npx vitest run --coverage
open coverage/index.html
# Find a red branch → write the missing test → re-run
```

---

## Capstone Connection

PipeForge enforces 70% coverage across all thresholds in CI. The `coverage/index.html` report is uploaded as a CI artifact. Three areas consistently need attention: the `CircuitBreaker` HALF_OPEN branch, the `PluginRegistry` dynamic import error path, and the `env.ts` cross-field validation `superRefine` branch.
