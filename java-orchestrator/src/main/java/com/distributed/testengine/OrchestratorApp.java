package com.distributed.testengine;

import com.distributed.testengine.aggregator.ResultAggregator;
import com.distributed.testengine.broker.RedisBrokerClient;
import com.distributed.testengine.model.TestBatch;
import com.distributed.testengine.model.TestItem;
import com.distributed.testengine.model.TestType;
import com.distributed.testengine.sharder.IShardingStrategy;
import com.distributed.testengine.sharder.LPTBinPackingSharder;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class OrchestratorApp {
    private static final String REDIS_HOST = System.getenv().getOrDefault("REDIS_HOST", "127.0.0.1");
    private static final int REDIS_PORT = Integer.parseInt(System.getenv().getOrDefault("REDIS_PORT", "6379"));
    private static final int TOTAL_TESTS = 100;
    private static final int TARGET_WORKERS = 10;

    public static void main(String[] args) {
        System.out.println("=================================================================");
        System.out.println("   ⚡ JAVA DISTRIBUTED TEST ORCHESTRATION ENGINE (LLD) ⚡");
        System.out.println("=================================================================");

        // 1. Generate 100 Heterogeneous Tests
        List<TestItem> suite = generateTestSuite(TOTAL_TESTS);
        double totalSequentialSec = suite.stream().mapToDouble(TestItem::getEstimatedDurationSec).sum();

        System.out.printf("📦 Generated %d Tests (30 UI, 40 API, 30 Integration).%n", suite.size());
        System.out.printf("⏱️  Theoretical Sequential Duration: %.1f seconds (~%.1f mins)%n", totalSequentialSec, totalSequentialSec / 60);

        // 2. Execute LPT Bin-Packing Sharding
        IShardingStrategy sharder = new LPTBinPackingSharder();
        List<TestBatch> batches = sharder.shard(suite, TARGET_WORKERS, 45.0);
        System.out.printf("⚖️  LPT Sharder produced %d balanced micro-batches.%n", batches.size());

        // 3. Connect to Redis & Dispatch
        try (RedisBrokerClient broker = new RedisBrokerClient(REDIS_HOST, REDIS_PORT)) {
            broker.cleanQueues();

            ResultAggregator aggregator = new ResultAggregator(TOTAL_TESTS);

            // Start listening for results BEFORE dispatching to prevent race conditions
            broker.subscribeToResults(aggregator);

            // Push work packets into Redis
            broker.publishBatches(batches);

            System.out.println("\n📡 Listening for events from worker fleet...");

            // Wait for workers to finish all 100 tests
            while (!aggregator.isComplete()) {
                Thread.sleep(250);
            }

            // Print final comprehensive metrics
            aggregator.printFinalReport(totalSequentialSec);

        } catch (Exception e) {
            System.err.println("Fatal Orchestrator error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static List<TestItem> generateTestSuite(int count) {
        List<TestItem> list = new ArrayList<>(count);
        Random rand = new Random(42); // deterministic seed for consistent runs

        // 30 UI Browser Tests (incorporates our real checkout.spec.ts)
        list.add(new TestItem("UI-001", "E-Commerce Checkout Flow", "tests/ui/checkout.spec.ts", TestType.BROWSER_UI, 28.5));
        for (int i = 2; i <= 30; i++) {
            double duration = 20.0 + (rand.nextDouble() * 20.0); // 20s - 40s
            list.add(new TestItem(String.format("UI-%03d", i), "Checkout_Step_Flow_" + i, null, TestType.BROWSER_UI, Math.round(duration * 10.0) / 10.0));
        }

        // 40 Headless API Tests (incorporates our real auth.spec.ts)
        list.add(new TestItem("API-001", "Authentication & User API", "tests/api/auth.spec.ts", TestType.HEADLESS_API, 1.2));
        for (int i = 2; i <= 40; i++) {
            double duration = 0.5 + (rand.nextDouble() * 2.0); // 0.5s - 2.5s
            list.add(new TestItem(String.format("API-%03d", i), "Auth_Token_Endpoint_" + i, null, TestType.HEADLESS_API, Math.round(duration * 10.0) / 10.0));
        }

        // 30 Integration Tests (incorporates our real order_flow.spec.ts)
        list.add(new TestItem("INT-001", "Order Lifecycle & Webhook", "tests/integration/order_flow.spec.ts", TestType.INTEGRATION, 8.4));
        for (int i = 2; i <= 30; i++) {
            double duration = 6.0 + (rand.nextDouble() * 8.0); // 6s - 14s
            list.add(new TestItem(String.format("INT-%03d", i), "Order_Webhook_Integration_" + i, null, TestType.INTEGRATION, Math.round(duration * 10.0) / 10.0));
        }

        return list;
    }
}
