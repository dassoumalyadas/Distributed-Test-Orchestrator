# Distributed Test Orchestration & Execution Platform (LLD)

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
             [Playwright Worker 01]                                                [Playwright Worker 02..N]
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
├── README.md                   # This master documentation
├── ORCHESTRATOR_FLOW.md        # UML Activity, Sequence, and Class diagrams
│
├── java-orchestrator/          # The Scheduling & Orchestration Core
│   ├── pom.xml                 # Maven dependencies (Jedis, Jackson, Slf4j)
│   └── src/main/java/com/distributed/testengine/
│       ├── OrchestratorApp.java# Main application entrypoint
│       ├── model/              # Domain entities
│       │   ├── TestItem.java   # Single test definition & duration metadata
│       │   ├── TestBatch.java  # Bundled work packet for workers
│       │   ├── TestType.java   # Enum: BROWSER_UI, INTEGRATION, HEADLESS_API
│       │   └── TestResultEvent.java # Deserialized worker telemetry
│       ├── sharder/            # Scheduling Intelligence
│       │   ├── IShardingStrategy.java # Strategy Pattern interface
│       │   └── LPTBinPackingSharder.java # Longest Processing Time First-Fit
│       ├── broker/
│       │   └── RedisBrokerClient.java # Jedis queue management & PubSub
│       └── aggregator/
│           └── ResultAggregator.java # Thread-safe counters & CLI dashboard
│
└── playwright-suite/           # The Worker Execution Fleet
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

* **Java:** OpenJDK 21 or 25
* **Build Tool:** Apache Maven 3.9+
* **Node.js:** v18+ (tested on Node 26) & npm
* **Docker:** For running the local Redis broker

---

## Step-by-Step Quickstart

### Step 1: Start Redis Broker
If Redis is not already running, start it using Docker:
```bash
docker run -d --name redis -p 6379:6379 redis:alpine
```
Verify it responds:
```bash
docker exec redis redis-cli ping
# Output: PONG
```

---

### Step 2: Install Playwright Dependencies
Navigate to the `playwright-suite` directory:
```bash
cd playwright-suite
npm install
```

---

### Step 3: Launch One or More Playwright Workers
Open one (or multiple) terminal tabs and run:
```bash
cd playwright-suite
npm run worker
```
*(Tip: You can open 2 or 3 separate terminal tabs and run `npm run worker` in each to watch parallel load distribution across multiple workers!)*

---

### Step 4: Run the Java Orchestrator
In another terminal tab, run:
```bash
cd java-orchestrator
mvn compile exec:java
```

---

## Sample Execution Output

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
🚀 Progress: [█████████████████████████] 100/100 (100.0%) | Latest: API-019 on worker-7ljo6r [PASSED]  

=================================================================
🏁 FINAL EXECUTION & ORCHESTRATION REPORT
=================================================================
Total Tests Scheduled       : 100
Passed Tests                : 99 ✅
Failed Tests                : 1 ❌
Pass Rate                   : 99.0%
Wall-Clock Execution Time   : 40.40 seconds ⚡
Theoretical Sequential Time : 1280.6 seconds
Parallel Speedup Factor     : 31.7x faster across workers!
-----------------------------------------------------------------
📊 WORKER LOAD DISTRIBUTION (Proving LPT Balance):
  • worker-7ljo6r      :  52 tests completed
  • worker-jvhxrd      :  48 tests completed
=================================================================
```

---

## Extending to Full Kubernetes / Cloud Deployment

To take this from local LLD to cloud scale:
1. **Containerize the Worker:** Package `playwright-suite` into a Docker image with pre-installed Chromium.
2. **KEDA Autoscaling:** Deploy the worker on Kubernetes with KEDA to scale pods automatically based on Redis queue depth (`queue:browser_ui`).
3. **Artifact Offloading:** Upload `.webm` video recordings and Playwright traces directly to AWS S3 or MinIO.
