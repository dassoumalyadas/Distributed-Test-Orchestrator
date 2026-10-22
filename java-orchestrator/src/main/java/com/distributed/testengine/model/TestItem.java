package com.distributed.testengine.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class TestItem implements Comparable<TestItem> {
    @JsonProperty("test_id")
    private String testId;

    @JsonProperty("name")
    private String name;

    @JsonProperty("file")
    private String file;

    @JsonProperty("type")
    private TestType type;

    @JsonProperty("duration")
    private double estimatedDurationSec;

    public TestItem() {}

    public TestItem(String testId, String name, String file, TestType type, double estimatedDurationSec) {
        this.testId = testId;
        this.name = name;
        this.file = file;
        this.type = type;
        this.estimatedDurationSec = estimatedDurationSec;
    }

    public String getTestId() { return testId; }
    public String getName() { return name; }
    public String getFile() { return file; }
    public TestType getType() { return type; }
    public double getEstimatedDurationSec() { return estimatedDurationSec; }

    // Descending order sort by estimated duration for LPT Bin-Packing
    @Override
    public int compareTo(TestItem other) {
        return Double.compare(other.estimatedDurationSec, this.estimatedDurationSec);
    }
}
