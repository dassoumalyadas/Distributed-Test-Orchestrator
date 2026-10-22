package com.distributed.testengine.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TestBatch {
    @JsonProperty("batch_id")
    private String batchId;

    @JsonProperty("type")
    private TestType type;

    @JsonProperty("tests")
    private List<TestItem> tests = new ArrayList<>();

    @JsonProperty("total_duration")
    private double totalDurationSec = 0.0;

    public TestBatch() {}

    public TestBatch(String batchId, TestType type) {
        this.batchId = batchId;
        this.type = type;
    }

    public synchronized void addTest(TestItem test) {
        this.tests.add(test);
        this.totalDurationSec += test.getEstimatedDurationSec();
    }

    public String getBatchId() { return batchId; }
    public TestType getType() { return type; }
    public List<TestItem> getTests() { return Collections.unmodifiableList(tests); }
    public double getTotalDurationSec() { return totalDurationSec; }
}
