package games.august.counter.service

import kotlin.time.Duration

interface CounterServiceListener {
    fun onFlushRetry(
        elapsedTime: Duration,
        batchSize: Int,
        numFailures: Int,
    )

    fun onFlushFailure(
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
