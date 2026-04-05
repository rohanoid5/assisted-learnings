# Module 06 — Exercises

## Prerequisites

FlowForge running locally with Redis, Postgres, and MailHog via Docker Compose.

```bash
# Start all dependencies
docker compose up -d postgres redis mailhog

# Start FlowForge workers + API
cd capstone/flowforge
npm run dev

# View queued/processed notifications
open http://localhost:3002/admin/queues   # Bull Board

# View delivered emails
open http://localhost:8025               # MailHog
```

---

## Exercise 1 — Queue Inspector

**Goal:** Explore BullMQ's queue states interactively.

**Steps:**

```bash
# 1. Enqueue several notifications
for i in {1..5}; do
  curl -s -X POST http://localhost:3002/api/v1/notifications \
    -H 'Content-Type: application/json' \
    -d "{\"channel\":\"email\",\"recipient\":\"user${i}@example.com\",\"subject\":\"Test ${i}\",\"body\":\"Hello ${i}\"}" | jq .notificationId
done
```

```typescript
// 2. Inspect queue states programmatically
import { notificationQueue } from './src/queues/notification.queue.js';

const counts = await notificationQueue.getJobCounts(
  'waiting', 'active', 'completed', 'failed', 'delayed'
);
console.log('Queue state breakdown:', counts);

// 3. Fetch and log the first job in 'completed'
const completed = await notificationQueue.getCompleted(0, 0);
const job = completed[0];
if (job) {
  console.log('Job ID:', job.id);
  console.log('Attempts:', job.attemptsMade);
  console.log('Finished at:', new Date(job.finishedOn!).toISOString());
}
```

**Expected:** You see job counts move from `waiting → active → completed` in Bull Board as workers process them.

---

## Exercise 2 — Fan-Out in Action

**Goal:** Verify that one API call fans out to both email and webhook queues.

**Steps:**

```bash
# Start a simple webhook receiver (capture incoming POSTs)
# In a separate terminal:
npx --yes webhook-listener --port 4567 &

# Send a fan-out notification to both channels
# (You'll need to modify the API to call fanOutEvent, or
#  send two separate POST requests — one for each channel)
curl -X POST http://localhost:3002/api/v1/notifications \
  -H 'Content-Type: application/json' \
  -d '{"channel":"email","recipient":"test@example.com","subject":"Fan-out","body":"Test"}'

curl -X POST http://localhost:3002/api/v1/notifications \
  -H 'Content-Type: application/json' \
  -d '{"channel":"webhook","recipient":"http://localhost:4567","body":"Test payload"}'
```

**Verify:**

1. MailHog (`http://localhost:8025`) received an email
2. Your webhook listener logged the incoming POST

---

## Exercise 3 — Dead Letter Queue Investigation

**Goal:** Force jobs into the DLQ and practice the recovery workflow.

```bash
# 1. Stop MailHog to cause email delivery failures
docker compose stop mailhog

# 2. Send 3 email notifications
for i in {1..3}; do
  curl -s -X POST http://localhost:3002/api/v1/notifications \
    -H 'Content-Type: application/json' \
    -d "{\"channel\":\"email\",\"recipient\":\"victim${i}@example.com\",\"subject\":\"DLQ Test\",\"body\":\"Will fail\"}"
done

# 3. Wait for all retries to exhaust (~30 seconds with exponential backoff)
# Watch Bull Board: jobs move from 'active' → 'delayed' → 'active' → ... → 'failed'

# 4. Verify DLQ
redis-cli ZCARD bull:email:failed
# Expected: 3

# 5. Restart MailHog
docker compose start mailhog

# 6. Replay failed jobs
curl -X POST http://localhost:3002/admin/queues/email/replay

# 7. Verify all 3 notifications land in MailHog
```

---

## Exercise 4 — Backpressure Under Load

**Goal:** Observe queue depth growth and validate the depth guard.

```typescript
// flood-test.exercise.ts

// TODO:
// 1. Set REJECT_AFTER = 100 in queue-guard.ts (low limit for testing)
// 2. Stop all workers so queue can fill up without draining:
//    await emailQueue.pause();
//
// 3. Send 150 POST requests to /api/v1/notifications rapidly
//    First 100: expect 202 Accepted
//    Jobs 101-150: expect 503 Service Unavailable (queue at capacity)
//
// 4. Verify queue depth:
//    const counts = await notificationQueue.getJobCounts('waiting');
//    assert counts.waiting <= 100

// 5. Resume workers, verify all 100 jobs drain correctly:
//    await emailQueue.resume();
```

**Expected results:**

| Requests | Expected Status | Queue Depth |
|---|---|---|
| First 100 | 202 Accepted | 0 → 100 |
| Next 50 | 503 Too Busy | remains ~100 |
| After drain | — | 0 (all processed) |
