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
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit

fun main() {
    val listener =
        object : CounterServiceListener {
            override fun onFlushStart(
                queueSize: Int,
                batchSize: Int,
                batchId: String,
            ) {
                println("Starting flush for batch $batchId with $batchSize updates.")
            }

            override fun onFlushRetry(
                throwable: Throwable,
                elapsedTime: Duration,
                batchSize: Int,
                batchId: String,
                numFailures: Int,
            ) {
                val elapsedTimeFormatted = elapsedTime.toString(DurationUnit.SECONDS)
                println("Retrying flush for batch $batchId with $batchSize updates after $elapsedTimeFormatted. Failed $numFailures times.")
                throwable.printStackTrace()
            }

            override fun onFlushFailure(
                throwable: Throwable,
                elapsedTime: Duration,
                batchSize: Int,
                batchId: String,
                numFailures: Int,
                batchRequeued: Boolean,
            ) {
                val elapsedTimeFormatted = elapsedTime.toString(DurationUnit.SECONDS)
                println(
                    "Failed flush for batch $batchId with $batchSize updates after $elapsedTimeFormatted, and $numFailures failures. Re-queued: $batchRequeued",
                )
                throwable.printStackTrace()
            }

            override fun onFlushSuccess(
                elapsedTime: Duration,
                batchSize: Int,
                batchId: String,
            ) {
                val elapsedTimeFormatted = elapsedTime.toString(DurationUnit.SECONDS)
                println(
                    "Successful flush for batch $batchId with $batchSize updates after $elapsedTimeFormatted",
                )
            }
        }
    // Create CounterService, passing in API key
    val service =
        CounterService.createDefault(
            config =
                CounterConfig(
                    apiKey = "",
                    timeResolution = 1.minutes,
                    flushErrorHandling =
                        CounterConfig.FlushErrorHandling(
                            reAddFailedUpdates = false,
                            maxFailureRetries = 1,
                            maxBackoff = 1.seconds,
                        ),
                ),
        )
    service.setListener(listener)
    service.start()
    // Update a single tag by `1` every second for 100 seconds
    runBlocking {
        var countersAdded = 0
        while (isActive) {
            val added =
                service.updateCounter(
                    update =
                        CounterUpdate(
                            tag = "test_tag",
                            added = BigNumber.create(amount = 1),
                        ),
                )
            if (!added) {
                println("Update failed: $countersAdded")
            }
            println("Added counter $countersAdded to \"test_tag\"!")
            delay(50.milliseconds)
            if (++countersAdded >= 200) {
                service.shutdown(flushPendingUpdates = true)
                break
            }
        }
    }
    println("Counters updated!")
}
