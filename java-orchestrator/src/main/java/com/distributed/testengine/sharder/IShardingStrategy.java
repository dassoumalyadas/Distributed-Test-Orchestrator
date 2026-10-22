package com.distributed.testengine.sharder;

import com.distributed.testengine.model.TestBatch;
import com.distributed.testengine.model.TestItem;
import java.util.List;

public interface IShardingStrategy {
    /**
     * Partitions a list of tests into balanced batches across workers.
     *
     * @param tests The complete suite of tests to run
     * @param targetWorkers Number of available worker nodes (e.g. 10)
     * @param maxBatchDurationSec Maximum target execution budget for a single micro-batch
     * @return List of balanced TestBatch objects ready for queueing
     */
    List<TestBatch> shard(List<TestItem> tests, int targetWorkers, double maxBatchDurationSec);
}
