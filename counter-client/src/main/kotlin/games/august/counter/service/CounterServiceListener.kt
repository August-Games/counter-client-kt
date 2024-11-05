package games.august.counter.service

import kotlin.time.Duration

interface CounterServiceListener {
    fun onFlushStart(
        batchSize: Int,
        batchId: String,
    )

    fun onFlushRetry(
        throwable: Throwable,
        elapsedTime: Duration,
        batchSize: Int,
        batchId: String,
        numFailures: Int,
    )

    fun onFlushFailure(
        throwable: Throwable,
        elapsedTime: Duration,
        batchSize: Int,
        batchId: String,
        numFailures: Int,
        batchRequeued: Boolean,
    )

    fun onFlushSuccess(
        elapsedTime: Duration,
        batchSize: Int,
        batchId: String,
    )
}
