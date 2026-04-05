# 7.5 — Plugin Architecture

## Concept

A plugin architecture lets you define extension points in your application that third parties (or your own team) can implement. Rather than coupling PipeForge to a fixed set of step types, plugins register handlers for step types at startup — making the step type list open-ended and independently deployable.

---

## Deep Dive

### Plugin Registry

```typescript
// src/plugins/registry.ts
type StepHandler = (input: unknown, signal: AbortSignal) => Promise<unknown>;

interface Plugin {
  name: string;           // Unique ID: 'csv-import', 'webhook', etc.
  stepType: string;       // Matches Step.type in the DB schema
  handler: StepHandler;
  schema?: ZodSchema;     // Optional input validation
}

class PluginRegistry {
  private plugins = new Map<string, Plugin>();

  register(plugin: Plugin): void {
    if (this.plugins.has(plugin.stepType)) {
      throw new Error(`Plugin for step type '${plugin.stepType}' already registered`);
    }
    this.plugins.set(plugin.stepType, plugin);
    console.log(`[plugins] Registered: ${plugin.name} (${plugin.stepType})`);
  }

  get(stepType: string): Plugin {
    const plugin = this.plugins.get(stepType);
    if (!plugin) throw new Error(`No plugin registered for step type: ${stepType}`);
    return plugin;
  }

  list(): string[] {
    return [...this.plugins.keys()];
  }
}

export const registry = new PluginRegistry();
```

### Built-in Plugins

```typescript
// src/plugins/built-in/csv-import.ts
import { registry } from '../registry.js';
import { pipeline } from 'node:stream/promises';
import { CsvToJsonTransform } from '../../transforms/csv-parser.js';

registry.register({
  name: 'CSV Importer',
  stepType: 'CSV_IMPORT',
  handler: async (input, signal) => {
    const { filePath } = input as { filePath: string };
    const rows: unknown[] = [];
    await pipeline(
      createReadStream(filePath),
      new CsvToJsonTransform(),
      new Writable({
        objectMode: true,
        write(row, _, cb) { rows.push(row); cb(); },
      }),
      { signal },
    );
    return { rows, count: rows.length };
  },
});

// src/plugins/built-in/webhook.ts
registry.register({
  name: 'Webhook Emitter',
  stepType: 'WEBHOOK',
  handler: async (input, signal) => {
    const { url, payload } = input as { url: string; payload: unknown };
    const res = await fetch(url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
      signal,
    });
    return { status: res.status, ok: res.ok };
  },
});
```

### Loading External Plugins

```typescript
// Load plugins from node_modules (npm package pattern)
const pluginPackages = env.PLUGIN_PACKAGES?.split(',') ?? [];
for (const pkg of pluginPackages) {
  await import(pkg); // Each package calls registry.register() on import
}
```

---

## Try It Yourself

**Exercise:** Implement a `DELAY` plugin that waits N milliseconds:

```typescript
registry.register({
  name: 'Delay',
  stepType: 'DELAY',
  handler: async (input, signal) => {
    // TODO: Wait for input.ms milliseconds, respecting the signal
    // Hint: use timers/promises setTimeout with { signal }
  },
});
```

<details>
<summary>Show solution</summary>

```typescript
import { setTimeout as delay } from 'node:timers/promises';

registry.register({
  name: 'Delay',
  stepType: 'DELAY',
  handler: async (input, signal) => {
    const { ms } = input as { ms: number };
    await delay(ms, undefined, { signal });
    return { waited: ms };
  },
});
```

</details>

---

## Capstone Connection

PipeForge's step execution in `PipelineEngine` resolves the plugin for each step's `type` via `registry.get(step.type)`, then calls the handler with the step's `config` as input. This makes adding a new step type purely additive — no changes to the engine.
