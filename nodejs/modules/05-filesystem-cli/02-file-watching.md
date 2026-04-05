# 5.2 — File Watching

## Concept

File watching lets your program react to changes on disk — useful for auto-reloading configs, invalidating caches, or triggering pipeline re-runs. Node.js has a built-in `fs.watch` API, but it's quirky; `chokidar` is the production-grade solution for most use cases.

---

## Deep Dive

### Built-in `fs.watch`

```typescript
import { watch } from 'node:fs';

// Watch a file or directory
const watcher = watch('pipelines/', { recursive: true }, (event, filename) => {
  console.log(`Event: ${event}, file: ${filename}`);
  // event: 'rename' (create/delete) or 'change' (modify)
});

// Stop watching
watcher.close();
```

**Caveats with `fs.watch`:**
- On macOS, directory events may duplicate
- `event` is imprecise — `'rename'` fires for both create and delete
- No built-in debouncing — a single save triggers multiple events
- No glob pattern support

### `fs.watch` with Async Iteration (Node 20+)

```typescript
import { watch } from 'node:fs/promises';
import { AbortController } from 'node:abort_controller';

const controller = new AbortController();

// The async iterator version is cleaner
(async () => {
  try {
    for await (const event of watch('pipelines/', { signal: controller.signal })) {
      console.log(event.eventType, event.filename);
    }
  } catch (err) {
    if (err.name !== 'AbortError') throw err;
  }
})();

// Stop watching after 30s
setTimeout(() => controller.abort(), 30_000);
```

### Debouncing File Events

```typescript
// File systems often fire multiple events for a single save
function debounce<T extends (...args: never[]) => void>(fn: T, ms: number): T {
  let timer: ReturnType<typeof setTimeout> | undefined;
  return ((...args: Parameters<T>) => {
    clearTimeout(timer);
    timer = setTimeout(() => fn(...args), ms);
  }) as T;
}

const handler = debounce((filename: string | null) => {
  console.log(`Processing change: ${filename}`);
  // reload config, trigger re-run, etc.
}, 300);

watch('pipelines/', { recursive: true }, (_event, filename) => {
  handler(filename);
});
```

---

## Try It Yourself

**Exercise:** Build a simple config auto-reloader.

```typescript
interface ReloadableConfig {
  watch(path: string): void;
  get(): Record<string, unknown>;
}

function createWatchedConfig(initialPath: string): ReloadableConfig {
  // TODO: read config on init, watch for changes, reload on change
  // debounce the handler by 200ms
}
```

<details>
<summary>Show solution</summary>

```typescript
import { readFileSync, watch } from 'node:fs';

function createWatchedConfig(initialPath: string) {
  let config: Record<string, unknown> = {};

  function load() {
    try {
      config = JSON.parse(readFileSync(initialPath, 'utf8'));
      console.log('[config] Reloaded');
    } catch (err) {
      console.error('[config] Failed to reload:', err);
    }
  }

  load(); // initial load

  let timer: ReturnType<typeof setTimeout> | undefined;
  watch(initialPath, () => {
    clearTimeout(timer);
    timer = setTimeout(load, 200);
  });

  return { get: () => config };
}
```

</details>

---

## Capstone Connection

PipeForge's `pipeforge --watch` mode uses file watching on the `pipelines/` directory. When a pipeline definition file changes, the watcher triggers a re-validation and re-sync with the database — giving teams a "config-as-code" workflow where pipeline definitions are committed to version control and auto-applied on change.
