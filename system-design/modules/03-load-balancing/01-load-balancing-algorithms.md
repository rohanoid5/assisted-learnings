# 3.1 — Load Balancing Algorithms

## Concept

A load balancer sits between clients and servers. Its job is to decide **which server** each incoming request goes to. The algorithm it uses affects performance, fairness, and cache efficiency. Choosing the wrong algorithm causes hot spots — some servers overwhelmed while others sit idle.

---

## Deep Dive

### Round Robin — The Default

```
Requests: 1, 2, 3, 4, 5, 6
Servers:  A, B, C, A, B, C

Request 1 → Server A
Request 2 → Server B
Request 3 → Server C
Request 4 → Server A
...

Pros:  Simple, equal distribution assuming equal request cost
Cons:  Ignores server load — a complex request on A blocks it while B/C serve 3 each
       Fails if servers have different capacities (heterogeneous fleet)
```

**Weighted Round Robin**: Assign more requests to more powerful servers.
```
Server A: weight 3 → gets 3/5 of traffic
Server B: weight 2 → gets 2/5 of traffic
Use case: Migration — old server gets less traffic while new one spins up
```

### Least Connections

```
Route each request to the server with the fewest active connections.

State:
  Server A: 10 active connections
  Server B: 3 active connections  ← next request goes here
  Server C: 7 active connections

Pros:  Adapts to workload differences — slow requests don't clog a server
Cons:  Requires connection counting state in the load balancer
       Between tie-breaking, can still have hot spots at millisecond level

Best for: Long-lived connections, variable request duration (API with heavy queries)
```

**Least Response Time** (extension): Routes to server with fewest connections AND lowest average response time. Used by HAProxy, Nginx Plus.

### IP Hash / Session Affinity (Sticky Sessions)

```
Hash the client IP (or session cookie) → always route to the same server.

User 192.168.1.1 → hash → Server B (always)
User 192.168.1.2 → hash → Server A (always)

Pros:  Server can cache user session state in memory (no Redis needed)
Cons:  Uneven distribution if some IPs generate more traffic
       If a server fails, all its "sticky" users hit errors until remapped
       Prevents true stateless horizontal scaling

Use case: Legacy apps with in-memory session state (not recommended for new systems)
ScaleForge: AVOID sticky sessions — use Redis for session/rate-limit state instead
```

### Consistent Hashing — The Cache-Friendly Algorithm

```
Use case: Route the SAME resource consistently to the SAME backend server.
Best for: CDN/cache clusters, sharded databases, distributed caches.

Traditional hash ring:
  Assign a hash position (0–2^32) to each server:
    Server A → position 1000
    Server B → position 2000
    Server C → position 3000

  To route request with key K = "url:abc123":
    1. Hash("url:abc123") → 1800
    2. Find next server clockwise on ring → Server B

  Adding Server D at position 2500:
    Only keys between 2000 and 2500 are remapped (B→D)
    Everything else unchanged — minimal disruption to caches

Virtual nodes: Each server maps to N positions on the ring (N=100 typical)
  → More even distribution, better load balancing across the ring
```

```
Consistent hashing in ScaleForge:
  Used by Redis Cluster to shard URL cache keys across Redis nodes.
  "url:abc123" always lands on the same Redis node →
  cache invalidation targets exactly one node, no broadcast needed.
```

### Comparison

| Algorithm | Best For | Handles Variable Load | State Required |
|-----------|----------|----------------------|----------------|
| Round Robin | Equal-cost requests, stateless | No | No |
| Weighted RR | Mixed capacity servers | No | No |
| Least Connections | Variable request duration | Yes | Per-server count |
| IP Hash | Session affinity | No | No |
| Consistent Hashing | Cache clusters, sharded storage | Partial | Ring state |
| Random | Simple deployments | No | No |

---

## Code Examples

### Consistent Hashing Implementation

```typescript
// src/util/consistent-hash.ts
// Used by ScaleForge Redis Cluster client to route keys to the correct shard.

import { createHash } from 'node:crypto';

export class ConsistentHashRing<T> {
  private ring = new Map<number, T>();
  private sortedKeys: number[] = [];

  constructor(private readonly virtualNodes = 100) {}

  addNode(node: T, nodeKey: string): void {
    for (let i = 0; i < this.virtualNodes; i++) {
      const position = this.hash(`${nodeKey}:${i}`);
      this.ring.set(position, node);
    }
    this.sortedKeys = [...this.ring.keys()].sort((a, b) => a - b);
  }

  removeNode(nodeKey: string): void {
    for (let i = 0; i < this.virtualNodes; i++) {
      const position = this.hash(`${nodeKey}:${i}`);
      this.ring.delete(position);
    }
    this.sortedKeys = [...this.ring.keys()].sort((a, b) => a - b);
  }

  getNode(key: string): T | null {
    if (this.ring.size === 0) return null;
    const hash = this.hash(key);

    // Find the first position >= hash (clockwise on the ring)
    const index = this.sortedKeys.findIndex(k => k >= hash);
    const position = index === -1
      ? this.sortedKeys[0]!   // Wrap around the ring
      : this.sortedKeys[index]!;

    return this.ring.get(position) ?? null;
  }

  private hash(key: string): number {
    const buf = createHash('md5').update(key).digest();
    // Take the first 4 bytes as an unsigned 32-bit integer
    return buf.readUInt32BE(0);
  }
}

// Usage: Route Redis cache keys to the right shard
const ring = new ConsistentHashRing<string>();
ring.addNode('redis-1:6379', 'redis-1');
ring.addNode('redis-2:6379', 'redis-2');
ring.addNode('redis-3:6379', 'redis-3');

const shard = ring.getNode('url:abc123');
console.log(`Cache key 'url:abc123' → ${shard}`);
```

### Least Connections Load Balancer (in-process, for testing)

```typescript
// experiments/lb-simulation.ts
// Simulate different LB algorithms, compare performance with variable request duration

type Server = { id: string; connections: number; requestsServed: number };

function roundRobin(servers: Server[]): Server {
  const idx = roundRobinState.counter++ % servers.length;
  return servers[idx]!;
}
const roundRobinState = { counter: 0 };

function leastConnections(servers: Server[]): Server {
  return servers.reduce((min, s) => s.connections < min.connections ? s : min);
}

async function simulateRequest(server: Server, durationMs: number): Promise<void> {
  server.connections++;
  server.requestsServed++;
  await new Promise(r => setTimeout(r, durationMs));
  server.connections--;
}

// Scenario: 2 fast servers + 1 slow server
// Request durations randomly vary — simulates real URL redirects
async function runSimulation(algorithm: 'round-robin' | 'least-connections') {
  const servers: Server[] = [
    { id: 'A (fast)', connections: 0, requestsServed: 0 },
    { id: 'B (fast)', connections: 0, requestsServed: 0 },
    { id: 'C (slow)', connections: 0, requestsServed: 0 },  // Simulates GC pause or hot DB connection
  ];

  const REQUESTS = 1000;
  const start = performance.now();

  // Randomly vary duration: B/C are 3x slower (simulates one slow replica)
  const promises = Array.from({ length: REQUESTS }, (_, i) => {
    const server = algorithm === 'round-robin' ? roundRobin(servers) : leastConnections(servers);
    const isSlow = server.id.includes('slow');
    const duration = isSlow ? 30 : 10;
    return simulateRequest(server, duration);
  });

  await Promise.all(promises);
  const elapsed = performance.now() - start;

  console.log(`\n${algorithm.toUpperCase()} — ${elapsed.toFixed(0)}ms total`);
  servers.forEach(s => console.log(`  ${s.id}: ${s.requestsServed} requests`));
}

await runSimulation('round-robin');
await runSimulation('least-connections');
// Observe: least-connections avoids overloading Server C
```

---

## Try It Yourself

**Exercise:** Implement weighted round-robin to simulate rolling a deployment.

```typescript
// src/util/weighted-round-robin.ts
//
// When deploying a new ScaleForge version, you want to gradually cut
// traffic to the new version before fully rolling over.
// Weighted round-robin lets you say "new version gets 10% of traffic"
// and ramp up without a hard cutover.

interface WeightedServer {
  id: string;
  weight: number; // relative weight (higher = more traffic)
}

export class WeightedRoundRobin {
  private currentIndex = 0;
  private currentWeight = 0;
  private maxWeight = 0;
  private gcd = 1;

  constructor(private servers: WeightedServer[]) {
    // TODO:
    // precompute maxWeight and gcd of all weights
    // (needed for the Nginx-style smooth weighted round-robin algorithm)
    throw new Error('Not implemented');
  }

  next(): WeightedServer {
    // TODO: implement smooth weighted round-robin selection
    // See: https://github.com/nicksyosop/smooth-wrr
    // Simpler approach: expand weights into array and cycle through
    throw new Error('Not implemented');
  }
}

// Test with:
// v1 (old): weight 9, v2 (new): weight 1
// After 100 calls, v2 should get ~10 calls
```

<details>
<summary>Show solution</summary>

```typescript
export class WeightedRoundRobin {
  private expanded: WeightedServer[];
  private index = 0;

  constructor(servers: WeightedServer[]) {
    // Expand into array: weight 3 → [s, s, s]
    // Normalize by GCD to keep the array small
    const gcd = servers.reduce((g, s) => gcdOf(g, s.weight), servers[0]?.weight ?? 1);
    this.expanded = servers.flatMap(s =>
      Array.from({ length: s.weight / gcd }, () => s)
    );
    shuffle(this.expanded); // Avoid burst patterns
  }

  next(): WeightedServer {
    const server = this.expanded[this.index % this.expanded.length]!;
    this.index++;
    return server;
  }
}

function gcdOf(a: number, b: number): number {
  return b === 0 ? a : gcdOf(b, a % b);
}

function shuffle<T>(arr: T[]): void {
  for (let i = arr.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [arr[i], arr[j]] = [arr[j]!, arr[i]!];
  }
}
```

</details>

---

## Capstone Connection

ScaleForge uses **least connections** in Nginx for the redirect path. Round-robin would route equally but a slow replica (e.g., one running garbage collection) would still receive the same traffic, causing p99 spikes. Consistent hashing powers the Redis Cluster setup in Module 05 — the URL cache keys are distributed across 3 Redis shards, and `GET url:abc123` always goes to the same shard without broadcasting to all three.
