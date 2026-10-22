package com.distributed.testengine.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class TestResultEvent {
    @JsonProperty("batch_id")
    private String batchId;

    @JsonProperty("test_id")
    private String testId;

    @JsonProperty("test_name")
    private String testName;

    @JsonProperty("worker_id")
    private String workerId;

    @JsonProperty("status")
    private String status;

    @JsonProperty("duration_ms")
    private long durationMs;

    @JsonProperty("error_message")
    private String errorMessage;

    @JsonProperty("timestamp")
    private long timestamp;

    public TestResultEvent() {}

    public String getBatchId() { return batchId; }
    public String getTestId() { return testId; }
    public String getTestName() { return testName; }
    public String getWorkerId() { return workerId; }
    public String getStatus() { return status; }
    public long getDurationMs() { return durationMs; }
    public String getErrorMessage() { return errorMessage; }
    public long getTimestamp() { return timestamp; }
}
