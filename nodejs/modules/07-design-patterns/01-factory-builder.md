# 7.1 — Factory & Builder Patterns

## Concept

Factory functions create objects without using `new` directly — they encapsulate construction logic, enforce invariants, and make the creation site readable. The Builder pattern is a fluent interface for constructing complex objects step-by-step.

---

## Deep Dive

### Factory Functions

```typescript
// ⚠️ Class-based construction — tight coupling, hard to mock
const job = new Job(db, engine, logger, pipelineId, userId, retryPolicy);

// ✅ Factory function — encapsulates construction, returns a clean interface
interface Job {
  id: string;
  run(): Promise<void>;
  cancel(): void;
}

function createJob(pipelineId: string, userId: string): Job {
  const id = randomUUID();
  const controller = new AbortController();

  return {
    id,
    async run() {
      // Construction details hidden here
      await engine.execute(id, pipelineId, { signal: controller.signal });
    },
    cancel() {
      controller.abort();
    },
  };
}

const job = createJob(pipelineId, userId); // much cleaner!
```

### Abstract Factory (for test doubles)

```typescript
// Define the interface
interface StorageService {
  upload(key: string, data: Buffer): Promise<string>;
  download(key: string): Promise<Buffer>;
}

// Production factory
function createS3Storage(bucket: string): StorageService {
  return {
    async upload(key, data) {
      // real S3 upload
      return `s3://${bucket}/${key}`;
    },
    async download(key) {
      // real S3 download
    },
  };
}

// Test factory (in-memory)
function createMemoryStorage(): StorageService & { store: Map<string, Buffer> } {
  const store = new Map<string, Buffer>();
  return {
    store,
    async upload(key, data) { store.set(key, data); return `mem://${key}`; },
    async download(key) {
      const data = store.get(key);
      if (!data) throw new Error(`Not found: ${key}`);
      return data;
    },
  };
}
```

### Builder Pattern

```typescript
// Complex pipeline configuration with many optional fields
class PipelineBuilder {
  private config: Partial<PipelineConfig> = {};

  name(name: string): this {
    this.config.name = name;
    return this;
  }

  step(name: string, type: StepType): this {
    this.config.steps = [...(this.config.steps ?? []), { name, type }];
    return this;
  }

  schedule(cron: string): this {
    this.config.schedule = cron;
    return this;
  }

  retryPolicy(attempts: number, backoffMs: number): this {
    this.config.retry = { attempts, backoffMs };
    return this;
  }

  build(): PipelineConfig {
    if (!this.config.name) throw new Error('Pipeline name is required');
    if (!this.config.steps?.length) throw new Error('At least one step is required');
    return this.config as PipelineConfig;
  }
}

// Usage — reads like a DSL
const config = new PipelineBuilder()
  .name('Daily ETL')
  .step('Extract', 'CSV_IMPORT')
  .step('Transform', 'TRANSFORM')
  .step('Load', 'DB_INSERT')
  .schedule('0 2 * * *')
  .retryPolicy(3, 1000)
  .build();
```

---

## Try It Yourself

**Exercise:** Implement a `JobLogBuilder` for constructing `JobLog` entries:

```typescript
class JobLogBuilder {
  // .forJob(jobId).atStep(stepName).level('ERROR').message('...').build()
}
```

<details>
<summary>Show solution</summary>

```typescript
interface JobLogEntry {
  jobId: string;
  stepName: string;
  level: 'INFO' | 'WARN' | 'ERROR';
  message: string;
  timestamp: Date;
  metadata?: Record<string, unknown>;
}

class JobLogBuilder {
  private data: Partial<JobLogEntry> = { timestamp: new Date() };

  forJob(jobId: string): this { this.data.jobId = jobId; return this; }
  atStep(stepName: string): this { this.data.stepName = stepName; return this; }
  level(level: JobLogEntry['level']): this { this.data.level = level; return this; }
  message(msg: string): this { this.data.message = msg; return this; }
  metadata(meta: Record<string, unknown>): this { this.data.metadata = meta; return this; }

  build(): JobLogEntry {
    const { jobId, stepName, level, message } = this.data;
    if (!jobId || !stepName || !level || !message) {
      throw new Error('Missing required fields: jobId, stepName, level, message');
    }
    return this.data as JobLogEntry;
  }
}
```

</details>

---

## Capstone Connection

PipeForge uses a `PipelineBuilder` to assemble pipeline configurations from JSON definition files. The CLI's `pipeforge init` uses a builder internally to construct the pipeline object before POSTing to the API.
