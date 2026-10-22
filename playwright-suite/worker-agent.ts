import Redis from 'ioredis';
import { exec } from 'child_process';
import { promisify } from 'util';
import * as fs from 'fs';

const execAsync = promisify(exec);

// Configuration from Environment Variables (with local defaults)
const REDIS_HOST = process.env.REDIS_HOST || '127.0.0.1';
const REDIS_PORT = parseInt(process.env.REDIS_PORT || '6379', 10);
const WORKER_ID = process.env.HOSTNAME || `worker-${Math.random().toString(36).substring(2, 8)}`;

interface TestItem {
  test_id: string;
  name: string;
  file?: string;
  type: string;
  duration?: number;
}

interface TestBatch {
  batch_id: string;
  type: string;
  tests: TestItem[];
}

interface TestResultEvent {
  run_id?: string;
  batch_id: string;
  test_id: string;
  test_name: string;
  worker_id: string;
  status: 'PASSED' | 'FAILED';
  duration_ms: number;
  error_message?: string;
  timestamp: number;
}

class WorkerAgent {
  private redisClient: Redis;
  private isRunning: boolean = true;
  private activeQueues: string[] = [
    'queue:browser_ui',
    'queue:integration',
    'queue:headless_api'
  ];

  constructor() {
    this.redisClient = new Redis({
      host: REDIS_HOST,
      port: REDIS_PORT,
      retryStrategy: (times) => Math.min(times * 1000, 5000),
      lazyConnect: true
    });
  }

  public async start(): Promise<void> {
    console.log(`====================================================`);
    console.log(`🤖 Starting Worker Agent: [${WORKER_ID}]`);
    console.log(`📡 Connecting to Redis Broker at ${REDIS_HOST}:${REDIS_PORT}...`);
    console.log(`====================================================`);

    try {
      await this.redisClient.connect();
      console.log(` Connected to Redis Broker.`);
    } catch (err: any) {
      console.error(`❌ Failed to connect to Redis: ${err.message}`);
      console.log(`💡 Reminder: Make sure Redis is running (e.g. docker run -d -p 6379:6379 redis:alpine)`);
      process.exit(1);
    }

    this.setupGracefulShutdown();
    await this.pollLoop();
  }

  /**
   * Main work loop: continuously pulls test batches using Redis BRPOP (Blocking Pop).
   * This is zero-CPU when the queues are empty!
   */
  private async pollLoop(): Promise<void> {
    console.log(`👂 Polling for work across queues: ${this.activeQueues.join(', ')}...`);

    while (this.isRunning) {
      try {
        // BRPOP waits up to 5 seconds for a job. If empty, returns null and loops.
        const response = await this.redisClient.brpop(...this.activeQueues, 5);

        if (!response) {
          continue; // Timeout reached, keep polling
        }

        const [queueName, rawData] = response;
        const batch: TestBatch = JSON.parse(rawData);
        console.log(`\n📥 [${WORKER_ID}] Picked up batch [${batch.batch_id}] from <${queueName}> with ${batch.tests.length} tests.`);

        await this.processBatch(batch);

      } catch (err: any) {
        if (!this.isRunning) break;
        console.error(`⚠️ Error in worker loop: ${err.message}`);
        await new Promise((resolve) => setTimeout(resolve, 2000));
      }
    }
  }

  /**
   * Executes tests in the batch and emits status events
   */
  private async processBatch(batch: TestBatch): Promise<void> {
    for (const test of batch.tests) {
      console.log(`  ▶️ [${WORKER_ID}] Executing test: ${test.test_id} - ${test.name}`);
      const startTime = Date.now();
      let status: 'PASSED' | 'FAILED' = 'PASSED';
      let errorMessage: string | undefined = undefined;

      try {
        if (test.file && fs.existsSync(test.file)) {
          // Execute actual Playwright test file
          await execAsync(`npx playwright test ${test.file}`);
        } else {
          // Simulated test execution based on duration budget
          const simulatedDuration = Math.max(100, (test.duration || 2) * 50);
          await new Promise((resolve) => setTimeout(resolve, simulatedDuration));
        }
      } catch (execErr: any) {
        status = 'FAILED';
        errorMessage = execErr.message?.split('\n')[0] || 'Assertion failed';
      }

      const durationMs = Date.now() - startTime;
      console.log(`  ${status === 'PASSED' ? '✅' : '❌'} [${WORKER_ID}] ${test.test_id} finished in ${durationMs}ms [${status}]`);

      // Emit event to Redis Pub/Sub for the Java Result Aggregator
      const event: TestResultEvent = {
        batch_id: batch.batch_id,
        test_id: test.test_id,
        test_name: test.name,
        worker_id: WORKER_ID,
        status,
        duration_ms: durationMs,
        error_message: errorMessage,
        timestamp: Date.now()
      };

      await this.redisClient.publish('events:results', JSON.stringify(event));
    }
  }

  private setupGracefulShutdown(): void {
    const shutdown = async () => {
      console.log(`\n🛑 Shutting down worker [${WORKER_ID}] gracefully...`);
      this.isRunning = false;
      await this.redisClient.quit();
      process.exit(0);
    };

    process.on('SIGINT', shutdown);
    process.on('SIGTERM', shutdown);
  }
}

// Start worker agent
const agent = new WorkerAgent();
agent.start();
