package com.distributed.testengine.model;

import com.fasterxml.jackson.annotation.JsonValue;

public enum TestType {
    BROWSER_UI("browser_ui", "queue:browser_ui"),
    INTEGRATION("integration", "queue:integration"),
    HEADLESS_API("headless_api", "queue:headless_api");

    private final String value;
    private final String queueName;

    TestType(String value, String queueName) {
        this.value = value;
        this.queueName = queueName;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    public String getQueueName() {
        return queueName;
    }
}
