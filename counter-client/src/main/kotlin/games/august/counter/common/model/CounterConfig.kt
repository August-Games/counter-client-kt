package games.august.counter.common.model

import games.august.counter.service.model.CounterUpdate
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

data class CounterConfig(
    val apiKey: String,
    val flushConfig: FlushConfig = FlushConfig(),
    val flushErrorHandling: FlushErrorHandling = FlushErrorHandling(),
) {
    /**
     * @param cooldown The delay _after completing a flush_ to perform another flush.
     * @param maxBatchSize The amount of [CounterUpdate] to send in a single API call.
     * @param maxBufferSize Roughly what you want the max buffer size to be for pending [CounterUpdate] items.
     *  If the current buffer size is `maxBufferSize - 1`, and 100 updates are queued, then the 100 will be added,
     *  but subsequent enqueues without a flush will fail.
     */
    data class FlushConfig(
        val cooldown: Duration = 5.seconds,
        val maxBatchSize: Int = 100,
        val maxBufferSize: Int = 5000,
    )

    data class FlushErrorHandling(
        val maxFailureRetries: Int = 3,
        val minBackoff: Duration = 500.milliseconds,
        val maxBackoff: Duration = 60.seconds,
    )
}
