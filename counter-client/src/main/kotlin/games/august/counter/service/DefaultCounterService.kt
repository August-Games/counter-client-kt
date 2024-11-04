package games.august.counter.service

import games.august.counter.common.model.CounterConfig
import games.august.counter.service.api.CounterApi
import games.august.counter.service.api.model.BatchUpdateCounterRequest
import games.august.counter.service.mapping.toRequest
import games.august.counter.service.model.CounterUpdate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.pow
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTimedValue

internal class DefaultCounterService(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
    private val config: CounterConfig,
    private val counterApi: CounterApi,
    private val listener: CounterServiceListener? = null,
) : CounterService {
    private val queuedUpdates = ConcurrentHashMap<String, List<CounterUpdate>>()
    private val queuedItemCount = AtomicInteger(0)
    private var paused: Boolean = false

    private var periodicQueuedItemCountUpdateJob: Job? = null
    private var flushJob: Job? = null

    override fun start() {
        periodicQueuedItemCountUpdateJob =
            scope.launch {
                // Periodically reset queuedItemCount in case of inconsistencies due to high concurrency
                while (isActive) {
                    queuedItemCount.updateAndGet { queuedUpdates.size } // updates.size performs a full sweep to count
                    delay(60.seconds)
                }
            }
        flushJob =
            scope.launch {
                delay(config.flushConfig.cooldown)
                while (isActive) {
                    if (!paused) {
                        flush()
                    }
                    delay(config.flushConfig.cooldown)
                }
            }
    }

    override fun resume() {
        paused = false
    }

    override fun pause() {
        paused = true
    }

    override suspend fun shutdown(flushPendingUpdates: Boolean) {
        if (!flushPendingUpdates) {
            periodicQueuedItemCountUpdateJob?.cancel()
            flushJob?.cancel()
            scope.cancel()
            return
        }
        var batch: List<CounterUpdate> = computeBatch()
        while (batch.isNotEmpty()) {
            flush(batch)
            batch = computeBatch()
        }
        periodicQueuedItemCountUpdateJob?.cancelAndJoin()
        flushJob?.cancelAndJoin()
    }

    override fun updateCounter(update: CounterUpdate): Boolean {
        periodicQueuedItemCountUpdateJob ?: errorNotStarted()
        flushJob ?: errorNotStarted()
        val size = queuedItemCount.get()
        if (size >= config.flushConfig.maxBufferSize) return false
        queuedUpdates.compute(update.tag) { tag, previousUpdates ->
            queuedItemCount.incrementAndGet()
            previousUpdates?.plus(update) ?: listOf(update)
        }
        return true
    }

    override fun batchUpdateCounter(updates: List<CounterUpdate>): Boolean {
        periodicQueuedItemCountUpdateJob ?: errorNotStarted()
        flushJob ?: errorNotStarted()
        val size = queuedItemCount.get()
        if (size >= config.flushConfig.maxBufferSize) return false
        for ((tag, updates) in updates.groupBy { it.tag }) {
            queuedUpdates.compute(tag) { tag, previousUpdates ->
                queuedItemCount.updateAndGet { it + updates.size }
                previousUpdates?.plus(updates) ?: updates
            }
        }
        return true
    }

    private fun errorNotStarted(): Nothing = throw IllegalStateException("CounterService.start() must be called before updating counters")

    private suspend fun flush(
        batch: List<CounterUpdate> = computeBatch(),
        failureCount: Int = 0,
    ) {
        if (batch.isEmpty()) return
        val (result, elapsedTime) =
            measureTimedValue {
                counterApi
                    .batchUpdateCounters(
                        apiKey = config.apiKey,
                        batchUpdateCounterRequest =
                            BatchUpdateCounterRequest(
                                updates = batch.map(CounterUpdate::toRequest),
                            ),
                    )
            }
        result
            .onSuccess {
                listener?.onFlushSuccess(elapsedTime, batch.size)
            }.onFailure { e ->
                val newFailureCount = failureCount + 1
                if (failureCount < config.flushErrorHandling.maxFailureRetries) {
                    val jitter = config.flushErrorHandling.getBackoffJitter()
                    val backoff =
                        (2.0.pow(failureCount.toDouble() + 1.0) + jitter)
                            .seconds
                            .coerceIn(
                                minimumValue = config.flushErrorHandling.minBackoff,
                                maximumValue = config.flushErrorHandling.maxBackoff,
                            )
                    delay(backoff)
                    listener?.onFlushRetry(elapsedTime + backoff, batch.size, newFailureCount)
                    flush(batch, newFailureCount)
                } else {
                    if (config.flushErrorHandling.reAddFailedUpdates) {
                        listener?.onFlushFailure(elapsedTime, batch.size, newFailureCount, true)
                        batchUpdateCounter(batch)
                    } else {
                        listener?.onFlushFailure(elapsedTime, batch.size, newFailureCount, false)
                    }
                }
            }
    }

    private fun computeBatch(): List<CounterUpdate> {
        val maxBatchSize = config.flushConfig.maxBatchSize
        val batch = mutableListOf<CounterUpdate>()
        var added = 0
        // Shuffle in case we can't keep up with counter updates incoming. We don't want to starve the service of
        // updates for tags which are less fortunate when it comes to key priority in [ConcurrentHashMap]
        val keys = queuedUpdates.keys().toList().shuffled()
        for (key in keys) {
            val remainingBatchSize = maxBatchSize - batch.size
            queuedUpdates.compute(key) { tag, previousUpdates ->
                previousUpdates ?: return@compute null
                when {
                    previousUpdates.size > remainingBatchSize -> {
                        batch.addAll(previousUpdates.take(remainingBatchSize))
                        added += remainingBatchSize
                        previousUpdates.subList(remainingBatchSize, previousUpdates.size)
                    }
                    else -> {
                        batch.addAll(previousUpdates)
                        added += previousUpdates.size
                        emptyList()
                    }
                }
            }
            if (batch.size >= maxBatchSize) {
                break
            }
        }
        queuedItemCount.updateAndGet { it - added }
        return batch
    }
}
