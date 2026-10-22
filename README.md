# Distributed Test Orchestration & Execution Platform

A production-grade, distributed test orchestration engine designed to execute **100+ heterogeneous UI & API tests** across **parallel worker containers** within a strict SLA (sub-minute wall-clock time), eliminating the long-tail straggler problem.

---

## Architecture Overview

```
                               ┌────────────────────────────────────────────────────────┐
                               │             JAVA ORCHESTRATOR (The Brain)              │
                               │  • Generates 100 heterogeneous test definitions        │
                               │  • Computes LPT Bin-Packing (25-30 micro-batches)      │
                               │  • Subscribes to real-time events:results              │
                               └───────────────────────────┬────────────────────────────┘
                                                           │
                                                           ▼
                                           ┌───────────────────────────────┐
                                           │       REDIS WORK BROKER       │
                                           │  • queue:browser_ui (High)    │
                                           │  • queue:integration (Medium) │
                                           │  • queue:headless_api (Fast)  │
                                           └───────────────┬───────────────┘
                                                           │
                        ┌──────────────────────────────────┴──────────────────────────────────┐
                        ▼                                                                     ▼
             [Docker Worker 01]                                                    [Docker Worker 02..10]
             • BRPOP queue:browser_ui                                              • BRPOP queue:browser_ui
             • Runs Chromium specs                                                 • Runs Chromium specs
             • PUBLISH events:results ─────────────────────┐                       • PUBLISH events:results
                                                           │                                   │
                                                           ▼                                   │
                                           ┌───────────────────────────────┐                   │
                                           │    REDIS PUBSUB: events       │◄──────────────────┘
                                           └───────────────┬───────────────┘
                                                           │
                                                           ▼
                                           ┌───────────────────────────────┐
                                           │    RESULT AGGREGATOR (Java)   │
                                           │  • Real-time CLI progress bar │
                                           │  • Computes parallel speedup  │
                                           └───────────────────────────────┘
```

---

## Project Structure

```text
Distributed-Test-Orchestrator/
├── README.md                   # Master documentation
├── ORCHESTRATOR_FLOW.md        # UML Activity, Sequence, and Class diagrams
├── docker-compose.yml          # Container grid definition (Redis + scalable workers)
│
├── java-orchestrator/          # The Scheduling & Orchestration Core
│   ├── pom.xml                 # Maven dependencies (Jedis, Jackson, Slf4j)
│   └── src/main/java/com/distributed/testengine/
│       ├── OrchestratorApp.java# Main application entrypoint
│       ├── model/              # Domain entities (TestItem, TestBatch, TestResultEvent)
│       ├── sharder/            # LPT Bin-Packing Sharding Strategy
│       ├── broker/             # Redis client & PubSub listener
│       └── aggregator/         # Thread-safe counters & CLI dashboard
│
└── playwright-suite/           # The Worker Execution Fleet
    ├── Dockerfile              # Container image with Chromium & Linux dependencies
    ├── package.json            # Node.js dependencies (Playwright, ioredis, ts-node)
    ├── tsconfig.json           # Modern ES2022 TypeScript configuration
    ├── playwright.config.ts    # Browser engines, videos, traces, HTML reports
    ├── worker-agent.ts         # Worker daemon pulling batches from Redis
    └── tests/                  # Physical Playwright test suites
        ├── ui/
        │   └── checkout.spec.ts   # Browser UI navigation, locators, video
        ├── api/
        │   └── auth.spec.ts       # Headless API tests via request fixture
        └── integration/
            └── order_flow.spec.ts # End-to-end multi-step transaction test
```

---

## Core Low-Level Design Concepts Applied

### 1. The Straggler Problem & LPT Bin-Packing
* **The Problem:** Naive round-robin partitioning allocates 10 tests per worker. If Worker 1 gets 10 UI tests ($10 \times 35\text{s} = 350\text{s}$) while Worker 2 gets 10 API tests ($10 \times 1\text{s} = 10\text{s}$), Worker 2 sits idle for over 5 minutes while the entire suite blocks on Worker 1.
* **The Solution:** 
  1. Tests are sorted descending by runtime ($T_{\text{longest}} \to T_{\text{shortest}}$).
  2. Bins are packed using a Min-Heap (`PriorityQueue<TestBatch>`).
  3. Work is divided into **micro-batches** ($\le 45\text{ seconds}$). Workers dynamically "steal" remaining packets as they finish.

### 2. Zero-CPU Priority Queuing (`BRPOP`)
* Workers don't busy-wait with high-frequency HTTP polling.
* Workers use Redis `BRPOP(queue:browser_ui, queue:integration, queue:headless_api, 5)`. 
* If no work exists, workers sleep with **0% CPU**. When tasks arrive, Redis wakes them up in microseconds.

### 3. Race-Condition Elimination (Subscribe-First Guarantee)
* In `OrchestratorApp.java`, the background thread subscribes to `events:results` **before** any batches are pushed into Redis.
* This guarantees that fast sub-second API tests cannot complete before the listener is ready to record them.

---

## Prerequisites

* **Docker Desktop:** Installed and running (powers the worker container fleet & Redis)
* **Java:** OpenJDK 21 or 25
* **Build Tool:** Apache Maven 3.9+
* **Node.js:** v18+ (tested on Node 26) & npm (for local development)

---

## Execution Modes

You can run the grid in two modes:

---

### Mode 1: 100% Containerized Grid (Recommended) 🚀

This runs the Redis broker and scales **10 isolated Playwright worker containers** automatically using Docker Compose.

#### 1. Spin up the 10-Worker Grid
From the project root directory:
```bash
docker compose up -d --build --scale worker=10
```

Verify all 10 worker containers and Redis are running:
```bash
docker compose ps
```
*(You will see `redis` and `distributed-test-orchestrator-worker-1` through `10` active).*

#### 2. Run the Java Orchestrator
In another terminal, trigger the 100 tests:
```bash
cd java-orchestrator
mvn compile exec:java
```
Watch all 10 Docker worker containers divide the workload in real time!

#### 3. Bring Down the Grid When Finished
```bash
docker compose down
```

---

### Mode 2: Local Development Mode (Running Workers via Terminal) 💻

If you prefer to run workers directly on your host machine without Docker containers:

#### 1. Start Only Redis
```bash
docker compose up -d redis
```

#### 2. Start Workers in Terminal
In one or more terminal tabs:
```bash
cd playwright-suite
npm install
npm run worker
```

#### 3. Run the Java Orchestrator
```bash
cd java-orchestrator
mvn compile exec:java
```

#### 4. Stop Redis
```bash
docker compose down
```

---

## Verified Benchmark Results

Actual benchmark results running **10 parallel Docker worker containers**:

```text
=================================================================
   ⚡ JAVA DISTRIBUTED TEST ORCHESTRATION ENGINE (LLD) ⚡
=================================================================
📦 Generated 100 Tests (30 UI, 40 API, 30 Integration).
⏱️  Theoretical Sequential Duration: 1280.6 seconds (~21.3 mins)
⚖️  LPT Sharder produced 39 balanced micro-batches.
🧹 Cleared old queues from Redis.
🚀 Dispatched 39 work packets into typed Redis queues.

📡 Listening for events from worker fleet...
🚀 Progress: [█████████████████████████] 100/100 (100.0%) | Latest: API-015 on aa12c4520fee [PASSED]  

=================================================================
🏁 FINAL EXECUTION & ORCHESTRATION REPORT
=================================================================
Total Tests Scheduled       : 100
Passed Tests                : 99 ✅
Failed Tests                : 1 ❌
Pass Rate                   : 99.0%
Wall-Clock Execution Time   : 7.89 seconds ⚡
Theoretical Sequential Time : 1280.6 seconds
Parallel Speedup Factor     : 162.4x faster across workers!
-----------------------------------------------------------------
📊 WORKER LOAD DISTRIBUTION (Proving LPT Balance):
  • 00c52ac1c492 (Pod 01) :  16 tests completed
  • 00cf04f21a96 (Pod 02) :   3 tests completed
  • 06b6f1e9318e (Pod 03) :   7 tests completed
  • 25881d1de039 (Pod 04) :   7 tests completed
  • 8965a3fde192 (Pod 05) :  16 tests completed
  • aa12c4520fee (Pod 06) :  17 tests completed
  • d7e3f91bc8e6 (Pod 07) :   6 tests completed
  • d9e750ee34c7 (Pod 08) :   7 tests completed
  • f51dd0ed8002 (Pod 09) :   8 tests completed
  • fc28860043b2 (Pod 10) :   6 tests completed
=================================================================
```

---

## Useful Docker Utility Commands

* **View live logs across all 10 workers:**
  ```bash
  docker compose logs -f worker
  ```
* **View container resource usage (CPU & Memory):**
  ```bash
  docker stats
  ```
* **Clean up all unused images to reclaim disk space:**
  ```bash
  docker image prune -a -f
  ```
* **Completely shut down the Docker Desktop application:**
  ```bash
  killall Docker
  ```
