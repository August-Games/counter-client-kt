package games.august.counter.service

import com.google.common.truth.Truth.assertThat
import games.august.counter.common.model.BigNumber
import games.august.counter.common.model.CounterConfig
import games.august.counter.common.model.CounterConfig.FlushConfig
import games.august.counter.service.api.CounterApi
import games.august.counter.service.api.model.BatchUpdateCounterRequest
import games.august.counter.service.api.model.CounterAggregate
import games.august.counter.service.api.model.GetCountAggregateRequest
import games.august.counter.service.api.model.UpdateCounterRequest
import games.august.counter.service.model.CounterUpdate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultCounterServiceTest {
    private val counterApiTestDelegate =
        object : CounterApi {
            override suspend fun updateCounter(
                apiToken: String,
                updateCounterRequest: UpdateCounterRequest,
            ): Result<Unit> = error("Stub!")

            override suspend fun batchUpdateCounters(
                apiToken: String,
                batchUpdateCounterRequest: BatchUpdateCounterRequest,
            ): Result<Unit> = error("Stub!")

            override suspend fun getCountAggregate(
                apiToken: String,
                tag: String,
                updateCounterRequest: GetCountAggregateRequest,
            ): Result<CounterAggregate> = error("Stub!")
        }

    @Test
    fun `Test updateCounter+batchUpdateCounter throws IllegalStateException when start not called`() =
        runTest {
            val service =
                createService(
                    scope = this,
                    counterApi = object : CounterApi by counterApiTestDelegate {},
                )
            assertThrows<IllegalStateException> {
                service.updateCounter(
                    update =
                        CounterUpdate(
                            tag = "test-tag",
                            added = BigNumber.create(100),
                            timestamp = getNow(),
                        ),
                )
            }
            assertThrows<IllegalStateException> {
                service.batchUpdateCounter(
                    updates =
                        listOf(
                            CounterUpdate(
                                tag = "test-tag",
                                added = BigNumber.create(100),
                                timestamp = getNow(),
                            ),
                        ),
                )
            }
        }

    @Test
    fun `Test updateCounter+batchUpdateCounter returns false when max buffer size is 0`() =
        runCancellingTest {
            val service =
                createService(
                    scope = this,
                    config =
                        CounterConfig(
                            apiKey = "test_key",
                            flushConfig =
                                FlushConfig(
                                    maxBufferSize = 0,
                                ),
                        ),
                    counterApi = object : CounterApi by counterApiTestDelegate {},
                )
            service.start()
            assertThat(
                service.updateCounter(
                    update =
                        CounterUpdate(
                            tag = "test-tag",
                            added = BigNumber.create(100),
                            timestamp = getNow(),
                        ),
                ),
            ).isFalse()
            assertThat(
                service.batchUpdateCounter(
                    updates =
                        listOf(
                            CounterUpdate(
                                tag = "test-tag",
                                added = BigNumber.create(100),
                                timestamp = getNow(),
                            ),
                        ),
                ),
            ).isFalse()
        }

    @Test
    fun `Test updateCounter returns false when max buffer size is full`() =
        runCancellingTest {
            val service =
                createService(
                    scope = this,
                    config =
                        CounterConfig(
                            apiKey = "test_key",
                            flushConfig =
                                FlushConfig(
                                    maxBufferSize = 1,
                                ),
                        ),
                    counterApi = object : CounterApi by counterApiTestDelegate {},
                )
            service.start()
            // Buffer has space
            assertThat(
                service.updateCounter(
                    update =
                        CounterUpdate(
                            tag = "test-tag",
                            added = BigNumber.create(100),
                            timestamp = getNow(),
                        ),
                ),
            ).isTrue()
            // Buffer is full, now fail
            assertThat(
                service.updateCounter(
                    update =
                        CounterUpdate(
                            tag = "test-tag",
                            added = BigNumber.create(100),
                            timestamp = getNow(),
                        ),
                ),
            ).isFalse()
        }

    @Test
    fun `Test batchUpdateCounter returns false when max buffer size is full`() =
        runCancellingTest {
            val service =
                createService(
                    scope = this,
                    config =
                        CounterConfig(
                            apiKey = "test_key",
                            flushConfig =
                                FlushConfig(
                                    maxBufferSize = 100,
                                ),
                        ),
                    counterApi = object : CounterApi by counterApiTestDelegate {},
                )
            service.start()
            // Buffer has space
            assertThat(
                service.batchUpdateCounter(
                    updates =
                        (0 until 100).map {
                            CounterUpdate(
                                tag = "test-tag",
                                added = BigNumber.create(100),
                                timestamp = getNow(),
                            )
                        },
                ),
            ).isTrue()
            // Buffer is full, now fail
            assertThat(
                service.batchUpdateCounter(
                    updates =
                        listOf(
                            CounterUpdate(
                                tag = "test-tag",
                                added = BigNumber.create(100),
                                timestamp = getNow(),
                            ),
                        ),
                ),
            ).isFalse()
        }

    @Test
    fun `Test updateCounter+batchUpdateCounter returns true even when service is paused, when buffer has space`() =
        runCancellingTest {
            val service =
                createService(
                    scope = this,
                    config =
                        CounterConfig(
                            apiKey = "test_key",
                            flushConfig =
                                FlushConfig(
                                    maxBufferSize = 100,
                                ),
                        ),
                    counterApi = object : CounterApi by counterApiTestDelegate {},
                )
            service.start()
            service.pause()
            assertThat(
                service.updateCounter(
                    update =
                        CounterUpdate(
                            tag = "test-tag",
                            added = BigNumber.create(100),
                            timestamp = getNow(),
                        ),
                ),
            ).isTrue()
            assertThat(
                service.batchUpdateCounter(
                    updates =
                        (0 until 50).map {
                            CounterUpdate(
                                tag = "test-tag",
                                added = BigNumber.create(100),
                                timestamp = getNow(),
                            )
                        },
                ),
            ).isTrue()
        }

    @Test
    fun `Test flushing occurs on schedule`() =
        runCancellingTest {
            val flushRequests = mutableListOf<BatchUpdateCounterRequest>()
            val service =
                createService(
                    scope = this,
                    config =
                        CounterConfig(
                            apiKey = "test_key",
                            flushConfig =
                                FlushConfig(
                                    cooldown = 5.seconds,
                                    maxBufferSize = 100,
                                ),
                        ),
                    counterApi =
                        object : CounterApi by counterApiTestDelegate {
                            override suspend fun batchUpdateCounters(
                                apiToken: String,
                                batchUpdateCounterRequest: BatchUpdateCounterRequest,
                            ): Result<Unit> {
                                flushRequests += batchUpdateCounterRequest // So we can verify when flushing happens
                                return Result.success(Unit)
                            }
                        },
                )
            service.start()
            assertThat(
                service.updateCounter(
                    update =
                        CounterUpdate(
                            tag = "test-tag",
                            added = BigNumber.create(100),
                            timestamp = getNow(),
                        ),
                ),
            ).isTrue()
            // Offset by 1 millisecond because moving forward by 5 seconds makes it perfectly align with the flush cooldown
            advanceTimeBy(1.milliseconds)
            // Wait 1 second, we should still have no flushes
            advanceTimeBy(1.seconds)
            assertThat(flushRequests).isEmpty()
            // Wait another 4 seconds, making it 5 seconds, expect 1 flush
            advanceTimeBy(4.seconds)
            assertThat(flushRequests).hasSize(1)
            // Wait another 5 seconds, expect 1 flush still because nothing else was queued
            advanceTimeBy(5.seconds)
            assertThat(flushRequests).hasSize(1)
            // Queue another update, when wait 1 second at a time, assert 1 flush until 5 seconds elapsed, then assert
            // 2 flushes
            assertThat(
                service.updateCounter(
                    update =
                        CounterUpdate(
                            tag = "test-tag",
                            added = BigNumber.create(100),
                            timestamp = getNow(),
                        ),
                ),
            ).isTrue()
            assertThat(flushRequests).hasSize(1)
            advanceTimeBy(1.seconds)
            assertThat(flushRequests).hasSize(1)
            advanceTimeBy(1.seconds)
            assertThat(flushRequests).hasSize(1)
            advanceTimeBy(1.seconds)
            assertThat(flushRequests).hasSize(1)
            advanceTimeBy(1.seconds)
            assertThat(flushRequests).hasSize(1)
            advanceTimeBy(1.seconds)
            assertThat(flushRequests).hasSize(2)
        }

    @Test
    fun `Test exponential backoff for failed flushes`() =
        runCancellingTest {
            var periodicFlushFailureCount = 0
            val flushRequests = mutableListOf<BatchUpdateCounterRequest>()
            val service =
                createService(
                    scope = this,
                    config =
                        CounterConfig(
                            apiKey = "test_key",
                            flushConfig =
                                FlushConfig(
                                    cooldown = 5.seconds,
                                    maxBufferSize = 100,
                                ),
                            flushErrorHandling =
                                CounterConfig.FlushErrorHandling(
                                    maxFailureRetries = 5,
                                    minBackoff = 10.seconds,
                                    maxBackoff = 100.seconds,
                                    getBackoffJitter = { 0.0 },
                                ),
                        ),
                    counterApi =
                        object : CounterApi by counterApiTestDelegate {
                            override suspend fun batchUpdateCounters(
                                apiToken: String,
                                batchUpdateCounterRequest: BatchUpdateCounterRequest,
                            ): Result<Unit> {
                                flushRequests += batchUpdateCounterRequest // So we can verify when flushing happens
                                return Result.failure(RuntimeException("test api failure"))
                            }
                        },
                    listener =
                        object : CounterServiceListener {
                            override fun onFlushRetry(
                                elapsedTime: Duration,
                                batchSize: Int,
                                numFailures: Int,
                            ) {
                            }

                            override fun onFlushFailure(
                                elapsedTime: Duration,
                                batchSize: Int,
                                numFailures: Int,
                                batchRequeued: Boolean,
                            ) {
                                periodicFlushFailureCount++
                            }

                            override fun onFlushSuccess(
                                elapsedTime: Duration,
                                batchSize: Int,
                            ) {
                            }
                        },
                )
            service.start()
            assertThat(
                service.updateCounter(
                    update = CounterUpdate(tag = "test-tag", added = BigNumber.create(100), timestamp = getNow()),
                ),
            ).isTrue()
            assertThat(flushRequests).isEmpty()
            assertThat(periodicFlushFailureCount).isEqualTo(0)
            advanceTimeBy(1.milliseconds)
            advanceTimeBy(5.seconds)
            assertThat(flushRequests).hasSize(1)
            // RETRY-1: Expect a retry in (2) 10 seconds
            advanceTimeBy(5.seconds)
            assertThat(flushRequests).hasSize(1)
            advanceTimeBy(5.seconds)
            assertThat(flushRequests).hasSize(2)
            assertThat(periodicFlushFailureCount).isEqualTo(0)
            // RETRY-2: Expect a retry in (4) 10 seconds
            advanceTimeBy(5.seconds)
            assertThat(flushRequests).hasSize(2)
            advanceTimeBy(5.seconds)
            assertThat(flushRequests).hasSize(3)
            assertThat(periodicFlushFailureCount).isEqualTo(0)
            // RETRY-3: Expect a retry in (8) 10 seconds
            advanceTimeBy(5.seconds)
            assertThat(flushRequests).hasSize(3)
            advanceTimeBy(5.seconds)
            assertThat(flushRequests).hasSize(4)
            assertThat(periodicFlushFailureCount).isEqualTo(0)
            // RETRY-4: Expect a retry in 16 seconds
            advanceTimeBy(10.seconds)
            assertThat(flushRequests).hasSize(4)
            advanceTimeBy(6.seconds)
            assertThat(flushRequests).hasSize(5)
            assertThat(periodicFlushFailureCount).isEqualTo(0)
            // RETRY-5: Expect a retry in 32 seconds
            advanceTimeBy(20.seconds)
            assertThat(flushRequests).hasSize(5)
            advanceTimeBy(12.seconds)
            assertThat(flushRequests).hasSize(6)
            assertThat(periodicFlushFailureCount).isEqualTo(1)
        }

    @Test
    fun `Test eventual success of flush, the show must go on`() =
        runCancellingTest {
            val failureException = RuntimeException("test api failure")
            var batchUpdateReturnValue: Result<Unit> = Result.failure(failureException)
            var periodicFlushFailureCount = 0
            val flushRequests = mutableListOf<BatchUpdateCounterRequest>()
            val service =
                createService(
                    scope = this,
                    config =
                        CounterConfig(
                            apiKey = "test_key",
                            flushConfig =
                                FlushConfig(
                                    cooldown = 5.seconds,
                                    maxBufferSize = 100,
                                ),
                            flushErrorHandling =
                                CounterConfig.FlushErrorHandling(
                                    maxFailureRetries = 5,
                                    minBackoff = 10.seconds,
                                    maxBackoff = 100.seconds,
                                    getBackoffJitter = { 0.0 },
                                ),
                        ),
                    counterApi =
                        object : CounterApi by counterApiTestDelegate {
                            override suspend fun batchUpdateCounters(
                                apiToken: String,
                                batchUpdateCounterRequest: BatchUpdateCounterRequest,
                            ): Result<Unit> {
                                flushRequests += batchUpdateCounterRequest // So we can verify when flushing happens
                                return batchUpdateReturnValue
                            }
                        },
                    listener =
                        object : CounterServiceListener {
                            override fun onFlushRetry(
                                elapsedTime: Duration,
                                batchSize: Int,
                                numFailures: Int,
                            ) {
                            }

                            override fun onFlushFailure(
                                elapsedTime: Duration,
                                batchSize: Int,
                                numFailures: Int,
                                batchRequeued: Boolean,
                            ) {
                                periodicFlushFailureCount++
                            }

                            override fun onFlushSuccess(
                                elapsedTime: Duration,
                                batchSize: Int,
                            ) {
                            }
                        },
                )
            service.start()
            assertThat(
                service.updateCounter(
                    update = CounterUpdate(tag = "test-tag", added = BigNumber.create(100), timestamp = getNow()),
                ),
            ).isTrue()
            assertThat(flushRequests).isEmpty()
            assertThat(periodicFlushFailureCount).isEqualTo(0)
            advanceTimeBy(1.milliseconds)
            advanceTimeBy(5.seconds)
            // RETRY-1: Expect a retry in (2) 10 seconds
            advanceTimeBy(5.seconds)
            assertThat(flushRequests).hasSize(1)
            advanceTimeBy(5.seconds)
            assertThat(flushRequests).hasSize(2)
            assertThat(periodicFlushFailureCount).isEqualTo(0)
            // Change the fake API to succeed next, and watch the show carry on
            // Enqueue 2 more updates while we're mid-flush, and we should see them flush out after we finally succeed
            // this flush.
            batchUpdateReturnValue = Result.success(Unit)
            assertThat(
                service.updateCounter(
                    update = CounterUpdate(tag = "test-tag", added = BigNumber.create(100), timestamp = getNow()),
                ),
            ).isTrue()
            assertThat(
                service.updateCounter(
                    update = CounterUpdate(tag = "test-tag", added = BigNumber.create(100), timestamp = getNow()),
                ),
            ).isTrue()
            // RETRY-2: Expect a retry in (4) 10 seconds
            advanceTimeBy(5.seconds)
            assertThat(flushRequests).hasSize(2)
            advanceTimeBy(5.seconds)
            assertThat(flushRequests).hasSize(3) // <-- success, we didn't retry again.
            assertThat(periodicFlushFailureCount).isEqualTo(0)
            advanceTimeBy(5.seconds)
            assertThat(flushRequests).hasSize(4) // 1 more flush for those other 2 updates we queued before
        }

    @Test
    fun `Test concurrent writes result in exactly correct amount of updates in flush request`() =
        runCancellingTest {
            var periodicFlushFailureCount = 0
            val flushRequests = mutableListOf<BatchUpdateCounterRequest>()
            val service =
                createService(
                    scope = this,
                    config =
                        CounterConfig(
                            apiKey = "test_key",
                            flushConfig =
                                FlushConfig(
                                    cooldown = 5.seconds,
                                    maxBufferSize = 1000,
                                    maxBatchSize = 10,
                                ),
                            flushErrorHandling =
                                CounterConfig.FlushErrorHandling(
                                    maxFailureRetries = 5,
                                    minBackoff = 10.seconds,
                                    maxBackoff = 100.seconds,
                                    getBackoffJitter = { 0.0 },
                                ),
                        ),
                    counterApi =
                        object : CounterApi by counterApiTestDelegate {
                            override suspend fun batchUpdateCounters(
                                apiToken: String,
                                batchUpdateCounterRequest: BatchUpdateCounterRequest,
                            ): Result<Unit> {
                                flushRequests += batchUpdateCounterRequest // So we can verify when flushing happens
                                return Result.success(Unit)
                            }
                        },
                    listener =
                        object : CounterServiceListener {
                            override fun onFlushRetry(
                                elapsedTime: Duration,
                                batchSize: Int,
                                numFailures: Int,
                            ) {
                                periodicFlushFailureCount++
                            }

                            override fun onFlushFailure(
                                elapsedTime: Duration,
                                batchSize: Int,
                                numFailures: Int,
                                batchRequeued: Boolean,
                            ) {
                                periodicFlushFailureCount++
                            }

                            override fun onFlushSuccess(
                                elapsedTime: Duration,
                                batchSize: Int,
                            ) {
                            }
                        },
                )
            service.start()

            val jobs = mutableListOf<Job>()
            // Launch 1000 coroutines that concurrently update 1 time each
            // Expect 100 flush requests with a batch size of 10 after 500 seconds
            for (i in 0 until 1000) {
                jobs +=
                    launch {
                        service
                            .updateCounter(
                                update = CounterUpdate(tag = "test-tag", added = BigNumber.create(100), timestamp = getNow()),
                            )
                    }
            }
            joinAll(*jobs.toTypedArray())
            jobs.clear()
            assertThat(flushRequests).isEmpty()
            advanceTimeBy(1.milliseconds)
            repeat(100) {
                advanceTimeBy(5.seconds)
                assertThat(flushRequests).hasSize(it + 1)
                assertThat(flushRequests[it].updates).hasSize(10)
            }
        }

    private fun runCancellingTest(block: suspend TestScope.() -> Unit) {
        try {
            runTest {
                block()
                cancel()
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            val message = e.message
            if (message != null && message.startsWith("TestScopeImpl was cancelled")) {
                // Eat it
                // Because We are testing DefaultCounterService which has `while(isActive) { ... }` persistent working
                // code while the coroutine scope is active, we need to be able to cancel the test after verifying what
                // we need to verify.
                // Otherwise, virtual time advances and the service begins to flush queued items to the API endlessly.
                // This allows us to call `cancel()` at the end of the test when we're done verifying what we need.
            } else {
                throw e
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun TestScope.getNow() = Instant.fromEpochMilliseconds(this.testScheduler.currentTime)

    private fun createService(
        scope: CoroutineScope,
        config: CounterConfig =
            CounterConfig(
                apiKey = "test-api-key",
            ),
        counterApi: CounterApi = object : CounterApi by counterApiTestDelegate {},
        listener: CounterServiceListener =
            object : CounterServiceListener {
                override fun onFlushRetry(
                    elapsedTime: Duration,
                    batchSize: Int,
                    numFailures: Int,
                ) {
                }

                override fun onFlushFailure(
                    elapsedTime: Duration,
                    batchSize: Int,
                    numFailures: Int,
                    batchRequeued: Boolean,
                ) {
                }

                override fun onFlushSuccess(
                    elapsedTime: Duration,
                    batchSize: Int,
                ) {
                }
            },
    ): CounterService =
        DefaultCounterService(
            scope = scope,
            config = config,
            counterApi = counterApi,
            listener = listener,
        )
}
