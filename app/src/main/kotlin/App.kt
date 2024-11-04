package games.august.app

import games.august.counter.common.model.BigNumber
import games.august.counter.common.model.CounterConfig
import games.august.counter.service.CounterService
import games.august.counter.service.CounterServiceListener
import games.august.counter.service.model.CounterUpdate
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit

fun main() {
    // Create CounterService, passing in API key
    val service =
        CounterService.createDefault(
            config =
                CounterConfig(
                    apiKey = "insert-api-key-here",
                    flushErrorHandling =
                        CounterConfig.FlushErrorHandling(
                            reAddFailedUpdates = false,
                            maxFailureRetries = 1,
                            maxBackoff = 1.seconds,
                        ),
                ),
            listener =
                object : CounterServiceListener {
                    override fun onFlushRetry(
                        elapsedTime: Duration,
                        batchSize: Int,
                        numFailures: Int,
                    ) {
                        val elapsedTimeFormatted = elapsedTime.toString(DurationUnit.SECONDS)
                        println("Retrying flush after $elapsedTimeFormatted for $batchSize updates. Failed $numFailures times.")
                    }

                    override fun onFlushFailure(
                        elapsedTime: Duration,
                        batchSize: Int,
                        numFailures: Int,
                        batchRequeued: Boolean,
                    ) {
                        val elapsedTimeFormatted = elapsedTime.toString(DurationUnit.SECONDS)
                        println(
                            "Failed flushing $batchSize updates after $elapsedTimeFormatted, and $numFailures failures. Re-queued: $batchRequeued",
                        )
                    }

                    override fun onFlushSuccess(
                        elapsedTime: Duration,
                        batchSize: Int,
                    ) {
                        val elapsedTimeFormatted = elapsedTime.toString(DurationUnit.SECONDS)
                        println(
                            "Successfully flushed $batchSize updates after $elapsedTimeFormatted",
                        )
                    }
                },
        )
    service.start()
    // Update a single tag by `1` every second for 100 seconds
    runBlocking {
        var countersAdded = 0
        while (isActive) {
            val added =
                service.updateCounter(
                    update =
                        CounterUpdate(
                            tag = "example-app-counter",
                            added = BigNumber.create(amount = 1),
                        ),
                )
            if (!added) {
                println("Update failed: $countersAdded")
            }
            println("Added counter $countersAdded to \"example-app-counter\"!")
            delay(100.milliseconds)
            if (++countersAdded >= 200) {
                service.shutdown(flushPendingUpdates = true)
                break
            }
        }
    }
    println("Counters updated!")
}
