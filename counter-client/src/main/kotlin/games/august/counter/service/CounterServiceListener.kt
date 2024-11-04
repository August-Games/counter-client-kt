package games.august.counter.service

import kotlin.time.Duration

interface CounterServiceListener {
    fun onFlushRetry(
        throwable: Throwable,
        elapsedTime: Duration,
        batchSize: Int,
        numFailures: Int,
    )

    fun onFlushFailure(
        throwable: Throwable,
        elapsedTime: Duration,
        batchSize: Int,
        numFailures: Int,
        batchRequeued: Boolean,
    )

    fun onFlushSuccess(
        elapsedTime: Duration,
        batchSize: Int,
    )
}
