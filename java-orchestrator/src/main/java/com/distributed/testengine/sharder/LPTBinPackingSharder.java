package com.distributed.testengine.sharder;

import com.distributed.testengine.model.TestBatch;
import com.distributed.testengine.model.TestItem;
import com.distributed.testengine.model.TestType;

import java.util.*;

public class LPTBinPackingSharder implements IShardingStrategy {

    @Override
    public List<TestBatch> shard(List<TestItem> tests, int targetWorkers, double maxBatchDurationSec) {
        // 1. Group tests by test type to preserve hardware/queue segregation
        Map<TestType, List<TestItem>> grouped = new EnumMap<>(TestType.class);
        for (TestType type : TestType.values()) {
            grouped.put(type, new ArrayList<>());
        }
        for (TestItem test : tests) {
            grouped.get(test.getType()).add(test);
        }

        List<TestBatch> allBatches = new ArrayList<>();
        int batchSequence = 1;

        for (Map.Entry<TestType, List<TestItem>> entry : grouped.entrySet()) {
            TestType type = entry.getKey();
            List<TestItem> typeTests = entry.getValue();

            if (typeTests.isEmpty()) {
                continue;
            }

            // 2. Sort tests descending by duration (Longest Processing Time First)
            Collections.sort(typeTests);

            // 3. Min-Heap of batches ordered by total duration
            PriorityQueue<TestBatch> minHeap = new PriorityQueue<>(
                Comparator.comparingDouble(TestBatch::getTotalDurationSec)
            );

            // Allocate initial bins based on worker proportion
            int initialBins = Math.max(2, targetWorkers / 3);
            for (int i = 0; i < initialBins; i++) {
                minHeap.add(new TestBatch(String.format("BATCH-%s-%03d", type.getValue().toUpperCase(), batchSequence++), type));
            }

            // 4. Greedily place tests into the least loaded batch
            for (TestItem test : typeTests) {
                TestBatch leastLoaded = minHeap.poll();

                // If adding this test exceeds the micro-batch budget, seal this batch and spawn a new one
                if (leastLoaded.getTotalDurationSec() + test.getEstimatedDurationSec() > maxBatchDurationSec && !leastLoaded.getTests().isEmpty()) {
                    minHeap.add(leastLoaded); // Put back the sealed batch
                    TestBatch newBatch = new TestBatch(String.format("BATCH-%s-%03d", type.getValue().toUpperCase(), batchSequence++), type);
                    newBatch.addTest(test);
                    minHeap.add(newBatch);
                } else {
                    leastLoaded.addTest(test);
                    minHeap.add(leastLoaded);
                }
            }

            // Collect non-empty batches
            for (TestBatch batch : minHeap) {
                if (!batch.getTests().isEmpty()) {
                    allBatches.add(batch);
                }
            }
        }

        return allBatches;
    }
}
