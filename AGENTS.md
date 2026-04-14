# Agent Instructions — `learnings` workspace

A self-directed engineering learning monorepo with six independent tracks. Each track has numbered modules, self-assessment checklists, and an incremental capstone project.

---

## Tracks at a glance

| Folder | Focus | Capstone | Stack |
|--------|-------|---------|-------|
| [`ds-algo/`](ds-algo/README.md) | DSA for FAANG interviews | AlgoForge | Java 17, Maven, JUnit 5 |
| [`spring-boot/`](spring-boot/README.md) | Spring Boot for JS devs | TaskForge | Java 17, Maven, Spring Boot 3.x |
| [`nodejs/`](nodejs/README.md) | Advanced Node.js | PipeForge | Node.js 20 LTS, TypeScript, ESM |
| [`postgres/`](postgres/README.md) | Production PostgreSQL | StoreForge | SQL, PL/pgSQL, psql |
| [`system-design/`](system-design/README.md) | Distributed system design | ScaleForge + FlowForge | TypeScript, Redis, BullMQ, Prisma |
| [`docker-k8s/`](docker-k8s/README.md) | Docker, Kubernetes & SRE | DeployForge | Docker, Kubernetes, Helm, Terraform, Prometheus |

---

## Workspace conventions

### Module structure (all tracks)
```
<track>/modules/<NN>-<topic>/
├── README.md          ← learning objectives + topic list
├── 01-<subtopic>.md   ← concept deep-dive (always ends with "Capstone Connection")
└── exercises/
    └── README.md      ← step-by-step exercises referencing the capstone
```

### Key conventions
- Modules are **sequential** — content builds on previous modules.
- Every concept file ends with a **Capstone Connection** section; exercises must reference it.
- [`CHECKLIST.md`](ds-algo/CHECKLIST.md) in each track is a self-assessment file using `[ ]` / `[~]` / `[x]` markers — never auto-complete these entries.
- New content belongs to the correct numbered module folder; don't create ad-hoc files at the track root.

---

## Build & run commands

### AlgoForge (`ds-algo/capstone/algoforge/`)
```bash
mvn clean compile          # compile
mvn test                   # run all ~122 tests (JUnit 5 + AssertJ)
mvn test -Dtest=<Class>    # run one test class
mvn exec:java -Dexec.mainClass="com.algoforge.ComplexityBenchmark"
```

### TaskForge (`spring-boot/capstone/taskforge/`)
```bash
mvn clean install          # build + test
mvn spring-boot:run        # start on :8080
# PostgreSQL required from Module 4:
docker run --name taskforge-db -e POSTGRES_USER=taskforge \
  -e POSTGRES_PASSWORD=password -e POSTGRES_DB=taskforge_dev \
  -p 5432:5432 -d postgres:15
```

### PipeForge (`nodejs/capstone/pipeforge/`)
```bash
npm install
cp .env.example .env
npm run dev                # ts-node / tsx watch mode
npm run cli -- --help
```

### ScaleForge (`system-design/capstone/scaleforge/`)
```bash
cp .env.example .env
docker compose up -d       # PostgreSQL + Redis + BullMQ
npm install
npm run db:migrate
npm run dev                # Express on :3001
curl http://localhost:3001/health
```

### FlowForge (`system-design/capstone/flowforge/`)
```bash
cp .env.example .env
docker compose up -d       # PostgreSQL + Redis + RabbitMQ
npm install
npm run db:migrate
npm run dev:event-service  # :3002
npm run dev:delivery-worker
```

### StoreForge (`postgres/capstone/storeforge/`)
```bash
docker run --name storeforge-db -e POSTGRES_USER=storeforge \
  -e POSTGRES_PASSWORD=password -e POSTGRES_DB=storeforge_dev \
  -p 5432:5432 -d postgres:15
psql -h localhost -U storeforge -d storeforge_dev
```

### DeployForge (`docker-k8s/capstone/deployforge/`)
```bash
# Docker phase (Modules 01-03):
cp .env.example .env
docker compose up -d       # API + Worker + PostgreSQL + Redis + Nginx
curl http://localhost:3000/health

# Kubernetes phase (Modules 04+):
./scripts/setup-kind.sh    # Create kind cluster + install Ingress
kubectl apply -k k8s/overlays/dev/
kubectl port-forward svc/api-gateway 3000:3000
curl http://localhost:3000/health
```

---

## Prerequisites

| Tool | Version | Install |
|------|---------|---------|
| Java | 17+ | `sdk install java` ([SDKMAN](https://sdkman.io/)) |
| Maven | 3.8+ | `brew install maven` |
| Node.js | 20 LTS | `nvm install 20` |
| Docker | Latest | [docker.com](https://www.docker.com/) |
| PostgreSQL client | 15+ | Ships with Docker image; or `brew install libpq` |
| kubectl | 1.28+ | `brew install kubectl` |
| kind | 0.20+ | `brew install kind` |
| Helm | 3.13+ | `brew install helm` |
| Terraform | 1.6+ | `brew install terraform` |

---

## Adding new content

1. **New module topic file** → `modules/<NN>-<topic>/<NN>-<subtopic>.md` following the existing format (Concept → Deep Dive → Code Examples → Capstone Connection).
2. **New exercise** → append to `modules/<NN>-<topic>/exercises/README.md`.
3. **New capstone code** → add to the appropriate capstone package/folder and a matching test class; follow existing naming (e.g., `DynamicArray.java` → `DynamicArrayTest.java`).
4. **Checklist update** → add checklist items to the track's `CHECKLIST.md`; do **not** mark them complete.
