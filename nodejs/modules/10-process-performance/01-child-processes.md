# 10.1 — Child Processes

## Concept

`child_process` lets Node.js spawn OS processes — shell commands, Python scripts, compiled binaries. Each child gets its own memory; communication happens via stdio pipes or IPC channels.

---

## Deep Dive

### `exec` vs `spawn` vs `fork`

| Method | Use case | stdio | IPC |
|--------|----------|-------|-----|
| `exec` | Short shell command, buffer output | buffered | No |
| `execFile` | Binary without shell | buffered | No |
| `spawn` | Long-running, streaming output | streams | No |
| `fork` | Node.js module, message-passing | streams | ✅ |

### `spawn` — Streaming Output

```typescript
import { spawn } from 'node:child_process';

function runCommand(cmd: string, args: string[]): Promise<string> {
  return new Promise((resolve, reject) => {
    const child = spawn(cmd, args, { stdio: ['ignore', 'pipe', 'pipe'] });
    
    const chunks: Buffer[] = [];
    const errChunks: Buffer[] = [];

    child.stdout.on('data', (chunk) => chunks.push(chunk));
    child.stderr.on('data', (chunk) => errChunks.push(chunk));

    child.on('error', reject);
    child.on('close', (code) => {
      if (code === 0) {
        resolve(Buffer.concat(chunks).toString('utf-8'));
      } else {
        reject(new Error(`Command failed (${code}): ${Buffer.concat(errChunks)}`));
      }
    });
  });
}

const output = await runCommand('python3', ['scripts/transform.py', 'data.csv']);
```

### `exec` with Promisified API

```typescript
import { exec } from 'node:child_process';
import { promisify } from 'node:util';

const execAsync = promisify(exec);

const { stdout, stderr } = await execAsync('git log --oneline -5', {
  cwd: '/path/to/repo',
  timeout: 5_000,   // auto-kill after 5s
  maxBuffer: 1024 * 1024, // 1 MB output limit
});

console.log(stdout);
```

### `fork` — IPC with Another Node Module

```typescript
// parent.ts
import { fork } from 'node:child_process';

const worker = fork('./src/workers/transform.js', [], { env: process.env });

worker.send({ type: 'PROCESS', payload: { filePath: 'data.csv' } });

worker.on('message', (msg: { type: string; result?: unknown; error?: string }) => {
  if (msg.type === 'DONE') console.log('Result:', msg.result);
  if (msg.type === 'ERROR') console.error('Worker error:', msg.error);
});

worker.on('exit', (code) => {
  if (code !== 0) console.error('Worker exited with code', code);
});
```

```typescript
// src/workers/transform.ts (run by fork)
process.on('message', async (msg: { type: string; payload: { filePath: string } }) => {
  if (msg.type === 'PROCESS') {
    try {
      const result = await heavyTransform(msg.payload.filePath);
      process.send!({ type: 'DONE', result });
    } catch (err) {
      process.send!({ type: 'ERROR', error: String(err) });
    }
  }
});
```

---

## Try It Yourself

**Exercise:** Write a function `runScript(scriptPath: string, env: Record<string, string>): Promise<string>` that spawns a Node.js child process with a custom environment and returns its stdout.

<details>
<summary>Show solution</summary>

```typescript
import { spawn } from 'node:child_process';

export function runScript(scriptPath: string, env: Record<string, string>): Promise<string> {
  return new Promise((resolve, reject) => {
    const child = spawn(process.execPath, [scriptPath], {
      env: { ...process.env, ...env },
      stdio: ['ignore', 'pipe', 'pipe'],
    });

    const out: Buffer[] = [];
    const err: Buffer[] = [];

    child.stdout.on('data', (b) => out.push(b));
    child.stderr.on('data', (b) => err.push(b));
    child.on('error', reject);
    child.on('close', (code) =>
      code === 0
        ? resolve(Buffer.concat(out).toString())
        : reject(new Error(Buffer.concat(err).toString()))
    );
  });
}
```

</details>

---

## Capstone Connection

PipeForge's `SHELL` step type uses `spawn` to run user-defined shell commands as pipeline steps. The child's stdout is piped to a Transform stream that emits each line as a `JobLog` entry in real time.
