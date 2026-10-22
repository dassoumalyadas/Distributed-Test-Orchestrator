package com.distributed.testengine.aggregator;

import com.distributed.testengine.model.TestResultEvent;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class ResultAggregator {
    private final int totalTests;
    private final AtomicInteger completedCount = new AtomicInteger(0);
    private final AtomicInteger passedCount = new AtomicInteger(0);
    private final AtomicInteger failedCount = new AtomicInteger(0);
    private final Map<String, Integer> workerStats = new ConcurrentHashMap<>();
    private final long startTime;

    public ResultAggregator(int totalTests) {
        this.totalTests = totalTests;
        this.startTime = System.currentTimeMillis();
    }

    public synchronized void recordStep(TestResultEvent event) {
        int done = completedCount.incrementAndGet();
        if ("PASSED".equalsIgnoreCase(event.getStatus())) {
            passedCount.incrementAndGet();
        } else {
            failedCount.incrementAndGet();
        }

        workerStats.merge(event.getWorkerId(), 1, Integer::sum);

        // Render dynamic progress bar
        double pct = ((double) done / totalTests) * 100.0;
        int barLength = 25;
        int filled = (int) (pct / (100.0 / barLength));
        String bar = "█".repeat(filled) + "░".repeat(Math.max(0, barLength - filled));

        System.out.printf("\r🚀 Progress: [%s] %3d/%3d (%5.1f%%) | Latest: %-8s on %-14s [%s]  ",
            bar, done, totalTests, pct, event.getTestId(), event.getWorkerId(), event.getStatus());
        System.out.flush();
    }

    public boolean isComplete() {
        return completedCount.get() >= totalTests;
    }

    public void printFinalReport(double theoreticalSequentialSec) {
        long elapsedMs = System.currentTimeMillis() - startTime;
        double wallClockSec = elapsedMs / 1000.0;

        System.out.println("\n\n" + "=".repeat(65));
        System.out.println("🏁 FINAL EXECUTION & ORCHESTRATION REPORT");
        System.out.println("=".repeat(65));
        System.out.printf("Total Tests Scheduled       : %d%n", totalTests);
        System.out.printf("Passed Tests                : %d ✅%n", passedCount.get());
        System.out.printf("Failed Tests                : %d ❌%n", failedCount.get());
        System.out.printf("Pass Rate                   : %.1f%%%n", ((double) passedCount.get() / totalTests) * 100);
        System.out.printf("Wall-Clock Execution Time   : %.2f seconds ⚡%n", wallClockSec);
        System.out.printf("Theoretical Sequential Time : %.1f seconds%n", theoreticalSequentialSec);
        double speedup = theoreticalSequentialSec / Math.max(0.1, wallClockSec);
        System.out.printf("Parallel Speedup Factor     : %.1fx faster across workers!%n", speedup);
        System.out.println("-".repeat(65));
        System.out.println("📊 WORKER LOAD DISTRIBUTION (Proving LPT Balance):");
        workerStats.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(entry -> System.out.printf("  • %-18s : %3d tests completed%n", entry.getKey(), entry.getValue()));
        System.out.println("=".repeat(65));
    }
}
