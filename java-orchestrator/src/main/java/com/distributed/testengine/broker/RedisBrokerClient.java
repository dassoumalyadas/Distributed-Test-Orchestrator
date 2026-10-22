package com.distributed.testengine.broker;

import com.distributed.testengine.aggregator.ResultAggregator;
import com.distributed.testengine.model.TestBatch;
import com.distributed.testengine.model.TestResultEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPubSub;

import java.util.List;

public class RedisBrokerClient implements AutoCloseable {
    private final JedisPool jedisPool;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RedisBrokerClient(String host, int port) {
        this.jedisPool = new JedisPool(host, port);
    }

    public void cleanQueues() {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.del("queue:browser_ui", "queue:integration", "queue:headless_api", "events:results");
            System.out.println("🧹 Cleared old queues from Redis.");
        }
    }

    public void publishBatches(List<TestBatch> batches) throws Exception {
        try (Jedis jedis = jedisPool.getResource()) {
            for (TestBatch batch : batches) {
                String queueName = batch.getType().getQueueName();
                String json = objectMapper.writeValueAsString(batch);
                jedis.rpush(queueName, json);
            }
            System.out.printf("🚀 Dispatched %d work packets into typed Redis queues.%n", batches.size());
        }
    }

    public void subscribeToResults(ResultAggregator aggregator) {
        // Run pubsub in a separate daemon thread
        Thread listenerThread = new Thread(() -> {
            try (Jedis jedis = jedisPool.getResource()) {
                jedis.subscribe(new JedisPubSub() {
                    @Override
                    public void onMessage(String channel, String message) {
                        try {
                            TestResultEvent event = objectMapper.readValue(message, TestResultEvent.class);
                            aggregator.recordStep(event);
                            if (aggregator.isComplete()) {
                                this.unsubscribe();
                            }
                        } catch (Exception e) {
                            System.err.println("\nFailed to parse result event: " + e.getMessage());
                        }
                    }
                }, "events:results");
            } catch (Exception e) {
                System.err.println("\nPubSub connection closed.");
            }
        }, "ResultListenerThread");
        listenerThread.setDaemon(true);
        listenerThread.start();
    }

    @Override
    public void close() {
        jedisPool.close();
    }
}
