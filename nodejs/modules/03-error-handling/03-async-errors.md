# 3.3 — Async Error Handling

## Concept

Async errors have more ways to go wrong than sync errors. Understanding the exact rules for when `try/catch` catches, when `.catch()` is needed, and how unhandled rejections surface is essential for writing reliable async code.

---

## Deep Dive

### Rules for try/catch with Promises

`try/catch` catches errors from `await`ed Promises. For non-awaited Promises, you must use `.catch()`:

```typescript
// ✅ Caught — the Promise is awaited
try {
  await fetch('bad-url');
} catch (err) {
  console.error('Caught!', err);
}

// ❌ NOT caught — the Promise is not awaited when try/catch is active
try {
  fetch('bad-url'); // rejection happens asynchronously, after try block exits
} catch (err) {
  console.error('This never runs');
}

// ✅ Use .catch() for fire-and-forget operations
fetch('bad-url').catch((err) => console.error('Caught!', err));
```

### The Most Common async/await Error Mistake

```typescript
// ❌ The try/catch does NOT protect against the returned Promise
async function dangerousOperation() {
  try {
    return doAsyncWork(); // no await!
  } catch (err) {
    // This only catches synchronous errors from doAsyncWork() setup
    // Async rejection from the returned Promise is NOT caught here
    console.error('I never run for async errors');
  }
}

// ✅ Always await inside try/catch
async function safeOperation() {
  try {
    return await doAsyncWork();
  } catch (err) {
    console.error('I catch both sync and async errors correctly');
  }
}
```

### Concurrent Promises and Error Handling

```typescript
// ❌ Errors from Promise.all propagate to the awaiting try/catch
//    But other Promises may still be pending (their results are discarded)
const [a, b, c] = await Promise.all([opA(), opB(), opC()]);
// If opB rejects at 2s, opC is abandoned (no cleanup!)

// ✅ Use Promise.allSettled for independent operations with cleanup needs
const results = await Promise.allSettled([opA(), opB(), opC()]);
// All complete — check each result individually
```

---

## Try It Yourself

**Exercise:** Predict which of these catch the error and fix the ones that don't:

```typescript
// 1.
async function test1() {
  try { return fetchData(); } catch (e) { console.log('caught'); }
}

// 2.
async function test2() {
  try { await Promise.reject(new Error('oops')); } catch (e) { console.log('caught'); }
}

// 3.
async function test3() {
  Promise.reject(new Error('oops')).catch(e => { throw new Error('rethrown'); });
}
```

<details>
<summary>Show answers</summary>

1. **Not caught** — `fetchData()` returns a Promise that isn't awaited. The rejection happens after the try block.
2. **Caught** — `await` inside try/catch correctly catches rejected Promises.
3. **Unhandled rejection** — The `.catch()` handler re-throws, and there's no handler for that new rejection. Use `.catch(e => console.error(e))` or attach another `.catch()`.

</details>

---

## Capstone Connection

PipeForge's `JobRunner` uses `return await` in all try/catch blocks to ensure job step errors are caught and recorded in `JobLog`. Fire-and-forget log writes use `.catch()` to prevent logging failures from affecting job execution.
