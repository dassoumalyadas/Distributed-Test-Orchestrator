# Distributed Test Orchestrator - UML Architectural & Behavioral Models

This document presents the complete **UML (Unified Modeling Language)** specifications for the Distributed Test Orchestration Engine, featuring **Vertical Swimlanes with Box Pillars**, **Sequence Lifelines**, and **Class Models**.

---

## 1. Visual Swimlane Grid (Text Overview)

```text
==========================================================================================================================================
   SWIMLANE 1: ORCHESTRATOR (Java)     │  SWIMLANE 2: LISTENER (Java)   │  SWIMLANE 3: BROKER (Redis)   │  SWIMLANE 4: WORKER (Playwright)
==========================================================================================================================================
 1. main() generates 100 tests         │                                │                               │
 2. LPT Bin-Packing (25-30 packets)    │                                │                               │
 3. broker.cleanQueues() ──────────────┼────────────────────────────────┼──► DEL old queues             │
                                       │                                │                               │
 4. [FORK] Spawns daemon thread ───────┼──► 5. SUBSCRIBE events:results ┼──► Registers PubSub channel   │
 6. Dispatches packets ────────────────┼────────────────────────────────┼──► RPUSH queue:browser_ui...  │
                                       │                                │                               │
 7. Enters non-blocking loop:          │                                │                               │ 8. BRPOP queue:browser_ui
    while (!aggregator.isComplete())   │                                │                               │ 9. Runs Playwright Chromium
                                       │                                │                               │10. PUBLISH events:results
                                       │                                │                               │    {status: PASSED, 120ms}
                                       │                                │                               │              │
                                       │ 11. onMessage(event) ◄─────────┼───────────────────────────────┼──────────────┘
                                       │ 12. completedCount.inc()       │                               │
                                       │ 13. Redraws live progress bar  │                               │
                                       │                                │                               │
 14. [JOIN] isComplete() == true ◄─────┼─── (All 100 tests verified)    │                               │
 15. Prints final duration & report    │                                │                               │
 16. Closes JedisPool & Exits          │                                │                               │
==========================================================================================================================================
```

---

## 2. UML Vertical Swimlane Diagram (Color Box Pillars)

Open this document in **Markdown Preview (`Cmd + Shift + V`)** to view the distinct vertical swimlane pillars:

```mermaid
sequenceDiagram
    autonumber

    box rgb(238, 242, 255) 👤 SWIMLANE 1: Java Orchestrator (Main Thread)
    participant Main as OrchestratorApp
    participant Sharder as LPT Sharder
    participant Aggregator as ResultAggregator
    end

    box rgb(254, 243, 199) 🧵 SWIMLANE 2: Async Event Listener (Daemon Thread)
    participant Listener as JedisPubSub Thread
    end

    box rgb(254, 226, 226) 🔴 SWIMLANE 3: Message Broker (Redis)
    participant RedisQueues as Redis Queues
    participant RedisChannel as PubSub: events:results
    end

    box rgb(220, 252, 231) 🤖 SWIMLANE 4: Worker Fleet (Playwright Pods)
    participant Worker as WorkerAgent.ts
    end

    Note over Main,Sharder: Phase 1: Test Ingestion & Sharding
    Main->>Sharder: shard(100 tests, 10 workers)
    Sharder-->>Main: Return 28 balanced micro-batches

    Note over Main,RedisQueues: Phase 2: Broker Setup & Fork
    Main->>RedisQueues: cleanQueues()
    Main->>Listener: [FORK] Spawn background listener
    Listener->>RedisChannel: SUBSCRIBE "events:results"

    Main->>RedisQueues: RPUSH batches (queue:browser_ui, queue:api)

    Note over RedisQueues,Worker: Phase 3: Parallel Execution
    par Across 10 Workers
        Worker->>RedisQueues: BRPOP queue:browser_ui
        RedisQueues-->>Worker: Hands over TestBatch
        Worker->>Worker: Executes Playwright test
        Worker->>RedisChannel: PUBLISH "events:results" {status: "PASSED"}
    end

    Note over RedisChannel,Listener: Phase 4: Telemetry & Progress
    RedisChannel-->>Listener: onMessage(event)
    Listener->>Aggregator: recordStep(event)
    Note over Aggregator: • completedCount++<br/>• Redraw [████░░] 60%

    Note over Main,Aggregator: Phase 5: Join & Completion
    loop Non-blocking wait (every 250ms)
        Main->>Aggregator: isComplete()?
    end
    Aggregator-->>Main: [JOIN] true (100/100 completed)
    Main->>Main: printFinalReport()
    Main->>RedisQueues: close()
```

---

## 3. UML Class Diagram (Structural Object Model)

```mermaid
classDiagram
    direction TB

    class IShardingStrategy {
        <<interface>>
        +shard(List~TestItem~ tests, int targetWorkers, double maxBatchDurationSec)* List~TestBatch~
    }

    class LPTBinPackingSharder {
        +shard(List~TestItem~ tests, int targetWorkers, double maxBatchDurationSec) List~TestBatch~
        -packBins(List~TestItem~ sortedTests, double maxBudget) List~TestBatch~
    }

    class TestItem {
        -String testId
        -String name
        -String file
        -TestType type
        -double estimatedDurationSec
        +compareTo(TestItem other) int
        +getEstimatedDurationSec() double
        +getType() TestType
    }

    class TestBatch {
        -String batchId
        -TestType type
        -List~TestItem~ tests
        -double totalDurationSec
        +addTest(TestItem test) void
        +getTotalDurationSec() double
        +getTests() List~TestItem~
    }

    class TestType {
        <<enumeration>>
        BROWSER_UI
        INTEGRATION
        HEADLESS_API
        -String value
        -String queueName
        +getQueueName() String
    }

    class RedisBrokerClient {
        -JedisPool jedisPool
        -ObjectMapper objectMapper
        +cleanQueues() void
        +publishBatches(List~TestBatch~ batches) void
        +subscribeToResults(ResultAggregator aggregator) void
        +close() void
    }

    class ResultAggregator {
        -int totalTests
        -AtomicInteger completedCount
        -AtomicInteger passedCount
        -AtomicInteger failedCount
        -Map~String, Integer~ workerStats
        -long startTime
        +recordStep(TestResultEvent event) void
        +isComplete() boolean
        +printFinalReport(double theoreticalSequentialSec) void
    }

    class TestResultEvent {
        -String batchId
        -String testId
        -String testName
        -String workerId
        -String status
        -long durationMs
        -String errorMessage
        -long timestamp
    }

    class OrchestratorApp {
        -String REDIS_HOST$
        -int REDIS_PORT$
        -int TOTAL_TESTS$
        -int TARGET_WORKERS$
        +main(String[] args)$ void
        -generateTestSuite(int count)$ List~TestItem~
    }

    IShardingStrategy <|.. LPTBinPackingSharder : implements
    TestItem ..|> Comparable : implements
    TestBatch "1" *-- "1..*" TestItem : contains
    TestBatch --> TestType : categorizes
    TestItem --> TestType : categorizes
    OrchestratorApp --> IShardingStrategy : uses
    OrchestratorApp --> RedisBrokerClient : manages
    OrchestratorApp --> ResultAggregator : tracks
    RedisBrokerClient ..> TestResultEvent : parses
    ResultAggregator ..> TestResultEvent : consumes
```
