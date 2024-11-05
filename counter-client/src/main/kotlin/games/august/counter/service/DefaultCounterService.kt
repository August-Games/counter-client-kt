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
import kotlinx.datetime.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.pow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTimedValue

internal class DefaultCounterService(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
    private val config: CounterConfig,
    private val counterApi: CounterApi,
) : CounterService {
    private val queuedUpdates = ConcurrentHashMap<String, List<CounterUpdate>>()
    private val queuedItemCount = AtomicInteger(0)
    private var paused: Boolean = false

    private var flushJob: Job? = null
    private var listener: CounterServiceListener? = null

    override fun setListener(listener: CounterServiceListener?) {
        this.listener = listener
    }

    override fun start() {
        flushJob =
            scope.launch {
                delay(config.flushConfig.cooldown)
                while (isActive) {
                    if (!paused) {
                        collapseQueue()
                        // After we've collapsed everything down, ideally we can send it all off in a single batch now,
                        // depending on the config passed in.
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
            flushJob?.cancel()
            scope.cancel()
            return
        }
        collapseQueue()
        var batch: List<CounterUpdate> = computeBatch()
        while (batch.isNotEmpty()) {
            flush(batch)
            batch = computeBatch()
        }
        flushJob?.cancelAndJoin()
    }

    override fun updateCounter(update: CounterUpdate): Boolean {
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
        val batchId = System.identityHashCode(batch).toString(16)
        listener?.onFlushStart(queuedItemCount.get(), batch.size, batchId)
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
                listener?.onFlushSuccess(elapsedTime, batch.size, batchId)
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
                    listener?.onFlushRetry(e, elapsedTime + backoff, batch.size, batchId, newFailureCount)
                    flush(batch, newFailureCount)
                } else {
                    if (config.flushErrorHandling.reAddFailedUpdates) {
                        listener?.onFlushFailure(e, elapsedTime, batch.size, batchId, newFailureCount, true)
                        batchUpdateCounter(batch)
                    } else {
                        listener?.onFlushFailure(e, elapsedTime, batch.size, batchId, newFailureCount, false)
                    }
                }
            }
    }

    private fun collapseQueue() {
        // Perform a client-side collapse BEFORE calculating the batch, so that we cram as much as
        // we can into the batch as possible. No need for all this processing to happen server-side if
        // we can do it here. Though the backend will ensure it's enforced server-side anyway.
        // This way is less compute for the backend.
        for (key in queuedUpdates.keys.iterator()) {
            queuedUpdates.compute(key) { k, v ->
                val totalBefore = v?.sumOf { it.added.remaining }
                val collapsed = v?.collapse(config.timeResolution)
                val totalAfter = collapsed?.sumOf { it.added.remaining }
                println(
                    "Collapsed $key ${v?.size} down to ${collapsed?.size}, total before: $totalBefore, after: $totalAfter",
                )
                collapsed
            }
        }
        // updates.size performs a full sweep to count
        queuedItemCount.updateAndGet { queuedUpdates.size }
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

    private fun List<CounterUpdate>.collapse(timeResolution: Duration): List<CounterUpdate> {
        // Group updates by collapsing timestamps to the nearest timeResolution boundary
        val groupedUpdates =
            this.groupBy { update ->
                val epochMillis = update.timestamp.toEpochMilliseconds()
                val resolutionMillis = timeResolution.inWholeMilliseconds
                val truncatedMillis = (epochMillis / resolutionMillis) * resolutionMillis
                Instant.fromEpochMilliseconds(truncatedMillis)
            }

        // For each group, combine all updates into a single update with summed added and removed counts
        return groupedUpdates.map { (truncatedTimestamp, updatesInGroup) ->
            updatesInGroup
                .reduce { acc, update ->
                    acc.copy(
                        added = acc.added.add(update.added),
                        removed = acc.removed.add(update.removed),
                    )
                }.copy(timestamp = truncatedTimestamp)
        }
    }
}
